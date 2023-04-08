package com.github.chencmd.datapacklinter.analyze

import com.github.chencmd.datapacklinter.ciplatform.CIPlatformInteractionInstr
import com.github.chencmd.datapacklinter.generic.AsyncExtra
import com.github.chencmd.datapacklinter.generic.DLSConfigExtra.*
import com.github.chencmd.datapacklinter.generic.EitherTExtra
import com.github.chencmd.datapacklinter.utils.FSAsync

import cats.Monad
import cats.data.EitherT
import cats.data.OptionT
import cats.data.StateT
import cats.effect.Async
import cats.implicits.*

import scala.scalajs.js
import scala.scalajs.js.Date

import typings.node.pathMod as path
import typings.vscodeUri.mod.URI

import typings.spgodingDatapackLanguageServer.libServicesCommonMod as DLSCommon
import typings.spgodingDatapackLanguageServer.mod as DLS
import typings.spgodingDatapackLanguageServer.spgodingDatapackLanguageServerStrings as DLSStr
import typings.spgodingDatapackLanguageServer.anon.GetText
import typings.spgodingDatapackLanguageServer.libNodesIdentityNodeMod.IdentityNode
import typings.spgodingDatapackLanguageServer.libTypesClientCacheMod.ClientCache
import typings.spgodingDatapackLanguageServer.libTypesConfigMod.Config as DLSConfig
import typings.spgodingDatapackLanguageServer.libTypesMod.Uri
import typings.spgodingDatapackLanguageServer.mod.DatapackLanguageService

final class DatapackAnalyzer[F[_]: Async] private (
  private val analyzerConfig: AnalyzerConfig,
  private val dls: DatapackLanguageService,
  private val dlsConfig: DLSConfig
)(using ciInteraction: CIPlatformInteractionInstr[F]) {
  private type AnalyzedCount = Int
  private type FOption[A]    = OptionT[F, A]

  def analyzeAll(
    analyzeCallback: AnalyzeResult => EitherT[F, String, Unit]
  ): StateT[[A] =>> EitherT[F, String, A], AnalyzedCount, List[AnalyzeResult]] = {
    val program = {
      for {
        parseFiles: List[AnalyzeState] <- StateT
          .liftF(
            dls.roots.toList
              .flatTraverse { root =>
                val dir = path.join(root.fsPath, "data")
                FSAsync.foreachFileRec(root.fsPath, dir, p => dlsConfig.isRelIncluded(p.rel)) { p =>
                  // リソースパスが存在して かつ ignorePathsに含まれていない
                  val isPathValid = IdentityNode
                    .fromRel(p.rel)
                    .map(_.id.toString)
                    .exists(!analyzerConfig.ignorePathsIncludes(_))

                  Option.when(isPathValid)(AnalyzeState.Waiting(root.fsPath, p.abs, p.rel)).pure[F]
                }
              }
              .map(_.flatten)
          )
          .mapK(EitherT.liftK)

        results <- {
          parseFiles.sorted.flatTraverse {
            case AnalyzeState.Waiting(root, abs, rel) => parseDoc(root, abs, rel)
                .mapK(EitherT.liftK)
                .flatMapF(res => res.traverse(analyzeCallback).as(res.toList))
            case AnalyzeState.Cached(_, _, res) => StateT.liftF(analyzeCallback(res).as(List(res)))
          }
        }
      } yield results
    }

    program
  }

  def parseDoc(
    root: String,
    file: String,
    rel: String
  ): StateT[F, AnalyzedCount, Option[AnalyzeResult]] = {
    val program = for {
      languageID <- OptionT.fromOption {
        val dotIdx   = file.lastIndexOf(".")
        val slashIdx = file.lastIndexOf("/")
        for {
          rawLang <- Option.when(dotIdx != -1 && slashIdx < dotIdx)(file.substring(dotIdx + 1))
          lang    <- rawLang match {
            case "mcfunction" => Some(DLSStr.mcfunction)
            case "json"       => Some(DLSStr.json)
            case _            => None
          }
        } yield lang: (DLSStr.mcfunction | DLSStr.json)
      }

      id <- OptionT.fromOption(IdentityNode.fromRel(rel).toOption.map(_.id))

      doc       <- AsyncExtra.fromPromise[FOption] {
        DLSCommon.getTextDocument(
          GetText(
            getText = () => DLS.readFile(file),
            langID = languageID,
            uri = Uri.file(file).asInstanceOf[URI]
          )
        )
      }
      parsedDoc <- OptionT(AsyncExtra.fromPromise[F](dls.parseDocument(doc)).map(_.toOption))

      res <- AnalyzeResult[FOption](root, file, id, parsedDoc, doc)
    } yield res

    for {
      res <- StateT.liftF(program.value)
      _   <- res.traverse(r => gc(r.analyzedLength))
    } yield res
  }

  def getDeclares = { ??? }

  def updateCache(): StateT[F, AnalyzedCount, Unit] = {
    def checkFilesInCache: F[Unit] = {
      for {
        uriStrings <- Monad[F].pure {
          dls.cacheFile.files.keys
        }
      } yield ()
    }

    def addNewFileToCache: StateT[F, AnalyzedCount, Unit] = {
      for {
        filePaths <- StateT.liftF {
          dls.roots.toList.traverse { root =>
            val dir = path.join(root.fsPath, "data")
            FSAsync.foreachFileRec(root.fsPath, dir, p => dlsConfig.isRelIncluded(p.rel)) {
              _.abs.pure[F]
            }
          }
        }
        _         <- {
          filePaths.flatten.traverse { filePath =>
            val uri           = dls.parseUri(Uri.file(filePath).toString())
            val alreadyCached = dls.cacheFile.files.contains(uri.toString())

            val program = for {
              _ <- EitherTExtra.exitWhenA(alreadyCached)(Monad[F].unit)
              _ <- EitherT.liftF {
                ciInteraction.printDebug(s"[updateCacheFile] file add detected: ${uri.fsPath}")
              }
              _ <- EitherT.liftF(AsyncExtra.fromPromise[F](dls.onAddedFile(uri)))
            } yield ()

            StateT.liftF(program.merge).flatMap(_ => gc())
          }
        }
      } yield ()
    }

    for {
      time1 <- StateT.liftF(Async[F].delay(Date.now()))
      _     <- StateT.liftF(checkFilesInCache)
      time2 <- StateT.liftF(Async[F].delay(Date.now()))
      _     <- StateT.liftF(ciInteraction.printInfo(s"[updateCacheFile] [1] ${time2 - time1} ms"))
      _     <- addNewFileToCache
      time3 <- StateT.liftF(Async[F].delay(Date.now()))
      _     <- StateT.liftF(ciInteraction.printInfo(s"[updateCacheFile] [2] ${time3 - time2} ms"))
      _     <- StateT.liftF(ciInteraction.printInfo(s"[updateCacheFile] [T] ${time3 - time1} ms"))
    } yield ()
  }

  private def gc(force: true): StateT[F, AnalyzedCount, Unit] = {
    for {
      _ <- StateT.liftF(Async[F].delay {
        dls.caches.asInstanceOf[js.Map[String, ClientCache]].clear()
      })
      _ <- StateT.set(0)
    } yield ()
  }

  private def gc(addAnalyzedNodeCount: AnalyzedCount = 17): StateT[F, AnalyzedCount, Unit] = {
    StateT.modifyF { analyzedNodeCount =>
      if (DatapackAnalyzer.GC_THRESHOLD <= analyzedNodeCount + addAnalyzedNodeCount) {
        Async[F].delay(dls.caches.asInstanceOf[js.Map[String, ClientCache]].clear()).as(0)
      } else {
        Monad[F].pure(analyzedNodeCount + addAnalyzedNodeCount)
      }
    }
  }
}

object DatapackAnalyzer {
  private val GC_THRESHOLD = 500

  def apply[F[_]: Async](
    analyzerConfig: AnalyzerConfig,
    dls: DatapackLanguageService,
    dlsConfig: DLSConfig
  )(using ciInteraction: CIPlatformInteractionInstr[F]): F[DatapackAnalyzer[F]] = {
    Async[F].delay(new DatapackAnalyzer(analyzerConfig, dls, dlsConfig))
  }
}

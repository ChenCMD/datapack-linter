package com.github.chencmd.datapacklinter.analyzer

import com.github.chencmd.datapacklinter.ciplatform.CIPlatformInteractionInstr
import com.github.chencmd.datapacklinter.dls.DLSHelper
import com.github.chencmd.datapacklinter.generic.AsyncExtra
import com.github.chencmd.datapacklinter.generic.DLSConfigExtra.*
import com.github.chencmd.datapacklinter.utils.FSAsync
import com.github.chencmd.datapacklinter.utils.Hash

import cats.Applicative
import cats.Monad
import cats.data.OptionT
import cats.data.StateT
import cats.effect.Async
import cats.implicits.*

import scala.scalajs.js
import scala.scalajs.js.Date

import typings.node.pathMod as path
import typings.vscodeUri.mod.URI

import typings.spgodingDatapackLanguageServer.libServicesCommonMod as DLSCommon
import typings.spgodingDatapackLanguageServer.libTypesClientCacheMod as ClientCache
import typings.spgodingDatapackLanguageServer.mod as DLS
import typings.spgodingDatapackLanguageServer.spgodingDatapackLanguageServerStrings as DLSStr
import typings.spgodingDatapackLanguageServer.anon.GetText
import typings.spgodingDatapackLanguageServer.libNodesIdentityNodeMod.IdentityNode
import typings.spgodingDatapackLanguageServer.libTypesConfigMod.Config as DLSConfig
import typings.spgodingDatapackLanguageServer.libTypesMod.Uri
import typings.spgodingDatapackLanguageServer.mod.DatapackLanguageService

final class DatapackAnalyzer private (
  private val analyzerConfig: AnalyzerConfig,
  private val dls: DatapackLanguageService,
  private val dlsConfig: DLSConfig
) {
  private type AnalyzedCount         = Int
  private type AnalyzeState[F[_], A] = StateT[F, AnalyzedCount, A]

  def analyzeAll[F[_]: Async](analyzeCallback: AnalyzeResult => F[Unit])(using
    ciInteraction: CIPlatformInteractionInstr[F]
  ): AnalyzeState[F, List[AnalyzeResult]] = {
    val program = dls.roots.toList
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
    for {
      parseFiles <- StateT.liftF(program)
      results    <- parseFiles.sorted.flatTraverse {
        case AnalyzeState.Waiting(root, abs, rel) => parseDoc(root, abs, rel)
            .flatMapF(res => res.traverse_(analyzeCallback).as(res.toList))
        case AnalyzeState.Cached(_, _, res) => StateT.liftF(analyzeCallback(res).as(List(res)))
      }
    } yield results
  }

  def parseDoc[F[_]: Async](
    root: String,
    file: String,
    rel: String
  ): AnalyzeState[F, Option[AnalyzeResult]] = {
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

      doc       <- AsyncExtra.fromPromise[OptionT[F, _]] {
        DLSCommon.getTextDocument(
          GetText(
            getText = () => DLS.readFile(file),
            langID = languageID,
            uri = Uri.file(file).asInstanceOf[URI]
          )
        )
      }
      parsedDoc <- DLSHelper.parseDoc(dls)(doc)

      res <- AnalyzeResult[OptionT[F, _]](root, file, id, parsedDoc, doc)
    } yield res

    for {
      res <- StateT.liftF(program.value)
      _   <- res.traverse_(r => gc(r.analyzedLength))
    } yield res
  }

  def fetchChecksums[F[_]: Async](files: List[String]): F[Map[String, String]] = for {
    data <- files.traverseFilter { uriString =>
      val program = for {
        uri      <- OptionT.pure(dls.parseUri(uriString))
        contents <- OptionT(FSAsync.readFileOpt(uri.fsPath))
        checksum <- OptionT.pure(Hash.stringToHash(contents))
      } yield uriString -> checksum
      program.value
    }
  } yield Map(data*)

  def updateCache[F[_]: Async](fileStates: Map[String, FileState])(using
    ciInteraction: CIPlatformInteractionInstr[F]
  ): AnalyzeState[F, Unit] = {
    import FileState.*

    def checkFilesInCache[F[_]: Async]()(using
      ciInteraction: CIPlatformInteractionInstr[F]
    ): F[Unit] = fileStates.toList.traverse_ {
      case (uriString, state) =>
        val uri = dls.parseUri(Uri.file(uriString).toString)
        state match {
          case Updated | RefsUpdated => for {
              _ <- AsyncExtra.fromPromise(dls.onModifiedFile(uri))
            } yield ()
          case Deleted               => Async[F].delay {
              dls.onDeletedFile(uri)
            }
          case _                     => Monad[F].unit
        }
    }

    def addNewFileToCache(): AnalyzeState[F, Unit] = {
      fileStates.toList.traverse_ {
        case (uriString, Created) => for {
            uri <- Monad[AnalyzeState[F, _]].pure(dls.parseUri(Uri.file(uriString).toString))
            _   <- StateT.liftF(AsyncExtra.fromPromise(dls.onAddedFile(uri)))
            _   <- gc()
          } yield ()
        case _                    => Monad[AnalyzeState[F, _]].unit
      }
    }

    for {
      time1 <- StateT.liftF(Async[F].delay(Date.now()))
      _     <- StateT.liftF(checkFilesInCache())
      time2 <- StateT.liftF(Async[F].delay(Date.now()))
      _     <- StateT.liftF(ciInteraction.printInfo(s"[updateCacheFile] [1] ${time2 - time1} ms"))
      _     <- addNewFileToCache()
      _     <- StateT.liftF(Async[F].delay(ClientCache.trimCache(dls.cacheFile.cache)))
      time3 <- StateT.liftF(Async[F].delay(Date.now()))
      _     <- StateT.liftF(ciInteraction.printInfo(s"[updateCacheFile] [2] ${time3 - time2} ms"))
      _     <- StateT.liftF(ciInteraction.printInfo(s"[updateCacheFile] [T] ${time3 - time1} ms"))
      _     <- gc(true)
    } yield ()
  }

  @annotation.nowarn("msg=unused explicit parameter")
  private def gc[F[_]: Async](force: true): AnalyzeState[F, Unit] = for {
    _ <- StateT.liftF(Async[F].delay {
      dls.caches.asInstanceOf[js.Map[String, Any]].clear()
    })
    _ <- StateT.set(0)
  } yield ()

  private def gc[F[_]: Async](
    addAnalyzedNodeCount: AnalyzedCount = 17
  ): AnalyzeState[F, Unit] = for {
    _     <- StateT.modify((_: AnalyzedCount) + addAnalyzedNodeCount)
    added <- StateT.get
    _     <- Applicative[AnalyzeState[F, _]].whenA(DatapackAnalyzer.GC_THRESHOLD <= added) {
      gc(true)
    }
  } yield ()
}

object DatapackAnalyzer {
  private val GC_THRESHOLD = 500

  def apply(
    analyzerConfig: AnalyzerConfig,
    dls: DatapackLanguageService,
    dlsConfig: DLSConfig
  ): DatapackAnalyzer = {
    new DatapackAnalyzer(analyzerConfig, dls, dlsConfig)
  }
}

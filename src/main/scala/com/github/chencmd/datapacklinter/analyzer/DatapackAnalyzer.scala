package com.github.chencmd.datapacklinter.analyzer

import com.github.chencmd.datapacklinter.ciplatform.CIPlatformInteractionInstr
import com.github.chencmd.datapacklinter.dls.DLSHelper
import com.github.chencmd.datapacklinter.generic.AsyncExtra
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

import typings.vscodeUri.mod.URI

import typings.spgodingDatapackLanguageServer.libServicesCommonMod as DLSCommon
import typings.spgodingDatapackLanguageServer.libTypesClientCacheMod as ClientCache
import typings.spgodingDatapackLanguageServer.mod as DLS
import typings.spgodingDatapackLanguageServer.spgodingDatapackLanguageServerStrings as DLSStr
import typings.spgodingDatapackLanguageServer.anon.GetText
import typings.spgodingDatapackLanguageServer.libNodesIdentityNodeMod.IdentityNode
import typings.spgodingDatapackLanguageServer.libTypesMod.Uri
import typings.spgodingDatapackLanguageServer.mod.DatapackLanguageService

final class DatapackAnalyzer private (
  private val dls: DatapackLanguageService,
  private val analyzerConfig: AnalyzerConfig,
  private val analyzeCache: Map[String, AnalysisResult]
) {
  import DatapackAnalyzer.*

  def analyzeAll[F[_]: Async](fileUpdates: Map[String, FileUpdate])(
    analyzeCallback: AnalysisResult => F[Unit]
  )(using
    ciInteraction: CIPlatformInteractionInstr[F]
  ): AnalysisState[F, List[AnalysisResult]] = {
    import FileUpdate.*
    fileUpdates.toList
      .sortBy(_._1)
      .map { case (k, v) => DLSHelper.getFileInfoFromAbs(dls)(k) -> v }
      .filter {
        case (k, _) => k
            .flatMap(a => IdentityNode.fromRel(a.rel).toOption)
            .map(_.id.toString)
            .exists(!analyzerConfig.ignorePathsIncludes(_))
      }
      .collect {
        case (Some(k), Created | ContentUpdated | RefsUpdated)     =>
          AnalysisState.Waiting(k.root, k.abs, k.rel)
        case (Some(k), NotChanged) if analyzeCache.contains(k.abs) =>
          AnalysisState.Cached(analyzeCache(k.abs))
      }
      .flatTraverse {
        case AnalysisState.Waiting(root, abs, rel) => for {
            res <- parseDoc(root, abs, rel)
            _   <- StateT.liftF(res.traverse_(analyzeCallback))
          } yield res.toList
        case AnalysisState.Cached(res) => StateT.liftF(analyzeCallback(res).as(List(res)))
      }
  }

  def parseDoc[F[_]: Async](
    root: String,
    file: String,
    rel: String
  ): AnalysisState[F, Option[AnalysisResult]] = {
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

      res <- AnalysisResult[OptionT[F, _]](root, file, id, parsedDoc, doc)
    } yield (res, parsedDoc.nodes.length)

    for {
      res <- StateT.liftF(program.value)
      _   <- res.traverse_(r => gc(r._2))
    } yield res.map(_._1)
  }

  def fetchChecksums[F[_]: Async](files: List[String]): F[Map[String, String]] = for {
    data <- files.traverseFilter { uriString =>
      val uri     = dls.parseUri(uriString)
      val program = for {
        contents <- OptionT(FSAsync.readFileOpt(uri.fsPath))
        checksum = Hash.stringToHash(contents)
      } yield uriString -> checksum
      program.value
    }
  } yield Map(data*)

  def updateCache[F[_]: Async](fileUpdates: Map[String, FileUpdate])(using
    ciInteraction: CIPlatformInteractionInstr[F]
  ): AnalysisState[F, Unit] = {
    import FileUpdate.*

    def checkFilesInCache[F[_]: Async]()(using
      ciInteraction: CIPlatformInteractionInstr[F]
    ): F[Unit] = fileUpdates.toList.traverse_ {
      case (uriString, state) =>
        val uri = dls.parseUri(Uri.file(uriString).toString)
        state match {
          case ContentUpdated | RefsUpdated => for {
              _ <- AsyncExtra.fromPromise(dls.onModifiedFile(uri))
            } yield ()
          case Deleted               => Async[F].delay {
              dls.onDeletedFile(uri)
            }
          case _                     => Monad[F].unit
        }
    }

    def addNewFileToCache(): AnalysisState[F, Unit] = {
      fileUpdates.toList.traverse_ {
        case (uriString, Created) => for {
            uri <- Monad[AnalysisState[F, _]].pure(dls.parseUri(Uri.file(uriString).toString))
            _   <- StateT.liftF(AsyncExtra.fromPromise(dls.onAddedFile(uri)))
            _   <- gc()
          } yield ()
        case _                    => Monad[AnalysisState[F, _]].unit
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
  private def gc[F[_]: Async](force: true): AnalysisState[F, Unit] = for {
    _ <- StateT.liftF(Async[F].delay {
      dls.caches.asInstanceOf[js.Map[String, Any]].clear()
    })
    _ <- StateT.set(0)
  } yield ()

  private def gc[F[_]: Async](
    addAnalyzedNodeCount: AnalyzedCount = 17
  ): AnalysisState[F, Unit] = for {
    _     <- StateT.modify((_: AnalyzedCount) + addAnalyzedNodeCount)
    added <- StateT.get
    _     <- Applicative[AnalysisState[F, _]].whenA(GC_THRESHOLD <= added) {
      gc(true)
    }
  } yield ()
}

object DatapackAnalyzer {
  private val GC_THRESHOLD = 500
  private type AnalyzedCount         = Int
  private type AnalysisState[F[_], A] = StateT[F, AnalyzedCount, A]

  def apply(
    dls: DatapackLanguageService,
    analyzerConfig: AnalyzerConfig,
    analyzeCache: List[AnalysisResult]
  ): DatapackAnalyzer = {
    new DatapackAnalyzer(dls, analyzerConfig, analyzeCache.map(c => c.absolutePath -> c).toMap)
  }
}

package com.github.chencmd.datapacklinter.analyzer

import com.github.chencmd.datapacklinter.ciplatform.CIPlatformInteractionInstr
import com.github.chencmd.datapacklinter.dls.DLSHelper
import com.github.chencmd.datapacklinter.facade.DatapackLanguageService
import com.github.chencmd.datapacklinter.generic.AsyncExtra
import com.github.chencmd.datapacklinter.generic.MapExtra.*
import com.github.chencmd.datapacklinter.term.AnalysisCache
import com.github.chencmd.datapacklinter.term.FileChecksums
import com.github.chencmd.datapacklinter.term.FileUpdates
import com.github.chencmd.datapacklinter.utils.FSAsync
import com.github.chencmd.datapacklinter.utils.Hash
import com.github.chencmd.datapacklinter.utils.Path
import com.github.chencmd.datapacklinter.utils.URI

import cats.Applicative
import cats.Monad
import cats.data.OptionT
import cats.data.StateT
import cats.effect.Async
import cats.implicits.*

import scala.scalajs.js
import scala.scalajs.js.Date

import typings.spgodingDatapackLanguageServer.libServicesCommonMod as DLSCommon
import typings.spgodingDatapackLanguageServer.libTypesClientCacheMod as ClientCache
import typings.spgodingDatapackLanguageServer.mod as DLS
import typings.spgodingDatapackLanguageServer.spgodingDatapackLanguageServerStrings as DLSStr
import typings.spgodingDatapackLanguageServer.anon.GetText
import typings.spgodingDatapackLanguageServer.libNodesIdentityNodeMod.IdentityNode

final class DatapackAnalyzer (
  private val dls: DatapackLanguageService,
  private val analyzerConfig: AnalyzerConfig,
  private val analyzeCache: AnalysisCache
) {
  import DatapackAnalyzer.*

  def analyzeAll[F[_]: Async](fileUpdates: FileUpdates)(analyzeCallback: AnalysisResult => F[Unit])(using
    ciInteraction: CIPlatformInteractionInstr[F]
  ): AnalysisState[F, List[AnalysisResult]] = {
    import FileUpdate.*
    fileUpdates.toList
      .sortBy(_._1.toString)
      .map { case (k, v) => DLSHelper.getFileInfoFromAbs(dls)(k) -> v }
      .filter {
        case (k, _) => k
            .flatMap(a => IdentityNode.fromRel(a.rel.toString).toOption)
            .map(_.id.toString)
            .exists(!analyzerConfig.ignorePathsIncludes(_))
      }
      .collect {
        case (Some(k), Created | ContentUpdated | RefsUpdated)     => AnalysisState.Waiting(k.root, k.abs, k.rel)
        case (Some(k), NotChanged) if analyzeCache.contains(k.abs) => AnalysisState.Cached(analyzeCache(k.abs))
      }
      .flatTraverse {
        case AnalysisState.Waiting(root, abs, rel) => for {
            res <- parseDoc(root, abs, rel)
            _   <- StateT.liftF(res.traverse_(analyzeCallback))
          } yield res.toList
        case AnalysisState.Cached(res)             => StateT.liftF(analyzeCallback(res).as(List(res)))
      }
  }

  def parseDoc[F[_]: Async](root: Path, file: Path, rel: Path): AnalysisState[F, Option[AnalysisResult]] = {
    val program = for {
      languageID <- OptionT.fromOption {
        for {
          rawLang <- Path.extname(file)
          lang    <- rawLang match {
            case "mcfunction" => Some(DLSStr.mcfunction)
            case "json"       => Some(DLSStr.json)
            case _            => None
          }
        } yield lang: (DLSStr.mcfunction | DLSStr.json)
      }

      id <- OptionT.fromOption(IdentityNode.fromRel(rel.toString).toOption.map(_.id))

      doc       <- AsyncExtra.fromPromise[OptionT[F, _]] {
        DLSCommon.getTextDocument(
          GetText(
            getText = () => DLS.readFile(file.toString),
            langID = languageID,
            uri = URI.file(file).vs
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

  def fetchChecksums[F[_]: Async](files: List[Path]): F[FileChecksums] = for {
    data <- files.traverseFilter { uriString =>
      val uri     = URI.fromPath(uriString)
      val program = for {
        contents <- OptionT(FSAsync.readFileOpt(uri.fsPath))
        checksum = Hash.stringToHash(contents)
      } yield uriString -> checksum
      program.value
    }
  } yield Map(data*)

  def updateCache[F[_]: Async](fileUpdates: FileUpdates)(using
    ciInteraction: CIPlatformInteractionInstr[F]
  ): AnalysisState[F, Unit] = {
    import FileUpdate.*

    def checkFilesInCache[F[_]: Async](uriFileUpdates: Map[URI, FileUpdate])(using
      ciInteraction: CIPlatformInteractionInstr[F]
    ): F[Unit] = uriFileUpdates.toList.traverse_ {
      case (uri, ContentUpdated | RefsUpdated) => for {
          _ <- AsyncExtra.coerceOrphansTask(AsyncExtra.fromPromise(dls.onModifiedFile(uri)))
        } yield ()
      case (uri, Deleted)                      => Async[F].delay {
          dls.onDeletedFile(uri)
        }
      case _                                   => Monad[F].unit
    }

    def addNewFileToCache(uriFileUpdates: Map[URI, FileUpdate]): AnalysisState[F, Unit] = {
      uriFileUpdates.filter(_._2 == Created).keys.toList.traverse_ { uri =>
        StateT.liftF(AsyncExtra.fromPromise(dls.onAddedFile(uri))) >> gc()
      }
    }

    val uriFileUpdates = fileUpdates.mapKeys(URI.file(_))
    for {
      time1 <- StateT.liftF(Async[F].delay(Date.now()))
      _     <- StateT.liftF(checkFilesInCache(uriFileUpdates))
      time2 <- StateT.liftF(Async[F].delay(Date.now()))
      _     <- StateT.liftF(ciInteraction.printInfo(s"[updateCacheFile] [1] ${time2 - time1} ms"))
      _     <- addNewFileToCache(uriFileUpdates)
      _     <- StateT.liftF(Async[F].delay(ClientCache.trimCache(dls.cacheFile.cache)))
      time3 <- StateT.liftF(Async[F].delay(Date.now()))
      _     <- StateT.liftF(ciInteraction.printInfo(s"[updateCacheFile] [2] ${time3 - time2} ms"))
      _     <- StateT.liftF(ciInteraction.printInfo(s"[updateCacheFile] [T] ${time3 - time1} ms"))
      _     <- gc(true)
    } yield ()
  }

  @annotation.nowarn("msg=unused explicit parameter")
  private def gc[F[_]: Async](force: true): AnalysisState[F, Unit] = for {
    _ <- StateT.liftF(Async[F].delay(dls.clearCaches()))
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
  private type AnalyzedCount          = Int
  private type AnalysisState[F[_], A] = StateT[F, AnalyzedCount, A]
}

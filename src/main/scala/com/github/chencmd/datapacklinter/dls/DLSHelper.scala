package com.github.chencmd.datapacklinter.dls

import com.github.chencmd.datapacklinter.ciplatform.CIPlatformInteractionInstr
import com.github.chencmd.datapacklinter.generic.AsyncExtra
import com.github.chencmd.datapacklinter.generic.DLSConfigExtra.*
import com.github.chencmd.datapacklinter.generic.Instances.given
import com.github.chencmd.datapacklinter.generic.RaiseNec
import com.github.chencmd.datapacklinter.generic.UndefOrExtra.*
import com.github.chencmd.datapacklinter.utils.Datapack
import com.github.chencmd.datapacklinter.utils.FSAsync
import com.github.chencmd.datapacklinter.utils.JSObject
import com.github.chencmd.datapacklinter.utils.Jsonc

import cats.Monad
import cats.data.OptionT
import cats.effect.Async
import cats.implicits.*

import scala.concurrent.duration.*
import scala.util.chaining.*

import scala.scalajs.js
import scala.scalajs.js.JSON

import typings.node.pathMod as path
import typings.vscodeLanguageserverTextdocument.mod.TextDocument

import typings.spgodingDatapackLanguageServer.libTypesClientCapabilitiesMod as ClientCapabilities
import typings.spgodingDatapackLanguageServer.mod as DLS
import typings.spgodingDatapackLanguageServer.libNodesIdentityNodeMod.IdentityNode
import typings.spgodingDatapackLanguageServer.libPluginsPluginLoaderMod.PluginLoader
import typings.spgodingDatapackLanguageServer.libServicesDatapackLanguageServiceMod.DatapackLanguageServiceOptions
import typings.spgodingDatapackLanguageServer.libTypesClientCacheMod.CacheFile
import typings.spgodingDatapackLanguageServer.libTypesConfigMod.Config as DLSConfig
import typings.spgodingDatapackLanguageServer.libTypesDatapackDocumentMod.DatapackDocument
import typings.spgodingDatapackLanguageServer.libTypesMod.Uri
import typings.spgodingDatapackLanguageServer.libTypesVersionInformationMod.VersionInformation
import typings.spgodingDatapackLanguageServer.mod.DatapackLanguageService

object DLSHelper {
  def parseDoc[F[_]: Async](
    dls: DatapackLanguageService
  )(doc: TextDocument): OptionT[F, DatapackDocument] = OptionT {
    AsyncExtra.fromPromise[F](dls.parseDocument(doc)).map(_.toOption)
  }

  def getAllFiles[F[_]: Async](
    dls: DatapackLanguageService,
    dlsConfig: DLSConfig
  ): F[List[String]] = {
    dls.roots.toList.flatTraverse { r =>
      val dir = path.join(r.fsPath, "data")
      FSAsync
        .foreachFileRec(r.fsPath, dir, p => dlsConfig.isRelIncluded(p.rel)) { p =>
          val isValidDocument = IdentityNode.fromRel(p.rel).isDefined
          Option.when(isValidDocument)(p.abs).pure[F]
        }
        .map(_.flatten)
    }
  }

  case class FileInfo(root: String, abs: String, rel: String)

  def getFileInfoFromAbs(dls: DatapackLanguageService)(abs: String): Option[FileInfo] = {
    dls.roots.toList
      .map(_.fsPath)
      .find(abs.startsWith)
      .map(root => FileInfo(root, abs, path.relative(root, abs)))
  }

  def genReferenceMap(dls: DatapackLanguageService): Map[String, List[String]] = Map.from {
    for {
      (_, cacheWithCategory) <- JSObject.entries(dls.cacheFile.cache)
      (_, cacheWithId)       <- JSObject.entries(cacheWithCategory)
      cacheUnit              <- cacheWithId.toList
      declare                <- cacheUnit.dcl.orEmpty.toList ++ cacheUnit.`def`.orEmpty.toList

      declareUri <- declare.uri.toList
      referenceUris = cacheUnit.ref.orEmpty.toList
        .flatMap(_.uri.toList)
        .map(Uri.parse(_))
        .map(_.fsPath)
    } yield (declareUri, referenceUris)
  }

  def createDLS[F[_]: Async](
    dir: String,
    cacheDir: String,
    dlsConfig: DLSConfig,
    cache: Option[CacheFile]
  )(using
    R: RaiseNec[F, String],
    ciInteraction: CIPlatformInteractionInstr[F]
  ): F[DatapackLanguageService] = for {
    roots <- Datapack.findDatapackRoots(dir, dlsConfig.env.detectionDepth.asInstanceOf[Int])

    capabilities <- Monad[F].pure {
      ClientCapabilities.getClientCapabilities(
        JSObject(
          "workspace" -> JSObject(
            "configuration"          -> true,
            "didChangeConfiguration" -> JSObject("dynamicRegistration" -> true)
          )
        )
      )
    }
    versionInfo  <- getLatestVersions
    plugins      <- AsyncExtra.fromPromise(PluginLoader.load())
    dls          <- Monad[F].pure {
      new DatapackLanguageService(
        DatapackLanguageServiceOptions()
          .setCapabilities(capabilities)
          .pipe(opt => cache.map(opt.setCacheFile(_)).getOrElse(opt))
          .setGlobalStoragePath(cacheDir)
          .setFetchConfig(_ => js.Promise.resolve(dlsConfig))
          .setPlugins(plugins)
          .setVersionInformation(versionInfo)
      )
    }

    _ <- AsyncExtra.fromPromise(dls.init())
    _ <- AsyncExtra.fromPromise(dls.getVanillaData(dlsConfig))
    _ <- Async[F].delay { dls.roots.push(roots*) }
    _ <- ciInteraction.printInfo("datapack roots:")
    _ <- roots.map(_.path).traverse_(ciInteraction.printInfo)
  } yield dls

  private def getLatestVersions[F[_]: Async](using
    R: RaiseNec[F, String],
    ciInteraction: CIPlatformInteractionInstr[F]
  ): F[VersionInformation] = {
    inline val VER_INFO_URI    = "https://launchermeta.mojang.com/mc/game/version_manifest.json"
    inline val PROCESSABLE_VER = "1.16.2"

    for {
      _               <- ciInteraction.printInfo("[LatestVersions] Fetching the latest versions...")
      rawVersionInfo  <- Async[F].timeoutTo(
        AsyncExtra.fromPromise(DLS.requestText(VER_INFO_URI)),
        7.seconds,
        R.raiseOne("[LatestVersions] Fetch timeout")
      )
      versionManifest <- Jsonc
        .parse(rawVersionInfo)
        .flatMap(VersionManifest.attemptToVersionManifest)
        .map(Monad[F].pure)
        .getOrElse(R.raiseOne("[LatestVersions] Failed to parsing version_manifest.json"))
      ans             <- Monad[F].pure {
        VersionInformation(
          versionManifest.latest.release,
          versionManifest.latest.snapshot,
          versionManifest.versions.reverse
            .dropWhile(_.id == PROCESSABLE_VER)
            .map(_.id)
        )
      }
      _ <- ciInteraction.printInfo(s"[LatestVersions] versionInformation = ${JSON.stringify(ans)}")
    } yield ans
  }
}

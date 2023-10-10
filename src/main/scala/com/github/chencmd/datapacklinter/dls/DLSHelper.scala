package com.github.chencmd.datapacklinter.dls

import com.github.chencmd.datapacklinter.ciplatform.CIPlatformInteractionInstr
import com.github.chencmd.datapacklinter.dls.DLSConfig
import com.github.chencmd.datapacklinter.facade.DatapackLanguageService
import com.github.chencmd.datapacklinter.generic.AsyncExtra
import com.github.chencmd.datapacklinter.generic.DLSConfigExtra.*
import com.github.chencmd.datapacklinter.generic.Instances.given
import com.github.chencmd.datapacklinter.generic.RaiseNec
import com.github.chencmd.datapacklinter.generic.UndefOrExtra.*
import com.github.chencmd.datapacklinter.utils.Datapack
import com.github.chencmd.datapacklinter.utils.FSAsync
import com.github.chencmd.datapacklinter.utils.JSObject
import com.github.chencmd.datapacklinter.utils.Jsonc
import com.github.chencmd.datapacklinter.utils.Path
import com.github.chencmd.datapacklinter.utils.URI

import cats.data.OptionT
import cats.effect.Async
import cats.implicits.*

import scala.concurrent.duration.*
import scala.util.chaining.*

import scala.scalajs.js
import scala.scalajs.js.JSON

import typings.filterConsole.mod as consoleFilter
import typings.vscodeLanguageserverTextdocument.mod.TextDocument

import typings.spgodingDatapackLanguageServer.libTypesClientCapabilitiesMod as ClientCapabilities
import typings.spgodingDatapackLanguageServer.mod as DLS
import typings.spgodingDatapackLanguageServer.libNodesIdentityNodeMod.IdentityNode
import typings.spgodingDatapackLanguageServer.libPluginsPluginLoaderMod.PluginLoader
import typings.spgodingDatapackLanguageServer.libServicesDatapackLanguageServiceMod.DatapackLanguageServiceOptions
import typings.spgodingDatapackLanguageServer.libTypesClientCacheMod.CacheFile
import typings.spgodingDatapackLanguageServer.libTypesDatapackDocumentMod.DatapackDocument
import typings.spgodingDatapackLanguageServer.libTypesVersionInformationMod.VersionInformation
import com.github.chencmd.datapacklinter.term.ResourcePath

object DLSHelper {
  def parseDoc[F[_]: Async](dls: DatapackLanguageService)(doc: TextDocument): OptionT[F, DatapackDocument] = OptionT {
    AsyncExtra.coerceOrphansTask(AsyncExtra.fromPromise[F](dls.parseDocument(doc))).map(_.toOption)
  }

  def getAllFiles[F[_]: Async](dls: DatapackLanguageService, dlsConfig: DLSConfig): F[List[Path]] = {
    dls.roots.toList.flatTraverse { r =>
      val dir = Path.join(r.fsPath, "data")
      FSAsync
        .foreachFileRec(r.fsPath, dir, p => dlsConfig.isRelIncluded(p.rel)) { p =>
          val isValidDocument = IdentityNode.fromRel(p.rel).isDefined
          Option.when(isValidDocument)(p.abs).pure[F]
        }
        .map(_.flatten)
    }
  }

  def getResourcePath(dls: DatapackLanguageService)(abs: Path): Option[ResourcePath] = {
    getFileInfoFromAbs(dls)(abs).flatMap(fi => getResourcePath(fi.rel))
  }

  def getResourcePath(rel: Path): Option[ResourcePath] = {
    IdentityNode.fromRel(rel.toString).toOption.map(_.id.toString)
  }

  case class FileInfo(root: Path, abs: Path, rel: Path)

  def getFileInfoFromAbs(dls: DatapackLanguageService)(abs: Path): Option[FileInfo] = {
    dls.roots.toList
      .map(_.fsPath)
      .find(root => abs.toString.startsWith(root.toString))
      .map(root => FileInfo(root, abs, Path.relative(root, abs)))
  }

  def genReferenceMap(dls: DatapackLanguageService): Map[ResourcePath, List[Path]] = Map.from {
    def uriStringToPath(uriString: String): Path = URI.parse(uriString).fsPath
    for {
      (_, cacheWithCategory)      <- JSObject.entries(dls.cacheFile.cache)
      (resourcePath, cacheWithId) <- JSObject.entries(cacheWithCategory)
      cacheUnit                   <- cacheWithId.toList

      referencePaths = cacheUnit.ref.orEmpty.toList
        .flatMap(_.uri.toList)
        .map(uriStringToPath)
    } yield (resourcePath, referencePaths)
  }

  def createDLS[F[_]: Async](dir: Path, cacheDir: Path, dlsConfig: DLSConfig, cache: Option[CacheFile])(using
    R: RaiseNec[F, String],
    ciInteraction: CIPlatformInteractionInstr[F]
  ): F[DatapackLanguageService] = for {
    roots <- Datapack.findDatapackRoots(dir, dlsConfig.env.detectionDepth.asInstanceOf[Int])

    capabilities = ClientCapabilities.getClientCapabilities(
      JSObject(
        "workspace" -> JSObject(
          "configuration"          -> true,
          "didChangeConfiguration" -> JSObject("dynamicRegistration" -> true)
        )
      )
    )
    versionInfo <- getLatestVersions
    plugins <- AsyncExtra.fromPromise(PluginLoader.load())
    dls = DatapackLanguageService(
      DatapackLanguageServiceOptions()
        .setCapabilities(capabilities)
        .pipe(opt => cache.map(opt.setCacheFile(_)).getOrElse(opt))
        .setGlobalStoragePath(cacheDir.toString)
        .setFetchConfig(_ => js.Promise.resolve(dlsConfig))
        .setPlugins(plugins)
        .setVersionInformation(versionInfo)
    )

    _ <- AsyncExtra.fromPromise(dls.init())
    _ <- AsyncExtra.fromPromise(dls.getVanillaData(dlsConfig))
    _ <- Async[F].delay { dls.pushRoots(roots) }
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
        .map(_.pure[F])
        .getOrElse(R.raiseOne("[LatestVersions] Failed to parsing version_manifest.json"))
      ans = VersionInformation(
        versionManifest.latest.release,
        versionManifest.latest.snapshot,
        versionManifest.versions.reverse
          .dropWhile(_.id == PROCESSABLE_VER)
          .map(_.id)
      )
      _ <- ciInteraction.printInfo(s"[LatestVersions] versionInformation = ${JSON.stringify(ans)}")
    } yield ans
  }

  def muteDLSBadLogs[F[_]: Async](): F[Unit] = Async[F].delay {
    consoleFilter.default(
      js.Array(s => s.startsWith("Tried to access collection \"") && s.endsWith("\", but that doesn't exist."))
    )
  }
}

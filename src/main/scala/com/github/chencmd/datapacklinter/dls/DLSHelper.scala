package com.github.chencmd.datapacklinter.dls

import com.github.chencmd.datapacklinter.ciplatform.CIPlatformInteractionInstr
import com.github.chencmd.datapacklinter.generic.AsyncExtra
import com.github.chencmd.datapacklinter.generic.RaiseNec
import com.github.chencmd.datapacklinter.utils.Datapack
import com.github.chencmd.datapacklinter.utils.Jsonc

import cats.Monad
import cats.data.OptionT
import cats.effect.Async
import cats.implicits.*

import scala.concurrent.duration.*

import scala.scalajs.js
import scala.scalajs.js.Dynamic.literal as JSObject
import scala.scalajs.js.JSON

import typings.node.pathMod as path
import typings.vscodeLanguageserverTextdocument.mod.TextDocument

import typings.spgodingDatapackLanguageServer.libTypesClientCapabilitiesMod as ClientCapabilities
import typings.spgodingDatapackLanguageServer.mod as DLS
import typings.spgodingDatapackLanguageServer.libPluginsPluginLoaderMod.PluginLoader
import typings.spgodingDatapackLanguageServer.libServicesDatapackLanguageServiceMod.DatapackLanguageServiceOptions
import typings.spgodingDatapackLanguageServer.libTypesConfigMod.Config as DLSConfig
import typings.spgodingDatapackLanguageServer.libTypesDatapackDocumentMod.DatapackDocument
import typings.spgodingDatapackLanguageServer.libTypesVersionInformationMod.VersionInformation
import typings.spgodingDatapackLanguageServer.mod.DatapackLanguageService

object DLSHelper {
  def parseDoc[F[_]: Async](
    dls: DatapackLanguageService
  )(doc: TextDocument): OptionT[F, DatapackDocument] = OptionT {
    AsyncExtra.fromPromise[F](dls.parseDocument(doc)).map(_.toOption)
  }

  def createDLS[F[_]: Async](
    dir: String,
    dlsConfig: DLSConfig
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
          .setCacheFileUndefined
          .setGlobalStoragePath(path.join(dir, ".cache"))
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

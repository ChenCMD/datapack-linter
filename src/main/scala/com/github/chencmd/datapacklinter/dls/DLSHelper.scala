package com.github.chencmd.datapacklinter.dls

import com.github.chencmd.datapacklinter.ciplatform.CIPlatformInteractionInstr
import com.github.chencmd.datapacklinter.generic.AsyncExtra
import com.github.chencmd.datapacklinter.utils.Datapack
import com.github.chencmd.datapacklinter.utils.Jsonc

import cats.data.EitherT
import cats.effect.Async
import cats.implicits.*

import scala.concurrent.duration.*

import scala.scalajs.js
import scala.scalajs.js.Dynamic.literal as JSObject
import scala.scalajs.js.JSON

import typings.node.pathMod as path

import typings.spgodingDatapackLanguageServer.libTypesClientCapabilitiesMod as ClientCapabilities
import typings.spgodingDatapackLanguageServer.mod as DLS
import typings.spgodingDatapackLanguageServer.libPluginsPluginLoaderMod.PluginLoader
import typings.spgodingDatapackLanguageServer.libServicesDatapackLanguageServiceMod.DatapackLanguageServiceOptions
import typings.spgodingDatapackLanguageServer.libTypesConfigMod.Config as DLSConfig
import typings.spgodingDatapackLanguageServer.libTypesVersionInformationMod.VersionInformation
import typings.spgodingDatapackLanguageServer.mod.DatapackLanguageService

object DLSHelper {
  def createDLS[F[_]: Async](
    dir: String,
    dlsConfig: DLSConfig
  )(using
    ciInteraction: CIPlatformInteractionInstr[F]
  ): EitherT[F, String, DatapackLanguageService] = {
    type FOption = EitherT[F, String, _]
    for {
      roots <-
        Datapack.findDatapackRoots[FOption](dir, dlsConfig.env.detectionDepth.asInstanceOf[Int])

      capabilities <- EitherT.pure {
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
      plugins      <- AsyncExtra.fromPromise[FOption](PluginLoader.load())
      dls          <- Async[FOption].delay {
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

      _ <- AsyncExtra.fromPromise[FOption](dls.init())
      _ <- AsyncExtra.fromPromise[FOption](dls.getVanillaData(dlsConfig))
      _ <- Async[FOption].delay { dls.roots.push(roots*) }
      _ <- EitherT.liftF(ciInteraction.printInfo("datapack roots:"))
      _ <- EitherT.liftF(roots.map(_.path).traverse(ciInteraction.printInfo))
    } yield dls
  }

  private def getLatestVersions[F[_]: Async](using
    ciInteraction: CIPlatformInteractionInstr[F]
  ): EitherT[F, String, VersionInformation] = {
    inline val VER_INFO_URI    = "https://launchermeta.mojang.com/mc/game/version_manifest.json"
    inline val PROCESSABLE_VER = "1.16.2"

    for {
      _               <- EitherT.liftF {
        ciInteraction.printInfo("[LatestVersions] Fetching the latest versions...")
      }
      rawVersionInfo  <- Async[EitherT[F, String, _]].timeoutTo(
        AsyncExtra.fromPromise(DLS.requestText(VER_INFO_URI)),
        7.seconds,
        EitherT.leftT("[LatestVersions] Fetch timeout")
      )
      versionManifest <- EitherT.fromEither {
        Jsonc
          .parse(rawVersionInfo)
          .flatMap(VersionManifest.attemptToVersionManifest)
          .toRight("[LatestVersions] Failed to parsing version_manifest.json")
      }
      ans             <- EitherT.pure {
        VersionInformation(
          versionManifest.latest.release,
          versionManifest.latest.snapshot,
          versionManifest.versions.reverse
            .dropWhile(_.id == PROCESSABLE_VER)
            .map(_.id)
        )
      }
      _               <- EitherT.liftF {
        ciInteraction.printInfo(s"[LatestVersions] versionInformation = ${JSON.stringify(ans)}")
      }
    } yield ans
  }
}

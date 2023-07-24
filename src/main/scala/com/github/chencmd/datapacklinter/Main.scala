package com.github.chencmd.datapacklinter

import com.github.chencmd.datapacklinter.analyzer.AnalyzerConfig
import com.github.chencmd.datapacklinter.analyzer.DatapackAnalyzer
import com.github.chencmd.datapacklinter.analyzer.FileState
import com.github.chencmd.datapacklinter.ciplatform.CIPlatformInteractionInstr
import com.github.chencmd.datapacklinter.ciplatform.CIPlatformManageCacheInstr
import com.github.chencmd.datapacklinter.ciplatform.CIPlatformReadKeyedConfigInstr
import com.github.chencmd.datapacklinter.ciplatform.ghactions.*
import com.github.chencmd.datapacklinter.ciplatform.local.*
import com.github.chencmd.datapacklinter.dls.DLSConfig
import com.github.chencmd.datapacklinter.dls.DLSHelper
import com.github.chencmd.datapacklinter.generic.RaiseNec
import com.github.chencmd.datapacklinter.linter.DatapackLinter
import com.github.chencmd.datapacklinter.linter.LinterConfig
import com.github.chencmd.datapacklinter.utils.FSAsync

import cats.Monad
import cats.data.EitherT
import cats.data.NonEmptyChain
import cats.data.OptionT
import cats.effect.Async
import cats.effect.ExitCode
import cats.effect.IOApp
import cats.effect.Resource
import cats.implicits.*

import scala.util.chaining.*

import scala.scalajs.js.JSConverters.*
import scala.scalajs.js.JSON

import typings.node.pathMod as path
import typings.node.processMod as process
import typings.node.global.console

import org.scalablytyped.runtime.StringDictionary
import typings.spgodingDatapackLanguageServer.libTypesClientCacheMod.CacheFile
import typings.spgodingDatapackLanguageServer.libTypesMod.Uri
import typings.spgodingDatapackLanguageServer.mod.DatapackLanguageService

object Main extends IOApp {
  private case class CIPlatformContext[F[_]](
    interaction: CIPlatformInteractionInstr[F],
    inputReader: CIPlatformReadKeyedConfigInstr[F],
    manageCache: CIPlatformManageCacheInstr[F]
  )

  val CACHE_DIRECTORY = ".cache"

  override def run(args: List[String]) = {
    def run[F[_]: Async]()(using R: RaiseNec[F, String]): F[ExitCode] = for {
      dir <- Async[F].delay(process.cwd())

      exitCode <- getApplicationContext(dir, args.get(0)).use { ctx =>
        given ciInteraction: CIPlatformInteractionInstr[F] = ctx.interaction
        given CIPlatformReadKeyedConfigInstr[F]            = ctx.inputReader

        val ciCache: CIPlatformManageCacheInstr[F] = ctx.manageCache

        val cacheDir      = path.join(dir, CACHE_DIRECTORY)
        val dlsCachePath  = path.join(cacheDir, "dlsCache.json")
        val checksumsPath = path.join(cacheDir, "checksum.json")

        for {
          restoreSucceed            <- ciCache.restore(List(CACHE_DIRECTORY))
          (dlsCache, prevChecksums) <- {
            val program = for {
              _ <- OptionT.when(restoreSucceed)(())

              rawDlsCache <- OptionT(FSAsync.readFileOpt(dlsCachePath))
              dlsCache    <- OptionT.pure {
                // TODO safe cast にする
                JSON.parse(rawDlsCache).asInstanceOf[CacheFile]
              }

              rawChecksums <- OptionT(FSAsync.readFileOpt(checksumsPath))
              checksums    <- OptionT.pure {
                // TODO safe cast にする
                JSON.parse(rawChecksums).asInstanceOf[StringDictionary[String]].toMap
              }
            } yield (dlsCache, checksums)
            for {
              caches <- program.value
              _      <- Async[F].whenA(caches.isEmpty) {
                ciInteraction.printInfo("Failed to restore the cache")
              }
            } yield caches.unzip
          }

          (dlsConfig, linterConfig, analyzerConfig) <- genConfigs(dir)
          dls      <- DLSHelper.createDLS(dir, cacheDir, dlsConfig, dlsCache)
          analyzer <- Monad[F].pure(DatapackAnalyzer(analyzerConfig, dls, dlsConfig))

          (checksums, fileStates) <- genFileStates(analyzer, dls, dlsConfig, prevChecksums)
          exitCode                <- lint(analyzer, fileStates, linterConfig)

          _ <- FSAsync.writeFile(dlsCachePath, JSON.stringify(dls.cacheFile))
          _ <- FSAsync.writeFile(checksumsPath, JSON.stringify(checksums.toJSDictionary))
          _ <- ciCache.store(List(CACHE_DIRECTORY))
        } yield exitCode
      }
    } yield exitCode

    def handleError[F[_]: Async](
      program: EitherT[F, NonEmptyChain[String], ExitCode]
    ): F[ExitCode] = {
      program.value.flatMap {
        case Right(exitCode) => Async[F].pure(exitCode)
        case Left(messages)  => messages
            .traverse_(mes => Async[F].delay(console.error(mes)))
            .as(ExitCode.Error)
      }
    }
    handleError(run())
  }

  private def getApplicationContext[F[_]: Async](
    dir: String,
    configFilePath: Option[String]
  )(using R: RaiseNec[F, String]): Resource[F, CIPlatformContext[F]] = {
    enum Platform {
      case GitHubActions
      case Local
    }
    for {
      context <- Resource.eval(Async[F].delay {
        if process.env.contains("GITHUB_ACTIONS") then Platform.GitHubActions else Platform.Local
      })

      interaction <- {
        context match {
          case Platform.GitHubActions => GitHubInteraction.createInstr(dir)
          case Platform.Local         => LocalInteraction.createInstr()
        }
      }

      inputReader <- Resource.eval {
        configFilePath match {
          case Some(path) => FileInputReader.createInstr(path)
          case None       => Monad[F].pure {
              EnvironmentInputReader.createInstr(k => s"INPUT_${k.replace(" ", "_").toUpperCase()}")
            }
        }
      }

      manageCache <- Resource.eval {
        given CIPlatformInteractionInstr[F] = interaction
        context match {
          case Platform.GitHubActions => GitHubManageCache.createInstr(1)
          case Platform.Local         => Monad[F].pure(LocalManageCache.createInstr())
        }
      }
    } yield CIPlatformContext(interaction, inputReader, manageCache)
  }

  private def genConfigs[F[_]: Async](dir: String)(using
    R: RaiseNec[F, String],
    ciInteraction: CIPlatformInteractionInstr[F],
    readConfig: CIPlatformReadKeyedConfigInstr[F]
  ): F[(DLSConfig, LinterConfig, AnalyzerConfig)] = for {
    dlsConfig      <- DLSConfig.readConfig(path.join(dir, ".vscode", "settings.json"))
    linterConfig   <- LinterConfig.withReader()
    analyzerConfig <- Monad[F].pure(AnalyzerConfig(linterConfig.ignorePaths))
  } yield (dlsConfig, linterConfig, analyzerConfig)

  private def genFileStates[F[_]: Async](
    analyzer: DatapackAnalyzer,
    dls: DatapackLanguageService,
    dlsConfig: DLSConfig,
    prevChecksums: Option[Map[String, String]]
  )(using
    ciInteraction: CIPlatformInteractionInstr[F]
  ): F[(Map[String, String], Map[String, FileState])] = for {
    targetFiles <- DLSHelper.getAllFiles(dls, dlsConfig)
    checksums   <- analyzer.fetchChecksums(targetFiles)
    refs        <- Monad[F].pure(DLSHelper.genReferenceMap(dls))
    fileStates  <- Monad[F].pure(FileState.diff(prevChecksums.orEmpty, checksums, refs))

    _ <- fileStates
      .groupMap(_._2)(t => Uri.file(t._1).fsPath)
      .map { case (k, v) => k -> v.toList }
      .pipe { fm =>
        def log(state: FileState, stateMes: String) = fm.get(state).orEmpty.traverse_ { file =>
          ciInteraction.printDebug(s"file $stateMes detected: $file")
        }
        log(FileState.Created, "add")
          >> log(FileState.Updated, "change")
          >> log(FileState.RefsUpdated, "ref change")
          >> log(FileState.Deleted, "delete")
      }
  } yield (checksums, fileStates)

  private def lint[F[_]: Async](
    analyzer: DatapackAnalyzer,
    fileStates: Map[String, FileState],
    config: LinterConfig
  )(using ciInteraction: CIPlatformInteractionInstr[F]): F[ExitCode] = {
    val program = for {
      _      <- analyzer.updateCache(fileStates)
      result <- analyzer.analyzeAll(DatapackLinter.printResult(_, config.muteSuccessResult))
      errors <- StateT.pure(DatapackLinter.extractErrorCount(result))
      _      <- StateT.liftF {
        def s(n: Int): String = if n > 1 then "s" else ""

        val e = errors.get(4).orEmpty
        val w = errors.get(3).orEmpty
        if (e + w == 0) {
          ciInteraction.printInfo("Check successful")
        } else for {
          _ <- ciInteraction.printInfo(s"Check failed ($e error${s(e)}, $w warning${s(w)})")
          _ <- Monad[F].whenA(config.forcePass) {
            ciInteraction.printInfo("The test has been forced to pass because forcePass is true")
          }
        } yield ()
      }
    } yield {
      if (config.forcePass || errors(3) + errors(4) == 0) ExitCode.Success else ExitCode.Error
    }

    program.runEmptyA
  }
}

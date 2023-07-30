package com.github.chencmd.datapacklinter

import com.github.chencmd.datapacklinter.analyzer.AnalyzeResult
import com.github.chencmd.datapacklinter.analyzer.AnalyzerConfig
import com.github.chencmd.datapacklinter.analyzer.DatapackAnalyzer
import com.github.chencmd.datapacklinter.analyzer.FileState
import com.github.chencmd.datapacklinter.analyzer.JSAnalyzeResult
import com.github.chencmd.datapacklinter.ciplatform.CIPlatformInteractionInstr
import com.github.chencmd.datapacklinter.ciplatform.CIPlatformManageCacheInstr
import com.github.chencmd.datapacklinter.ciplatform.CIPlatformReadKeyedConfigInstr
import com.github.chencmd.datapacklinter.ciplatform.ghactions.*
import com.github.chencmd.datapacklinter.ciplatform.local.*
import com.github.chencmd.datapacklinter.dls.DLSConfig
import com.github.chencmd.datapacklinter.dls.DLSHelper
import com.github.chencmd.datapacklinter.generic.EitherTExtra
import com.github.chencmd.datapacklinter.generic.MapExtra.*
import com.github.chencmd.datapacklinter.generic.RaiseNec
import com.github.chencmd.datapacklinter.linter.DatapackLinter
import com.github.chencmd.datapacklinter.linter.LinterConfig
import com.github.chencmd.datapacklinter.utils.FSAsync
import com.github.chencmd.datapacklinter.utils.Hash

import cats.Monad
import cats.data.EitherT
import cats.data.NonEmptyChain
import cats.data.StateT
import cats.effect.Async
import cats.effect.ExitCode
import cats.effect.IOApp
import cats.effect.Resource
import cats.implicits.*

import scala.util.chaining.*

import scala.scalajs.js
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
        given ciCache: CIPlatformManageCacheInstr[F]       = ctx.manageCache

        val cacheDir               = path.join(dir, CACHE_DIRECTORY)
        val dlsCachePath           = path.join(cacheDir, "dls.json")
        val checksumCachePath      = path.join(cacheDir, "checksums.json")
        val analyzeResultCachePath = path.join(cacheDir, "analyze-results.json")

        for {
          (dlsConfig, linterConfig, analyzerConfig)     <- genConfigs(dir)
          (dlsConfigChecksum, linterConfigChecksum)     <- Monad[F].pure {
            (Hash.objectToHash(dlsConfig), Hash.objectToHash(linterConfig.toJSObject))
          }
          (dlsCache, checksumCache, analyzeResultCache) <- restoreCaches(
            CACHE_DIRECTORY,
            dlsCachePath,
            checksumCachePath,
            analyzeResultCachePath,
            dlsConfigChecksum,
            linterConfigChecksum
          )

          dls      <- DLSHelper.createDLS(dir, cacheDir, dlsConfig, dlsCache)
          analyzer <- Monad[F].pure {
            DatapackAnalyzer(dls, analyzerConfig, analyzeResultCache.orEmpty)
          }

          (checksums, fileStates)      <- genFileStates(analyzer, dls, dlsConfig, checksumCache)
          (analyzeResult, lintSucceed) <- lint(analyzer, fileStates, linterConfig)

          additionalChecksum <- Monad[F].pure(
            Map("$dls-config" -> dlsConfigChecksum, "$linter-config" -> linterConfigChecksum)
          )

          _ <- FSAsync.writeFile(dlsCachePath, JSON.stringify(dls.cacheFile))
          _ <- FSAsync.writeFile(
            checksumCachePath,
            JSON.stringify((checksums ++ additionalChecksum).toJSDictionary)
          )
          _ <- FSAsync.writeFile(
            analyzeResultCachePath,
            JSON.stringify(analyzeResult.map(_.toJSObject).toJSArray)
          )
          _ <- ciCache.store(List(CACHE_DIRECTORY))

          _ <- Monad[F].whenA(!lintSucceed && linterConfig.forcePass) {
            ciInteraction.printInfo("The test has been forced to pass because forcePass is true")
          }
        } yield if lintSucceed || linterConfig.forcePass then ExitCode.Success else ExitCode.Error
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

  private def restoreCaches[F[_]: Async](
    cacheDir: String,
    dlsCachePath: String,
    checksumCachePath: String,
    analyzeResultCachePath: String,
    dlsConfigChecksum: String,
    linterConfigChecksum: String
  )(using
    R: RaiseNec[F, String],
    ciInteraction: CIPlatformInteractionInstr[F],
    ciCache: CIPlatformManageCacheInstr[F]
  ): F[(Option[CacheFile], Option[Map[String, String]], Option[List[AnalyzeResult]])] = {
    val program = for {
      restoreSucceed <- EitherT.liftF(ciCache.restore(List(cacheDir)))
      _              <- EitherTExtra.exitWhenA(restoreSucceed)("Failed to restore the cache")

      rawDlsCache <- EitherT.fromOptionF(FSAsync.readFileOpt(dlsCachePath), "")
      dlsCache    <- EitherT.pure {
        // TODO safe cast にする
        JSON.parse(rawDlsCache).asInstanceOf[CacheFile]
      }

      rawChecksums  <- EitherT.fromOptionF(FSAsync.readFileOpt(checksumCachePath), "")
      checksumCache <- EitherT.pure {
        // TODO safe cast にする
        JSON.parse(rawChecksums).asInstanceOf[StringDictionary[String]].toMap
      }

      rawAnalyzeResultCache <- EitherT.fromOptionF(FSAsync.readFileOpt(analyzeResultCachePath), "")
      analyzeResultsCache   <- EitherT.pure {
        // TODO safe cast にする
        JSON
          .parse(rawAnalyzeResultCache)
          .asInstanceOf[js.Array[JSAnalyzeResult]]
          .toList
          .map(AnalyzeResult.fromJSObject)
      }

      _ <- EitherTExtra.exitWhenA(checksumCache.get("$dls-config").contains(dlsConfigChecksum))("")
      _ <- EitherTExtra.exitWhenA(
        checksumCache.get("$linter-config").contains(linterConfigChecksum)
      )("")
    } yield (dlsCache, checksumCache, analyzeResultsCache)
    for {
      caches <- program.value
      _      <- caches.swap.traverse(ciInteraction.printInfo)
    } yield caches.toOption.unzip3
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
        def log(state: FileState, stateMes: String) = fm.getOrEmpty(state).traverse_ { file =>
          ciInteraction.printDebug(s"file $stateMes detected: $file")
        }
        log(FileState.Created, "add")
          >> log(FileState.Updated, "change")
          >> log(FileState.RefsUpdated, "ref change")
          >> log(FileState.Deleted, "delete")
          >> log(FileState.NoChanged, "no change")
      }
  } yield (checksums, fileStates)

  private def lint[F[_]: Async](
    analyzer: DatapackAnalyzer,
    fileStates: Map[String, FileState],
    config: LinterConfig
  )(using ciInteraction: CIPlatformInteractionInstr[F]): F[(List[AnalyzeResult], Boolean)] = {
    val program = for {
      _                        <- analyzer.updateCache(fileStates)
      result                   <- analyzer.analyzeAll(fileStates)(
        DatapackLinter.printResult(_, config.muteSuccessResult)
      )
      (lintSucceed, err, warn) <- StateT.pure {
        val errors = DatapackLinter.extractErrorCount(result)
        val e      = errors.getOrEmpty(1)
        val w      = errors.getOrEmpty(2)
        (e + w == 0, e, w)
      }

      _ <- StateT.liftF {
        def s(n: Int): String = if n > 1 then "s" else ""
        if (err + warn == 0) {
          ciInteraction.printInfo("Check successful")
        } else {
          ciInteraction.printInfo(s"Check failed ($err error${s(err)}, $warn warning${s(warn)})")
        }
      }
    } yield (result, lintSucceed)

    program.runEmptyA
  }
}

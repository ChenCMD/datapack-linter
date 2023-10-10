package com.github.chencmd.datapacklinter

import com.github.chencmd.datapacklinter.analyzer.AnalysisResult
import com.github.chencmd.datapacklinter.analyzer.AnalyzerConfig
import com.github.chencmd.datapacklinter.analyzer.DatapackAnalyzer
import com.github.chencmd.datapacklinter.analyzer.FileUpdate
import com.github.chencmd.datapacklinter.analyzer.JSAnalysisResult
import com.github.chencmd.datapacklinter.ciplatform.CIPlatformCacheRestorationInstr
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
import com.github.chencmd.datapacklinter.term.AnalysisCache
import com.github.chencmd.datapacklinter.term.Checksum
import com.github.chencmd.datapacklinter.term.FileChecksums
import com.github.chencmd.datapacklinter.term.FileUpdates
import com.github.chencmd.datapacklinter.term.RestoreCacheOrSkip
import com.github.chencmd.datapacklinter.utils.FSAsync
import com.github.chencmd.datapacklinter.utils.Hash
import com.github.chencmd.datapacklinter.utils.Path

import cats.Monad
import cats.data.EitherT
import cats.data.NonEmptyChain
import cats.data.StateT
import cats.effect.Async
import cats.effect.ExitCode
import cats.effect.IOApp
import cats.effect.Resource
import cats.effect.unsafe.IORuntimeConfig
import cats.implicits.*

import scala.concurrent.duration.Duration

import scala.scalajs.js
import scala.scalajs.js.JSConverters.*
import scala.scalajs.js.JSON

import typings.node.global.console
import typings.node.processMod.^ as process

import org.scalablytyped.runtime.StringDictionary
import typings.spgodingDatapackLanguageServer.libTypesClientCacheMod.CacheFile

object Main extends IOApp {
  override protected def runtimeConfig: IORuntimeConfig = {
    super.runtimeConfig.copy(cpuStarvationCheckInitialDelay = Duration.Inf)
  }

  private case class CIPlatformContext[F[_]](
    interaction: CIPlatformInteractionInstr[F],
    inputReader: CIPlatformReadKeyedConfigInstr[F],
    manageCache: CIPlatformManageCacheInstr[F],
    cacheRestoration: CIPlatformCacheRestorationInstr[F]
  )

  val CACHE_DIRECTORY = Path.coerce(".cache")
  val CACHE_VERSION   = 1

  override def run(args: List[String]) = {
    def run[F[_]: Async]()(using R: RaiseNec[F, String]): F[ExitCode] = for {
      cwd <- Async[F].delay(Path.coerce(process.cwd()))

      exitCode <- getApplicationContext(cwd, args.get(0).map(Path.coerce(_))).use { ctx =>
        given ciInteraction: CIPlatformInteractionInstr[F] = ctx.interaction
        given CIPlatformReadKeyedConfigInstr[F]            = ctx.inputReader
        given ciCache: CIPlatformManageCacheInstr[F]       = ctx.manageCache
        given CIPlatformCacheRestorationInstr[F]           = ctx.cacheRestoration

        val cacheDir                    = Path.join(cwd, CACHE_DIRECTORY)
        val dlsCachePath                = Path.join(cacheDir, "dls.json")
        val fileChecksumCachePath       = Path.join(cacheDir, "file-checksums.json")
        val validationChecksumCachePath = Path.join(cacheDir, "validation-checksums.json")
        val analysisResultCachePath     = Path.join(cacheDir, "analysis-results.json")

        for {
          (dlsConfig, linterConfig, analyzerConfig) <- readConfigs(cwd)
          requireChecksums = Map(
            "config-file"   -> Hash.objectToHash(dlsConfig),
            "linter-config" -> Hash.objectToHash(linterConfig.toJSObject)
          )
          shouldRestore <- shouldRestoreCache(linterConfig)
          (dlsCache, checksumCache, analyzeResultCache) <- {
            shouldRestore match {
              case RestoreCacheOrSkip.Skip(reason) => ciInteraction.printInfo(reason).as(None)
              case RestoreCacheOrSkip.Restore      => restoreCaches(
                  CACHE_DIRECTORY,
                  dlsCachePath,
                  fileChecksumCachePath,
                  analysisResultCachePath,
                  validationChecksumCachePath,
                  requireChecksums
                )
            }
          }.map(_.unzip3)

          _ <- ciInteraction.startGroup("init logs")

          _   <- DLSHelper.muteDLSBadLogs()
          dls <- DLSHelper.createDLS(linterConfig.lintDirectory, cacheDir, dlsConfig, dlsCache)
          analyzer = DatapackAnalyzer(dls, analyzerConfig, analyzeResultCache.getOrElse(Map.empty))

          targetFiles <- DLSHelper.getAllFiles(dls, dlsConfig)
          checksums   <- analyzer.fetchChecksums(targetFiles)
          fileUpdates <- {
            val refs = DLSHelper.genReferenceMap(dls)
            val res  = FileUpdate.diff(dls)(checksumCache.getOrElse(Map.empty), checksums, refs)
            DatapackLinter.printFileUpdatesLog(res).as(res)
          }

          (analyzeResult, lintSucceed) <- lint(analyzer, fileUpdates, linterConfig)

          _ <- {
            val cacheMap = List(
              dlsCachePath                -> JSON.stringify(dls.cacheFile),
              fileChecksumCachePath       -> JSON.stringify(
                checksums.map(a => a._1.toString -> a._2.toString).toJSDictionary
              ),
              analysisResultCachePath     -> JSON.stringify(analyzeResult.map(_.toJSObject).toJSArray),
              validationChecksumCachePath -> JSON.stringify(requireChecksums.toJSDictionary)
            )
            cacheMap.traverse_(FSAsync.writeFile(_, _))
          }
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
        case Right(exitCode) => exitCode.pure[F]
        case Left(messages)  => messages
            .traverse_(mes => Async[F].delay(console.error(mes)))
            .as(ExitCode.Error)
      }
    }
    handleError(run())
  }

  private def getApplicationContext[F[_]: Async](
    dir: Path,
    configFilePath: Option[Path]
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
          case None       => EnvironmentInputReader
              .createInstr(k => s"INPUT_${k.replace(" ", "_").toUpperCase()}")
              .pure[F]
        }
      }

      manageCache <- Resource.eval {
        given CIPlatformInteractionInstr[F] = interaction
        context match {
          case Platform.GitHubActions => GitHubManageCache.createInstr(CACHE_VERSION)
          case Platform.Local         => LocalManageCache.createInstr().pure[F]
        }
      }

      cacheRestoration <- Resource.eval {
        context match {
          case Platform.GitHubActions => GitHubCacheRestoration.createInstr()
          case Platform.Local         => LocalCacheRestoration.createInstr().pure[F]
        }
      }
    } yield CIPlatformContext(interaction, inputReader, manageCache, cacheRestoration)
  }

  private def shouldRestoreCache[F[_]: Async](linterConfig: LinterConfig)(using
    cacheRestoration: CIPlatformCacheRestorationInstr[F]
  ): F[RestoreCacheOrSkip] = {
    val program = for {
      _   <- EitherTExtra.exitWhenA(linterConfig.alwaysCheckAllFiles) {
        RestoreCacheOrSkip.Skip("The cache is not used because the checkAlwaysAllFile is true.")
      }
      res <- EitherT.liftF(cacheRestoration.shouldRestoreCache())
    } yield res
    program.merge
  }

  private def restoreCaches[F[_]: Async](
    cacheDir: Path,
    dlsCachePath: Path,
    fileChecksumCachePath: Path,
    analyzeResultCachePath: Path,
    objectChecksumCachePath: Path,
    validationChecksums: Map[String, Checksum]
  )(using
    R: RaiseNec[F, String],
    ciInteraction: CIPlatformInteractionInstr[F],
    ciCache: CIPlatformManageCacheInstr[F]
  ): F[Option[(CacheFile, FileChecksums, AnalysisCache)]] = {
    def readFileOrExit(path: Path, exitMessage: String): EitherT[F, String, String] = {
      EitherT.fromOptionF(FSAsync.readFileOpt(path), exitMessage)
    }

    val program = for {
      restoreSucceed <- EitherT.liftF(ciCache.restore(List(cacheDir)))
      _              <- EitherTExtra.exitWhenA(!restoreSucceed)("Failed to restore the cache")

      rawDlsCache <- readFileOrExit(dlsCachePath, "Failed to read cache file")
      // TODO safe cast にする
      dlsCache = JSON.parse(rawDlsCache).asInstanceOf[CacheFile]

      rawFileChecksums <- readFileOrExit(fileChecksumCachePath, "Failed to read cache file")
      // TODO safe cast にする
      fileChecksumCache = JSON
        .parse(rawFileChecksums)
        .asInstanceOf[StringDictionary[Checksum]]
        .toMap
        .asInstanceOf[FileChecksums]

      rawAnalyzeResultCache <- readFileOrExit(analyzeResultCachePath, "Failed to read cache file")
      // TODO safe cast にする
      analyzeResultsCache = JSON
        .parse(rawAnalyzeResultCache)
        .asInstanceOf[js.Array[JSAnalysisResult]]
        .toList
        .map(AnalysisResult.fromJSObject)
        .map(c => c.absolutePath -> c)
        .toMap

      rawValidationChecksums <- readFileOrExit(
        objectChecksumCachePath,
        "Failed to read cache file"
      )
      // TODO safe cast にする
      validationChecksumCache = JSON
        .parse(rawValidationChecksums)
        .asInstanceOf[StringDictionary[Checksum]]
        .toMap

      _ <- validationChecksums.toList.traverse {
        case (name, checksum) => EitherTExtra.exitWhenA(!validationChecksumCache.get(name).contains(checksum)) {
            s"The cache is not used because the ${name.replace("$", "")} has been changed"
          }
      }
    } yield (dlsCache, fileChecksumCache, analyzeResultsCache)
    for {
      caches <- program.value
      _      <- caches.left.toOption.traverse(ciInteraction.printInfo)
    } yield caches.toOption
  }

  private def readConfigs[F[_]: Async](dir: Path)(using
    R: RaiseNec[F, String],
    ciInteraction: CIPlatformInteractionInstr[F],
    readConfig: CIPlatformReadKeyedConfigInstr[F]
  ): F[(DLSConfig, LinterConfig, AnalyzerConfig)] = for {
    linterConfig <- LinterConfig.withReader(dir)
    analyzerConfig = AnalyzerConfig(linterConfig.ignorePaths)
    dlsConfig <- DLSConfig.readConfig(linterConfig.configPath)
  } yield (dlsConfig, linterConfig, analyzerConfig)

  private def lint[F[_]: Async](
    analyzer: DatapackAnalyzer,
    fileUpdates: FileUpdates,
    config: LinterConfig
  )(using ciInteraction: CIPlatformInteractionInstr[F]): F[(List[AnalysisResult], Boolean)] = {
    val program = for {
      _      <- analyzer.updateCache(fileUpdates)
      _      <- StateT.liftF(ciInteraction.endGroup())
      result <- analyzer.analyzeAll(fileUpdates)(
        DatapackLinter.printResult(_, config.muteSuccessResult)
      )
      (lintSucceed, err, warn) = {
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

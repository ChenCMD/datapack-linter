package com.github.chencmd.datapacklinter

import com.github.chencmd.datapacklinter.analyzer.AnalyzeResult
import com.github.chencmd.datapacklinter.analyzer.AnalyzerConfig
import com.github.chencmd.datapacklinter.analyzer.DatapackAnalyzer
import com.github.chencmd.datapacklinter.analyzer.ErrorSeverity
import com.github.chencmd.datapacklinter.ciplatform.CIPlatformInteractionInstr
import com.github.chencmd.datapacklinter.ciplatform.CIPlatformReadKeyedConfigInstr
import com.github.chencmd.datapacklinter.ciplatform.ghactions.*
import com.github.chencmd.datapacklinter.ciplatform.local.*
import com.github.chencmd.datapacklinter.dls.DLSConfig
import com.github.chencmd.datapacklinter.dls.DLSHelper
import com.github.chencmd.datapacklinter.linter.DatapackLinter
import com.github.chencmd.datapacklinter.linter.LinterConfig

import cats.Monad
import cats.data.EitherT
import cats.data.StateT
import cats.effect.Async
import cats.effect.ExitCode
import cats.effect.IOApp
import cats.effect.Resource
import cats.implicits.*

import typings.node.pathMod as path
import typings.node.processMod as process
import typings.node.global.console
import cats.mtl.Raise
import cats.effect.IO

object Main extends IOApp {
  private case class CIPlatformContext[F[_]](
    interaction: CIPlatformInteractionInstr[F],
    inputReader: CIPlatformReadKeyedConfigInstr[F]
  )

  override def run(args: List[String]) = {
    def run[F[_]: Async]()(using raise: Raise[F, String]): F[ExitCode] = for {
      dir <- Async[F].delay(process.cwd())

      exitCode <- getContextResources(dir, args.get(0)).use { ctx =>
        given CIPlatformInteractionInstr[F]     = ctx.interaction
        given CIPlatformReadKeyedConfigInstr[F] = ctx.inputReader

        lint(dir)
      }
    } yield exitCode

    def catchErr[F[_]: Async](program: EitherT[F, String, ExitCode]): F[ExitCode] = {
      program.value.flatMap {
        case Right(exitCode) => Async[F].pure(exitCode)
        case Left(mes)       => Async[F].delay(console.error(mes)) as ExitCode.Error
      }
    }

    catchErr(run())
  }

  private def getContextResources[F[_]: Async](
    dir: String,
    configFilePath: Option[String]
  )(using raise: Raise[F, String]): Resource[F, CIPlatformContext[F]] = {
    enum Platform {
      case GitHubActions
      case Local
    }
    for {
      context <- Resource.eval(Async[F].delay {
        if (process.env.contains("GITHUB_ACTIONS")) {
          Platform.GitHubActions
        } else {
          Platform.Local
        }
      })

      interaction <- {
        context match {
          case Platform.GitHubActions => GitHubInteraction.createInstr(dir)
          case Platform.Local         => LocalInteraction.createInstr()
        }
      }

      inputReader <- Resource.eval {
        configFilePath
          .map(FileInputReader.createInstr)
          .getOrElse(Monad[F].pure {
            EnvironmentInputReader.createInstr(k => s"INPUT_${k.replace(" ", "_").toUpperCase()}")
          })
      }
    } yield CIPlatformContext(interaction, inputReader)
  }

  private def lint[F[_]: Async](dir: String)(using
    raise: Raise[F, String],
    ciInteraction: CIPlatformInteractionInstr[F],
    readConfig: CIPlatformReadKeyedConfigInstr[F]
  ): F[ExitCode] = {
    val contexts = for {
      dlsConfig      <- DLSConfig.readConfig(path.join(dir, ".vscode", "settings.json"))
      linterConfig   <- LinterConfig.withReader()
      analyzerConfig <- Monad[F].pure(AnalyzerConfig(linterConfig.ignorePaths))

      dls <- DLSHelper.createDLS(dir, dlsConfig)

      analyzer <- Monad[F].pure(DatapackAnalyzer(analyzerConfig, dls, dlsConfig))
    } yield (linterConfig, analyzer)

    val program = for {
      ctx <- StateT.liftF(contexts)
      (config, analyzer) = ctx
      _      <- analyzer.updateCache()
      result <- analyzer.analyzeAll { r =>
        DatapackLinter.printResult(r, config.muteSuccessResult)
      }
    } yield {
      val errors = DatapackLinter.extractErrorCount(result)
      if config.forcePass || errors.values.sum == 0 then {
        ExitCode.Success
      } else {
        ExitCode.Error
      }
    }

    program.runEmptyA
  }
}

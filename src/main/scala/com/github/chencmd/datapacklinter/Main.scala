package com.github.chencmd.datapacklinter

import com.github.chencmd.datapacklinter.analyze.AnalyzeResult
import com.github.chencmd.datapacklinter.analyze.AnalyzerConfig
import com.github.chencmd.datapacklinter.analyze.DatapackAnalyzer
import com.github.chencmd.datapacklinter.analyze.ErrorSeverity
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
import typings.node.global.console.^ as console

object Main extends IOApp {
  private case class CIPlatformContext[F[_]](
    interaction: CIPlatformInteractionInstr[F],
    inputReader: CIPlatformReadKeyedConfigInstr[F]
  )

  override def run(args: List[String]) = {
    def run[F[_]: Async]() = for {
      dir <- Async[F].delay(process.cwd())

      lintResult <- getContextResources(dir, args.get(0)).use { ctx =>
        given CIPlatformInteractionInstr[F]     = ctx.interaction
        given CIPlatformReadKeyedConfigInstr[F] = ctx.inputReader

        lint(dir)
      }.value

      exitCode <- lintResult match {
        case Right(exitCode) => Async[F].pure(exitCode)
        case Left(mes)       => Async[F].delay(console.error(mes)) as ExitCode.Error
      }
    } yield exitCode

    run()
  }

  private def getContextResources[F[_]: Async](
    dir: String,
    configFilePath: Option[String]
  ): Resource[EitherT[F, String, _], CIPlatformContext[F]] = {
    enum Platform {
      case GitHubActions
      case Local
    }
    for {
      context <- Resource.eval(Async[EitherT[F, String, _]].delay {
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
      }.mapK(EitherT.liftK)

      inputReader <- Resource.eval {
        configFilePath.fold {
          EitherT.pure(EnvironmentInputReader.createInstr { k =>
            s"INPUT_${k.replace(" ", "_").toUpperCase()}"
          })
        } {
          FileInputReader.createInstr(_)
        }
      }
    } yield CIPlatformContext(interaction, inputReader)
  }

  private def lint[F[_]: Async](dir: String)(using
    ciInteraction: CIPlatformInteractionInstr[F],
    readConfig: CIPlatformReadKeyedConfigInstr[F]
  ): EitherT[F, String, ExitCode] = {
    val contexts = for {
      dlsConfig      <- EitherT.liftF {
        DLSConfig.readConfig(path.join(dir, ".vscode", "settings.json"))
      }
      linterConfig   <- LinterConfig.withReader()
      analyzerConfig <- EitherT.pure(AnalyzerConfig(linterConfig.ignorePaths))

      dls <- DLSHelper.createDLS(dir, dlsConfig)

      analyzer <- EitherT.liftF(DatapackAnalyzer(analyzerConfig, dls, dlsConfig))
    } yield (linterConfig, analyzer)

    val program = for {
      ctx <- StateT.liftF(contexts)
      (config, analyzer) = ctx
      _      <- analyzer.updateCache().mapK(EitherT.liftK)
      result <- analyzer.analyzeAll { r =>
        EitherT.liftF(DatapackLinter.printResult(r, config.muteSuccessResult))
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

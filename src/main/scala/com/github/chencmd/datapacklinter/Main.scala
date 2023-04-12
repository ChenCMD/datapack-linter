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
import com.github.chencmd.datapacklinter.linter.LintResultPrinter
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

object Main extends IOApp {
  private case class CIPlatformContext[F[_]](
    interaction: CIPlatformInteractionInstr[F],
    inputReader: CIPlatformReadKeyedConfigInstr[F]
  )

  override def run(args: List[String]) = {
    def run[F[_]: Async]() = for {
      dir <- Async[F].delay(process.cwd())

      lintResult <- githubActionsContextResource(dir).use { ctx =>
        given CIPlatformInteractionInstr[F]     = ctx.interaction
        given CIPlatformReadKeyedConfigInstr[F] = ctx.inputReader

        lint(dir)
      }.value

      exitCode <- lintResult match {
        case Right(exitCode) => Async[F].pure(exitCode)
        case Left(mes)       => Async[F].delay(println(mes)).as(ExitCode.Error)
      }
    } yield exitCode

    run()
  }

  private def githubActionsContextResource[F[_]: Async](dir: String): Resource[
    EitherT[F, String, _],
    CIPlatformContext[F]
  ] = {
    val program = for {
      interaction <- GitHubInteraction.createInstr(dir)
      inputReader <- Resource.eval(GitHubInputReader.createInstr())
    } yield CIPlatformContext(interaction, inputReader)

    program.mapK(EitherT.liftK)
  }

  private def localContextResource[F[_]: Async](dir: String): Resource[
    EitherT[F, String, _],
    CIPlatformContext[F]
  ] = {
    val program = for {
      interaction <- LocalInteraction.createInstr().mapK(EitherT.liftK)
      inputReader <- Resource.eval(LocalInputReader.createInstr(dir))
    } yield CIPlatformContext(interaction, inputReader)
    program
  }

  private def lint[F[_]: Async](dir: String)(using
    ciInteraction: CIPlatformInteractionInstr[F],
    readConfig: CIPlatformReadKeyedConfigInstr[F]
  ): EitherT[F, String, ExitCode] = {
    val program = for {
      dlsConfig      <- StateT.liftF(EitherT.liftF {
        DLSConfig.readConfig(path.join(dir, ".vscode", "settings.json"))
      })
      linterConfig   <- StateT.liftF(LinterConfig.withReader())
      analyzerConfig <- StateT.liftF(EitherT.pure(AnalyzerConfig(linterConfig.ignorePaths)))

      dls <- StateT.liftF(DLSHelper.createDLS(dir, dlsConfig))

      analyzer <- StateT.liftF(EitherT.liftF(DatapackAnalyzer(analyzerConfig, dls, dlsConfig)))

      _      <- analyzer.updateCache().mapK(EitherT.liftK)
      result <- analyzer.analyzeAll { r =>
        EitherT.liftF(LintResultPrinter.print(r, linterConfig.muteSuccessResult))
      }
    } yield {
      val errors = result.foldLeft(Map.empty[ErrorSeverity, Int]) { (map, r) =>
        r.errors.foldLeft(map)((m, e) => m.updatedWith(e.severity)(a => Some(a.getOrElse(0) + 1)))
      }
      if linterConfig.forcePass || errors.values.sum == 0 then {
        ExitCode.Success
      } else {
        ExitCode.Error
      }
    }

    program.runEmptyA
  }
}

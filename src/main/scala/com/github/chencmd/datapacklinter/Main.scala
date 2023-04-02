package com.github.chencmd.datapacklinter

import com.github.chencmd.datapacklinter.ciplatform.CIPlatformInteractionInstr
import com.github.chencmd.datapacklinter.ciplatform.CIPlatformReadKeyedConfigInstr
import com.github.chencmd.datapacklinter.ciplatform.ghactions.*
import com.github.chencmd.datapacklinter.ciplatform.local.*
import com.github.chencmd.datapacklinter.dls.DLSConfig
import com.github.chencmd.datapacklinter.dls.DLSHelper
import com.github.chencmd.datapacklinter.linter.AnalyzeResult
import com.github.chencmd.datapacklinter.linter.DatapackLinter
import com.github.chencmd.datapacklinter.linter.LinterConfig

import cats.Monad
import cats.data.EitherT
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
        case Right(_)  => Async[F].pure {
            ExitCode.Success
          }
        case Left(mes) => Async[F].delay {
            println(mes)
            ExitCode.Error
          }
      }
    } yield exitCode

    run()
  }

  private def githubActionsContextResource[F[_]: Async](dir: String): Resource[
    [A] =>> EitherT[F, String, A],
    CIPlatformContext[F]
  ] = {
    val program = for {
      interaction <- GitHubInteraction.createInstr(dir)
      inputReader <- Resource.eval(GitHubInputReader.createInstr())
    } yield CIPlatformContext(interaction, inputReader)

    program.mapK(EitherT.liftK)
  }

  private def localContextResource[F[_]: Async](dir: String): Resource[
    [A] =>> EitherT[F, String, A],
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
  ): EitherT[F, String, Unit] = {
    for {
      dlsConfig <- EitherT.liftF(DLSConfig.readConfig(path.join(dir, ".vscode", "settings.json")))
      dls       <- DLSHelper.createDLS(dir, dlsConfig)

      linterConfig <- LinterConfig.withReader()
      linter       <- EitherT.liftF(DatapackLinter(linterConfig, dls, dlsConfig))

      analyzedCount <- EitherT.liftF(linter.updateCache())
      _             <- linter.lintAll(analyzedCount)(printLintResult)
    } yield ()
  }

  def printLintResult[F[_]: Async](res: AnalyzeResult)(using
    ciInteraction: CIPlatformInteractionInstr[F]
  ): EitherT[F, String, Unit] = {
      val title = s"${res.resourcePath} (${res.dpFilePath})"

      val program = if (res.errors.exists(_.severity <= 2)) {
        for {
          _ <- ciInteraction.printInfo(s"\u001b[91m✗\u001b[39m  ${title}")
          _ <- res.errors
            .filter(_.severity <= 2)
            .map { e =>
              val pos                   = e.range.start
              val paddedPosition        =
                f"${pos.line.asInstanceOf[Int]}%5d:${pos.character.asInstanceOf[Int]}%-5d"
              val indentAdjuster        = " " * (if (e.severity == 1) then 2 else 0)
              val humanReadableSeverity = {
                val raw = e.severity match {
                  case 1 => "Error"
                  case 2 => "Warning"
                  case _ => "Unknown"
                }
                f"${raw}%-7s"
              }
              (e.severity, s" $indentAdjuster$paddedPosition $humanReadableSeverity ${e.message}")
            }
            .traverse {
              case (1, res) => ciInteraction.printError(res)
              case (2, res) => ciInteraction.printWarning(res)
              case (_, res) => ciInteraction.printInfo(res)
            }
        } yield ()
      } else if (true) {
        ciInteraction.printInfo(s"\u001b[92m✓\u001b[39m  ${title}")
      } else {
        Monad[F].pure(())
      }

      EitherT.liftF(program)
    }
}

package com.github.chencmd.datapacklinter

import com.github.chencmd.datapacklinter.ciplatform.CIPlatformInteractionInstr
import com.github.chencmd.datapacklinter.ciplatform.CIPlatformReadKeyedConfigInstr
import com.github.chencmd.datapacklinter.ciplatform.ghactions.*
import com.github.chencmd.datapacklinter.ciplatform.local.*
import com.github.chencmd.datapacklinter.dls.DLSConfig
import com.github.chencmd.datapacklinter.dls.DLSHelper
import com.github.chencmd.datapacklinter.linter.DatapackLinter
import com.github.chencmd.datapacklinter.linter.LinterConfig

import cats.data.EitherT
import cats.effect.Async
import cats.effect.ExitCode
import cats.effect.IOApp
import cats.effect.Resource
import cats.implicits.*

import typings.node.pathMod as path
import typings.node.processMod as process

object Main extends IOApp {
  override def run(args: List[String]) = {
    def run[F[_]: Async]() = for {
      dir        <- Async[F].delay(process.cwd())
      lintResult <- githubActionsContextResource[F](dir).use { ctx =>
        given CIPlatformInteractionInstr[F]     = ctx._1
        given CIPlatformReadKeyedConfigInstr[F] = ctx._2

        lint(dir)
      }.value
      exitCode   <- lintResult match {
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

  private def githubActionsContextResource[F[_]: Async](dir: String) = Resource.both(
    GitHubInteraction.createInstr(dir),
    GitHubInputReader.createInstr()
  )

  private def localContextResource[F[_]: Async](dir: String) = Resource.both(
    LocalInteraction.createInstr(),
    LocalInputReader.createInstr(dir)
  )

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
      _             <- EitherT.liftF(linter.lintAll(analyzedCount))
    } yield ()
  }
}

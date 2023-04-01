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
      dir <- Async[F].delay(process.cwd())
      res <- runForLocal[F](dir)
        .use(EitherT.pure(_))
        .value
        .flatMap { res =>
          res.fold(
            Async[F].delay(_).as(ExitCode.Error),
            Async[F].pure(_)
          )
        }
    } yield res

    run()
  }

  def runForGitHubActions[F[_]: Async](
    dir: String
  ): Resource[[A] =>> EitherT[F, String, A], ExitCode] = {
    for {
      given CIPlatformInteractionInstr[F]     <- GitHubInteraction.createInstr(dir)
      given CIPlatformReadKeyedConfigInstr[F] <- GitHubInputReader.createInstr()
      res                                     <- Resource.eval(lint(dir))
    } yield res
  }

  def runForLocal[F[_]: Async](
    dir: String
  ): Resource[[A] =>> EitherT[F, String, A], ExitCode] = {
    for {
      given CIPlatformInteractionInstr[F]     <- LocalInteraction.createInstr()
      given CIPlatformReadKeyedConfigInstr[F] <- LocalInputReader.createInstr(dir)
      res                                     <- Resource.eval(lint(dir))
    } yield res
  }

  def lint[F[_]: Async](dir: String)(using
    ciInteraction: CIPlatformInteractionInstr[F],
    readConfig: CIPlatformReadKeyedConfigInstr[F]
  ): EitherT[F, String, ExitCode] = {
    for {
      dlsConfig <- EitherT.liftF(DLSConfig.readConfig(path.join(dir, ".vscode", "settings.json")))
      dls       <- DLSHelper.createDLS(dir, dlsConfig)

      linterConfig <- LinterConfig.withReader()
      linter       <- EitherT.liftF(DatapackLinter(linterConfig, dls, dlsConfig))

      analyzedCount <- EitherT.liftF(linter.updateCache())
      _             <- EitherT.liftF(linter.lintAll(analyzedCount))
    } yield ExitCode.Success
  }
}

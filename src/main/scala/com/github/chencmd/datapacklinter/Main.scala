package com.github.chencmd.datapacklinter

import cats.{Applicative, Monad}
import cats.data.{EitherT, OptionT}
import cats.effect.{IO, IOApp, ExitCode, Async, Resource}
import cats.implicits.*

import com.github.chencmd.datapacklinter.generic.DLSConfigExtra.*
import com.github.chencmd.datapacklinter.dls.{DLSConfig, DLSHelper}
import com.github.chencmd.datapacklinter.linter.{DatapackLinter, LinterConfig}
import com.github.chencmd.datapacklinter.ciplatform.{
  CIPlatformInteractionInstr,
  CIPlatformReadKeyedConfigInstr
}
import com.github.chencmd.datapacklinter.ciplatform.local.*
import com.github.chencmd.datapacklinter.ciplatform.ghactions.*

import typings.node.processMod as process
import typings.node.pathMod as path
import typings.spgodingDatapackLanguageServer.libNodesIdentityNodeMod.IdentityNode

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

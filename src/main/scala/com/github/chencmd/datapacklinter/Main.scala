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
    def run[F[_]: Async]() = {
      runForLocal[F]
        .use(EitherT.pure(_))
        .value
        .flatMap { res =>
          res.fold(
            Async[F].delay(_).as(ExitCode.Error),
            Async[F].pure(_)
          )
        }
    }

    run()
  }

  def runForGitHubActions[F[_]: Async]: Resource[[A] =>> EitherT[F, String, A], ExitCode] = {
    for {
      dir <- Resource.eval(EitherT.liftF(Async[F].delay(process.cwd())))

      given CIPlatformInteractionInstr[F]     <- GitHubInteraction.createInstr(dir)
      given CIPlatformReadKeyedConfigInstr[F] <- GitHubInputReader.createInstr()
      res                                     <- Resource.eval(lint())
    } yield res
  }

  def runForLocal[F[_]: Async]: Resource[[A] =>> EitherT[F, String, A], ExitCode] = {
    for {
      dir <- Resource.eval(EitherT.liftF(Async[F].delay(process.cwd())))

      given CIPlatformInteractionInstr[F]     <- LocalInteraction.createInstr()
      given CIPlatformReadKeyedConfigInstr[F] <- LocalInputReader.createInstr(dir)
      res                                     <- Resource.eval(lint())
    } yield res
  }

  def lint[F[_]: Async]()(using
    ciInteraction: CIPlatformInteractionInstr[F],
    readConfig: CIPlatformReadKeyedConfigInstr[F]
  ): EitherT[F, String, ExitCode] = {
    val dir            = process.cwd()
    val configFilePath = path.join(dir, ".vscode", "settings.json")

    for {
      dlsConfig <- EitherT.liftF(DLSConfig.readConfig[F](configFilePath))
      dls       <- DLSHelper.createDLS[F](dir, dlsConfig)

      linterConfig <- LinterConfig.withReader()
      linter       <- EitherT.liftF(DatapackLinter[F](linterConfig, dls, dlsConfig))

      analyzedCount <- EitherT.liftF(linter.updateCache())
      _             <- EitherT.liftF(linter.lintAll(analyzedCount))
    } yield ExitCode.Success
  }
}

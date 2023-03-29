package com.github.chencmd.datapacklinter

import cats.{Applicative, Monad}
import cats.data.{EitherT, OptionT}
import cats.effect.{IO, IOApp, ExitCode, Async}
import cats.implicits.*

import com.github.chencmd.datapacklinter.generic.DLSConfigExtra.*
import com.github.chencmd.datapacklinter.dls.{DLSConfig, DLSHelper}
import com.github.chencmd.datapacklinter.linter.{DatapackLinter, LinterConfig}
import com.github.chencmd.datapacklinter.ciplatform.{
  PlatformContext,
  CIPlatformInteraction,
  KeyedConfigReader
}
import com.github.chencmd.datapacklinter.ciplatform.KeyedConfigReader.ConfigValueType
import com.github.chencmd.datapacklinter.ciplatform.local.*
import com.github.chencmd.datapacklinter.ciplatform.ghactions.*

import typings.node.processMod as process
import typings.node.pathMod as path
import typings.spgodingDatapackLanguageServer.libNodesIdentityNodeMod.IdentityNode

object Main extends IOApp {
  override def run(args: List[String]): IO[ExitCode] = runForLocal[IO]

  def runForGitHubActions[F[_]: Async]: F[ExitCode] = {
    for {
      ciInteraction <- Async[F].delay(new GitHubInteraction)
      inputReader   <- Async[F].delay(new GitHubInputReader)
      res           <- lint(PlatformContext(ciInteraction, inputReader))
    } yield res
  }

  def runForLocal[F[_]: Async]: F[ExitCode] = {
    for {
      ciInteraction <- Async[F].delay(new LocalInteraction)
      inputReader   <- Async[F].delay(new LocalInputReader)
      res           <- lint(PlatformContext(ciInteraction, inputReader))
    } yield res
  }

  def lint[F[_]: Async](ctx: PlatformContext[F]): F[ExitCode] = {
    val dir            = process.cwd()
    val configFilePath = path.join(dir, ".vscode", "settings.json")

    given CIPlatformInteraction[F] = ctx.ciInteraction

    val program = for {
      _ <- ctx.initialize(dir)

      dlsConfig <- EitherT.liftF(DLSConfig.readConfig[F](configFilePath))
      dls       <- DLSHelper.createDLS[F](dir, dlsConfig)

      linterConfig <- LinterConfig.withReader(ctx.keyedConfigReader)
      linter       <- EitherT.liftF(DatapackLinter[F](linterConfig, dls, dlsConfig))

      analyzedCount <- EitherT.liftF(linter.updateCache())
      _             <- EitherT.liftF(linter.lintAll(analyzedCount))

      _ <- ctx.finalize(dir)
    } yield ExitCode.Success

    program.value
      .flatMap { res =>
        res.fold(
          e => Async[F].delay(println(e)) as ExitCode.Error,
          a => Monad[F].pure(a)
        )
      }
  }
}

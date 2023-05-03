package com.github.chencmd.datapacklinter.linter

import com.github.chencmd.datapacklinter.ciplatform.CIPlatformInteractionInstr
import com.github.chencmd.datapacklinter.ciplatform.CIPlatformReadKeyedConfigInstr

import cats.data.EitherT
import cats.effect.Async

import typings.minimatch.mod as minimatch
import typings.minimatch.mod.IOptions

final case class LinterConfig private (
  forcePass: Boolean,
  muteSuccessResult: Boolean,
  ignorePaths: List[String],
  checkAlwaysAllFile: Boolean
)

object LinterConfig {
  def withReader[F[_]: Async]()(using
    ciInteraction: CIPlatformInteractionInstr[F],
    reader: CIPlatformReadKeyedConfigInstr[F]
  ): EitherT[F, String, LinterConfig] = {
    for {
      forcePass          <- reader.readKeyOrElse("forcePass", false)
      muteSuccessResult  <- reader.readKeyOrElse("notOutputSuccess", false)
      ignorePaths        <- reader.readKeyOrElse("ignoreLintPathPattern", List.empty)
      checkAlwaysAllFile <- reader.readKeyOrElse("checkAlwaysAllFile", false)
    } yield LinterConfig(forcePass, muteSuccessResult, ignorePaths, checkAlwaysAllFile)
  }
}

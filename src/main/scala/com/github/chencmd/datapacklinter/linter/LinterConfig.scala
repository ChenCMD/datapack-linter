package com.github.chencmd.datapacklinter.linter

import com.github.chencmd.datapacklinter.ciplatform.KeyedConfigReader

import cats.data.EitherT
import cats.effect.Async

final case class LinterConfig private (
  forcePass: Boolean,
  muteSuccessResult: Boolean,
  ignoreLintPathPattern: List[String],
  checkAlwaysAllFile: Boolean
)

object LinterConfig {
  def withReader[F[_]: Async](reader: KeyedConfigReader[F]): EitherT[F, String, LinterConfig] = {
    for {
      forcePass             <- reader.readKeyOrElse("forcePass", false)
      muteSuccessResult     <- reader.readKeyOrElse("notOutputSuccess", false)
      ignoreLintPathPattern <- reader.readKeyOrElse("ignoreLintPathPattern", List.empty)
      checkAlwaysAllFile    <- reader.readKeyOrElse("checkAlwaysAllFile", false)
    } yield LinterConfig(forcePass, muteSuccessResult, ignoreLintPathPattern, checkAlwaysAllFile)
  }
}

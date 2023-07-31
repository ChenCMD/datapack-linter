package com.github.chencmd.datapacklinter.linter

import com.github.chencmd.datapacklinter.ciplatform.CIPlatformInteractionInstr
import com.github.chencmd.datapacklinter.ciplatform.CIPlatformReadKeyedConfigInstr
import com.github.chencmd.datapacklinter.generic.RaiseNec
import com.github.chencmd.datapacklinter.utils.JSObject

import cats.effect.Async
import cats.implicits.*

import scala.scalajs.js
import scala.scalajs.js.JSConverters.*

final case class LinterConfig private (
  forcePass: Boolean,
  muteSuccessResult: Boolean,
  ignorePaths: List[String],
  checkAlwaysAllFile: Boolean
) {
  def toJSObject: js.Object & js.Dynamic = {
    JSObject(
      "forcePass"          -> forcePass,
      "muteSuccessResult"  -> muteSuccessResult,
      "ignorePaths"        -> ignorePaths.toJSArray,
      "checkAlwaysAllFile" -> checkAlwaysAllFile
    )
  }
}

object LinterConfig {
  def withReader[F[_]: Async]()(using
    R: RaiseNec[F, String],
    ciInteraction: CIPlatformInteractionInstr[F],
    reader: CIPlatformReadKeyedConfigInstr[F]
  ): F[LinterConfig] = {
    for {
      forcePass          <- reader.readKeyOrElse("forcePass", false)
      muteSuccessResult  <- reader.readKeyOrElse("notOutputSuccess", false)
      ignorePaths        <- reader.readKeyOrElse("ignoreLintPathPattern", List.empty)
      checkAlwaysAllFile <- reader.readKeyOrElse("checkAlwaysAllFile", false)
      config             <- {
        (forcePass, muteSuccessResult, ignorePaths, checkAlwaysAllFile)
          .mapN(LinterConfig.apply)
          .fold(R.raise, _.pure[F])
      }
    } yield config
  }
}

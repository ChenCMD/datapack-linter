package com.github.chencmd.datapacklinter.linter

import com.github.chencmd.datapacklinter.ciplatform.CIPlatformInteractionInstr
import com.github.chencmd.datapacklinter.ciplatform.CIPlatformReadKeyedConfigInstr
import com.github.chencmd.datapacklinter.generic.RaiseNec
import com.github.chencmd.datapacklinter.utils.JSObject
import com.github.chencmd.datapacklinter.utils.Path

import cats.effect.Async
import cats.implicits.*

import scala.scalajs.js
import scala.scalajs.js.JSConverters.*

final case class LinterConfig private (
  lintDirectory: Path,
  configPath: Path,
  forcePass: Boolean,
  muteSuccessResult: Boolean,
  ignorePaths: List[String],
  alwaysCheckAllFiles: Boolean
) {
  def toJSObject: js.Object & js.Dynamic = {
    JSObject(
      "lintDirectory"       -> lintDirectory.toString,
      "configPath"          -> configPath.toString,
      "forcePass"           -> forcePass,
      "muteSuccessResult"   -> muteSuccessResult,
      "ignorePaths"         -> ignorePaths.toJSArray,
      "alwaysCheckAllFiles" -> alwaysCheckAllFiles
    )
  }
}

object LinterConfig {
  def withReader[F[_]: Async](dir: Path)(using
    R: RaiseNec[F, String],
    ciInteraction: CIPlatformInteractionInstr[F],
    reader: CIPlatformReadKeyedConfigInstr[F]
  ): F[LinterConfig] = {
    for {
      lintDirectory       <- reader.readKeyOrElse("lintDirectory", ".")
      configPath          <- reader.readKeyOrElse("configPath", ".vscode/settings.json")
      forcePass           <- reader.readKeyOrElse("forcePass", false)
      muteSuccessResult   <- reader.readKeyOrElse("muteSuccessResult", false)
      ignorePaths         <- reader.readKeyOrElse("ignoreLintPathPattern", List.empty[String])
      alwaysCheckAllFiles <- reader.readKeyOrElse("alwaysCheckAllFiles", false)
      config              <- {
        (
          lintDirectory.map(Path.join(dir, _)),
          configPath.map(Path.join(dir, _)),
          forcePass,
          muteSuccessResult,
          ignorePaths,
          alwaysCheckAllFiles
        )
          .mapN(LinterConfig.apply)
          .fold(R.raise, _.pure[F])
      }
    } yield config
  }
}

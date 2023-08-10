package com.github.chencmd.datapacklinter.ciplatform.local

import com.github.chencmd.datapacklinter.ciplatform.CIPlatformReadKeyedConfigInstr
import com.github.chencmd.datapacklinter.generic.RaiseNec
import com.github.chencmd.datapacklinter.utils.FSAsync
import com.github.chencmd.datapacklinter.utils.JSObject
import com.github.chencmd.datapacklinter.utils.Path

import cats.effect.Async
import cats.implicits.*

import scala.scalajs.js.JSON

object FileInputReader {
  def createInstr[F[_]: Async](
    configPath: Path
  )(using R: RaiseNec[F, String]): F[CIPlatformReadKeyedConfigInstr[F]] = for {
    rawConfig <- FSAsync.readFileOpt(configPath)
    _         <- Async[F].whenA(rawConfig.isEmpty)(R.raiseOne("linter-config.json does not exist"))
    config = JSObject.toWrappedDictionary[String](JSON.parse(rawConfig.get))

    instr = new CIPlatformReadKeyedConfigInstr[F] {
      override protected def read(key: String): F[Option[String]] = {
        config.get(key).filter(_.nonEmpty).pure[F]
      }
    }
  } yield instr
}

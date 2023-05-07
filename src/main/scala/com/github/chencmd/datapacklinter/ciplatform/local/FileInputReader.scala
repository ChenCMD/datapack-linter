package com.github.chencmd.datapacklinter.ciplatform.local

import com.github.chencmd.datapacklinter.ciplatform.CIPlatformReadKeyedConfigInstr
import com.github.chencmd.datapacklinter.generic.RaiseNec.*
import com.github.chencmd.datapacklinter.utils.FSAsync
import com.github.chencmd.datapacklinter.utils.JSObject

import cats.Monad
import cats.effect.Async
import cats.implicits.*

import scala.scalajs.js.JSON

object FileInputReader {
  import CIPlatformReadKeyedConfigInstr.ConfigValueType

  def createInstr[F[_]: Async](
    configPath: String
  )(using R: RaiseNec[F, String]): F[CIPlatformReadKeyedConfigInstr[F]] = for {
    existsConfig <- FSAsync.pathAccessible(configPath)
    rawConfig    <- {
      if (existsConfig) {
        FSAsync.readFile(configPath)
      } else {
        R.raiseOne("linter-config.json does not exist")
      }
    }
    config       <- Monad[F].pure(JSObject.toWrappedDictionary[String](JSON.parse(rawConfig)))

    instr <- Monad[F].pure {
      new CIPlatformReadKeyedConfigInstr[F] {
        override protected def read(key: String): F[Option[String]] = Monad[F].pure {
          config.get(key).filter(_.nonEmpty)
        }
      }
    }
  } yield instr
}

package com.github.chencmd.datapacklinter.ciplatform.local

import com.github.chencmd.datapacklinter.ciplatform.CIPlatformInteractionInstr
import com.github.chencmd.datapacklinter.ciplatform.CIPlatformReadKeyedConfigInstr
import com.github.chencmd.datapacklinter.utils.FSAsync
import com.github.chencmd.datapacklinter.utils.JSObject

import cats.data.EitherT
import cats.effect.Async
import cats.effect.Resource
import cats.implicits.*

import scala.scalajs.js
import scala.scalajs.js.JSON

import typings.node.pathMod as path

object FileInputReader {
  import CIPlatformReadKeyedConfigInstr.ConfigValueType

  def createInstr[F[_]: Async](
    configPath: String
  ): EitherT[F, String, CIPlatformReadKeyedConfigInstr[F]] = for {
    existsConfig <- FSAsync.pathAccessible[EitherT[F, String, _]](configPath)
    rawConfig    <- {
      if (existsConfig) {
        EitherT.right(FSAsync.readFile(configPath))
      } else {
        EitherT
          .left(FSAsync.writeFile(configPath, "{}"))
          .leftMap(_ => "linter-config.json does not exist")
      }
    }
    config       <- EitherT.pure(JSObject.toWrappedDictionary[String](JSON.parse(rawConfig)))

    instr <- EitherT.pure {
      new CIPlatformReadKeyedConfigInstr[F] {
        override protected def readKey[A](
          key: String,
          required: Boolean,
          default: => Option[A]
        )(using
          ciInteraction: CIPlatformInteractionInstr[F],
          valueType: ConfigValueType[A]
        ): EitherT[F, String, A] = EitherT.fromEither {
          Right(config.get(key))
            .filterOrElse(_.isDefined || !required, s"Input required and not supplied: $key")
            .flatMap(_.traverse(valueType.tryCast(key, _)))
            .map(_.getOrElse(default.get))
        }
      }
    }
  } yield instr
}

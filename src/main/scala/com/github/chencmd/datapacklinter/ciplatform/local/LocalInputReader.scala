package com.github.chencmd.datapacklinter.ciplatform.local

import com.github.chencmd.datapacklinter.ciplatform.CIPlatformReadKeyedConfigInstr
import com.github.chencmd.datapacklinter.utils.{FSAsync, JSObject}

import cats.data.EitherT
import cats.effect.{Async, Resource}
import cats.implicits.*

import scala.util.chaining.*

import scalajs.js

import typings.node.pathMod as path

object LocalInputReader {
  import CIPlatformReadKeyedConfigInstr.ConfigValueType

  def createInstr[F[_]: Async](
    dir: String
  ): Resource[[A] =>> EitherT[F, String, A], CIPlatformReadKeyedConfigInstr[F]] = {
    val configPath = path.join(dir, "linter-config.json")
    val program = for {
      existsConfig <- FSAsync.pathAccessible[[A] =>> EitherT[F, String, A]](configPath)
      rawConfig    <- {
        if (existsConfig) {
          EitherT.right(FSAsync.readFile(configPath))
        } else {
          EitherT
            .left(FSAsync.writeFile(configPath, "{}"))
            .leftMap(_ => "linter-config.json does not exist")
        }
      }
      config       <- EitherT.pure(JSObject.toWrappedDictionary[String](js.JSON.parse(rawConfig)))

      instr <- EitherT.liftF(Async[F].delay {
        new CIPlatformReadKeyedConfigInstr[F] {
          override protected def readKey[A](
            key: String,
            required: Boolean,
            default: => Option[A]
          )(using
            valueType: ConfigValueType[A]
          ): EitherT[F, String, A] = {
            EitherT
              .fromEither {
                Right(config.get(key))
                  .filterOrElse(_.isDefined || !required, s"Input required and not supplied: $key")
                  .flatMap(_.traverse(valueType.tryCast(key, _)))
                  .map(_.getOrElse(default.get))
              }
          }
        }
      })
    } yield instr

    Resource.eval(program)
  }
}

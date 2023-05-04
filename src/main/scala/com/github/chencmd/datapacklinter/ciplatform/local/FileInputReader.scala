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
import cats.mtl.Raise
import cats.Monad

object FileInputReader {
  import CIPlatformReadKeyedConfigInstr.ConfigValueType

  def createInstr[F[_]: Async](
    configPath: String
  )(using raise: Raise[F, String]): F[CIPlatformReadKeyedConfigInstr[F]] = for {
    existsConfig <- FSAsync.pathAccessible(configPath)
    rawConfig    <- {
      if (existsConfig) {
        FSAsync.readFile(configPath)
      } else {
        raise.raise("linter-config.json does not exist")
      }
    }
    config       <- Monad[F].pure(JSObject.toWrappedDictionary[String](JSON.parse(rawConfig)))

    instr <- Monad[F].pure {
      new CIPlatformReadKeyedConfigInstr[F] {
        override protected def readKey[A](
          key: String,
          required: Boolean,
          default: => Option[A]
        )(using
          raise: Raise[F, String],
          ciInteraction: CIPlatformInteractionInstr[F],
          valueType: ConfigValueType[A]
        ): F[A] = for {
          v <- Monad[F].pure(config.get(key))
          _ <- ciInteraction.printDebug(s"read key: $key, result: $v")
          _ <- Monad[F].whenA(v.isEmpty && required) {
            raise.raise(s"Input required and not supplied: $key")
          }
          b <- v.traverse(valueType.tryCast(key, _)) match {
            case Right(value) => Monad[F].pure(value)
            case Left(value)  => raise.raise(value)
          }
        } yield b.getOrElse(default.get)
      }
    }
  } yield instr
}

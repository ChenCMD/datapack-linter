package com.github.chencmd.datapacklinter.ciplatform.ghactions

import com.github.chencmd.datapacklinter.ciplatform.CIPlatformInteractionInstr
import com.github.chencmd.datapacklinter.ciplatform.CIPlatformReadKeyedConfigInstr

import cats.Monad
import cats.data.EitherT
import cats.effect.Async
import cats.effect.Resource
import cats.implicits.*

import scala.util.chaining.*

import typings.node.processMod as process
import cats.mtl.Raise

object EnvironmentInputReader {
  import CIPlatformReadKeyedConfigInstr.ConfigValueType

  def createInstr[F[_]: Async](
    keyConverter: String => String
  ): CIPlatformReadKeyedConfigInstr[F] = new CIPlatformReadKeyedConfigInstr[F] {
    override protected def readKey[A](key: String, required: Boolean, default: => Option[A])(using
      raise: Raise[F, String],
      ciInteraction: CIPlatformInteractionInstr[F],
      valueType: ConfigValueType[A]
    ): F[A] = for {
      v <- Async[F].delay {
        process.env
          .get(keyConverter(key))
          .flatMap(_.toOption)
          .filter(_.nonEmpty)
      }
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

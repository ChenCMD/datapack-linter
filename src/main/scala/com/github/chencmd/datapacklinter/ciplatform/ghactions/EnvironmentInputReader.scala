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

object EnvironmentInputReader {
  import CIPlatformReadKeyedConfigInstr.ConfigValueType

  def createInstr[F[_]: Async](
    keyConverter: String => String
  ): CIPlatformReadKeyedConfigInstr[F] = new CIPlatformReadKeyedConfigInstr[F] {
    override protected def readKey[A](key: String, required: Boolean, default: => Option[A])(using
      ciInteraction: CIPlatformInteractionInstr[F],
      valueType: ConfigValueType[A]
    ): EitherT[F, String, A] = for {
      v <- EitherT.liftF(Async[F].delay {
        process.env
          .get(keyConverter(key))
          .flatMap(_.toOption)
          .filter(_.nonEmpty)
      })
      _ <- EitherT.liftF(ciInteraction.printDebug(s"read key: $key, result: $v"))
      _ <- EitherT.cond(v.isDefined || !required, (), s"Input required and not supplied: $key")
      b <- EitherT.fromEither(v.traverse(valueType.tryCast(key, _)))
    } yield b.getOrElse(default.get)
  }
}

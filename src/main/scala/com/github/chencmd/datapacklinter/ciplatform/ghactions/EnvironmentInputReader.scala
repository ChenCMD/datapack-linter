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
    override protected def read(key: String): F[Option[String]] = Async[F].delay {
      process.env
        .get(keyConverter(key))
        .flatMap(_.toOption)
        .filter(_.nonEmpty)
    }
  }
}

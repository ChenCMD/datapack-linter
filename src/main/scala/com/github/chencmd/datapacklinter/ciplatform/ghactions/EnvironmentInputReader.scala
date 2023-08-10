package com.github.chencmd.datapacklinter.ciplatform.ghactions

import com.github.chencmd.datapacklinter.ciplatform.CIPlatformReadKeyedConfigInstr

import cats.effect.Async

import typings.node.processMod.^ as process

object EnvironmentInputReader {
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

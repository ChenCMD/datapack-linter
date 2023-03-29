package com.github.chencmd.datapacklinter.ciplatform

import com.github.chencmd.datapacklinter.ciplatform.KeyedConfigReader

import cats.data.EitherT
import cats.effect.{Async, Resource}
import cats.implicits.*

final case class PlatformContext[F[_]: Async](
  ciInteraction: CIPlatformInteraction[F],
  keyedConfigReader: KeyedConfigReader[F]
) {
  def initialize(dir: String): EitherT[F, String, Unit] = {
    for {
      _ <- ciInteraction.initialize(dir)
      _ <- keyedConfigReader.initialize(dir)
    } yield ()
  }

  def finalize(dir: String): EitherT[F, String, Unit] = {
    for {
      _ <- ciInteraction.finalize(dir)
      _ <- keyedConfigReader.finalize(dir)
    } yield ()
  }
}

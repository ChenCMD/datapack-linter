package com.github.chencmd.datapacklinter.ciplatform

import cats.Monad
import cats.data.EitherT
import cats.effect.Async

trait Lifecycle[F[_]: Async] {
  def initialize(dir: String): EitherT[F, String, Unit] =
    Monad[[A] =>> EitherT[F, String, A]].pure(())

  def finalize(dir: String): EitherT[F, String, Unit] =
    Monad[[A] =>> EitherT[F, String, A]].pure(())
}

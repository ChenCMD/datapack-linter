package com.github.chencmd.datapacklinter.generic

import cats.Applicative
import cats.data.EitherT
import cats.implicits.*

object EitherTExtra {
  def exitWhenF[F[_]: Applicative, A](cond: Boolean)(exitValue: => F[A]): EitherT[F, A, Unit] = {
    if cond then EitherT.left(exitValue)
    else EitherT.pure(())
  }

  def exitWhenA[F[_]: Applicative, A](cond: Boolean)(exitValue: => A): EitherT[F, A, Unit] = {
    if cond then EitherT.left(exitValue.pure[F])
    else EitherT.pure(())
  }
}

package com.github.chencmd.datapacklinter.generic

import cats.Applicative
import cats.data.EitherT

object EitherTExtra {
  def exitWhenF[F[_]: Applicative, A](cond: Boolean)(exitValue: => F[A]): EitherT[F, A, Unit] = {
    if cond then EitherT.left(exitValue)
    else EitherT.pure(())
  }

  def exitWhenA[F[_]: Applicative, A](cond: Boolean)(exitValue: => A): EitherT[F, A, Unit] = {
    if cond then EitherT.left(Applicative[F].pure(exitValue))
    else EitherT.pure(())
  }
}

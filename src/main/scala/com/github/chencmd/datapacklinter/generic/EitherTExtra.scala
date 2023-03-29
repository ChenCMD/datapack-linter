package com.github.chencmd.datapacklinter.generic

import cats.Applicative
import cats.data.EitherT

object EitherTExtra {
  def exitWhenA[F[_]: Applicative, A](cond: Boolean)(exitValue: => F[A]): EitherT[F, A, Unit] = {
    if cond then EitherT.left(exitValue)
    else EitherT.pure(())
  }

  // extension [F[_]: Applicative, A](exitValue: => F[A]) {
  //   def exitWhenA(cond: Boolean): EitherT[F, A, Unit] = EitherTExtra.exitWhenA(cond)(exitValue)
  // }
}

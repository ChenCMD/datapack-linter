package com.github.chencmd.datapacklinter.generic

import cats.Applicative
import cats.implicits.given

object ApplicativeExtra {
  def whenA[F[_]: Applicative, A](
    cond: Boolean
  )(action: => F[A]): F[Option[A]] = {
    if cond then Applicative[F].map(action)(_.some)
    else Applicative[F].pure(None)
  }

  def unlessA[F[_]: Applicative, A](
    cond: Boolean
  )(action: => F[A]): F[Option[A]] = {
    if cond then Applicative[F].pure(None)
    else Applicative[F].map(action)(_.some)
  }
}

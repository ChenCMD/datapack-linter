package com.github.chencmd.datapacklinter.generic

import cats.Applicative
import cats.syntax.all.*
import cats.kernel.Monoid
import cats.Functor

object ApplicativeExtra {
  extension [F[_]: Applicative, A](action: => F[A]) {
    def whenOrPureNoneA(cond: Boolean): F[Option[A]] = {
      if cond then Functor[F].map(action)(_.some)
      else Applicative[F].pure(None)
    }

    def unlessOrPureNoneA(cond: Boolean): F[Option[A]] = {
      if cond then Applicative[F].pure(None)
      else Functor[F].map(action)(_.some)
    }
  }
}

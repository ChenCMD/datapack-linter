package com.github.chencmd.datapacklinter.generic

import cats.Applicative
import cats.Functor
import cats.MonoidK
import cats.implicits.*

object ApplicativeExtra {
  def whenAOrPureNone[F[_]: Applicative, A](cond: Boolean)(action: => F[A]): F[Option[A]] = {
    whenAOrPureEmpty(cond)(Functor[F].map(action)(_.some))
  }

  def unlessAOrPureNone[F[_]: Applicative, A](cond: Boolean)(action: => F[A]): F[Option[A]] = {
    whenAOrPureEmpty(!cond)(Functor[F].map(action)(_.some))
  }

  def whenAOrPureEmpty[F[_]: Applicative, M[_]: MonoidK, A](cond: Boolean)(action: => F[M[A]]): F[M[A]] = {
    if cond then action else MonoidK[M].empty.pure[F]
  }
}

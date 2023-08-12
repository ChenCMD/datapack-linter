package com.github.chencmd.datapacklinter.generic

import cats.effect.Async
import cats.effect.implicits.*
import cats.implicits.*

import scala.scalajs.js

object AsyncExtra {
  class PartiallyAppliedFromPromise[F[_]](private val dummy: Boolean = true) extends AnyVal {
    def apply[A](thunk: => js.Promise[A])(using Async[F]): F[A] = {
      Async[F].fromPromise(Async[F].delay(thunk))
    }
  }

  def fromPromise[F[_]]: PartiallyAppliedFromPromise[F] = {
    new PartiallyAppliedFromPromise[F]
  }

  def coerceOrphansTask[F[_]: Async, A](expensiveComputation: F[A]): F[A] = {
    Async[F].cede *> expensiveComputation.guarantee(Async[F].cede)
  }
}

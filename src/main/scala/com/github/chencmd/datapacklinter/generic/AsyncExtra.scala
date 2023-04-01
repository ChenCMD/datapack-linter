package com.github.chencmd.datapacklinter.generic

import cats.effect.Async

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
}

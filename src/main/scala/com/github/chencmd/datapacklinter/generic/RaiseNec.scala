package com.github.chencmd.datapacklinter.generic

import cats.Functor
import cats.data.NonEmptyChain
import cats.mtl.Raise

trait RaiseNec[F[_], -E] extends Raise[F, NonEmptyChain[E]] {
  def raiseOne[A](a: E): F[A] = raise(NonEmptyChain.one(a))
}

object RaiseNec {
  given [F[_]: Functor, E](using F: Raise[F, NonEmptyChain[E]]): RaiseNec[F, E] with {
    def functor                                       = Functor[F]
    def raise[E2 <: NonEmptyChain[E], A](e: E2): F[A] = F.raise(e)
  }
}

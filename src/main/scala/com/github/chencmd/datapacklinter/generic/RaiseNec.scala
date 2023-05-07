package com.github.chencmd.datapacklinter.generic

import cats.data.NonEmptyChain
import cats.mtl.Raise

package object RaiseNec {
  type RaiseNec[F[_], E] = Raise[F, NonEmptyChain[E]]

  extension [F[_], E](R: Raise[F, NonEmptyChain[E]]) {
    def raiseOne[A](a: E): F[A] = R.raise(NonEmptyChain.one(a))
  }
}

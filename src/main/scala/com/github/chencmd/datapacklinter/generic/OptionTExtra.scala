package com.github.chencmd.datapacklinter.generic

import cats.Applicative
import cats.Functor
import cats.data.OptionT

object OptionTExtra {
  def exitWhenF[F[_]: Applicative](cond: Boolean)(action: => F[Unit]): OptionT[F, Unit] = {
    if cond then OptionT(Functor[F].as(action, None)) else OptionT.pure(())
  }

  def exitUnlessF[F[_]: Applicative](cond: Boolean)(action: => F[Unit]): OptionT[F, Unit] = {
    exitWhenF(!cond)(action)
  }
}

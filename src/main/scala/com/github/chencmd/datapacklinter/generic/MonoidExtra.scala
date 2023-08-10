package com.github.chencmd.datapacklinter.generic

import cats.MonoidK

object MonoidExtra {
  extension [F[_]: MonoidK, A](action: => F[A]) {
    def when(cond: Boolean): F[A] = {
      if cond then action else MonoidK[F].empty[A]
    }

    def unless(cond: Boolean): F[A] = {
      if cond then MonoidK[F].empty[A] else action
    }
  }
}

package com.github.chencmd.datapacklinter.generic

import cats.kernel.Monoid

object MonoidExtra {
  def when[A: Monoid](cond: Boolean)(a: => A): A = {
    if cond then a else Monoid.empty[A]
  }

  def unless[A: Monoid](cond: Boolean)(a: => A): A = {
    if cond then Monoid.empty[A] else a
  }
}

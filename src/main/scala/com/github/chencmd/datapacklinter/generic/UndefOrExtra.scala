package com.github.chencmd.datapacklinter.generic

import cats.kernel.Monoid

import scala.scalajs.js

object UndefOrExtra {
  extension [A](a: js.UndefOr[A]) {
    def orEmpty(using MonoidA: Monoid[A]): A = a.getOrElse(MonoidA.empty)
  }
}

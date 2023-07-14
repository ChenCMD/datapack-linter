package com.github.chencmd.datapacklinter.generic

import cats.Monoid

import scala.scalajs.js

package object Instances {
  given monoidForJSArray[A]: Monoid[js.Array[A]] with {
    def empty: js.Array[A] = js.Array()

    def combine(x: js.Array[A], y: js.Array[A]): js.Array[A] = x ++ y
  }
}

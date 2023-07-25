package com.github.chencmd.datapacklinter.generic

import cats.Monoid
import cats.implicits.*

object MapExtra {
  extension [K, V](m: Map[K, V]) {
    def getOrEmpty(key: K)(using M: Monoid[V]): V = {
      m.get(key).orEmpty
    }
  }
}

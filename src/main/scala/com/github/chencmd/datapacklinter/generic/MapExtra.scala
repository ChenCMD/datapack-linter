package com.github.chencmd.datapacklinter.generic

import cats.Monoid
import cats.implicits.*

object MapExtra {
  extension [K, V](m: Map[K, V]) {
    def getOrEmpty(key: K)(using M: Monoid[V]): V = {
      m.get(key).orEmpty
    }

    def mapKeys[K1](f: K => K1): Map[K1, V] = {
      m.map { case (k, v) => f(k) -> v }
    }
  }
}

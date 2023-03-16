package com.github.chencmd.datapacklinter.generic

import scala.reflect.TypeTest

object CastOps {
  extension [A](value: A) {
    def downcastOrNone[B](using tt: TypeTest[A, B]): Option[A & B] = {
      value match {
        case tt(b) => Some(b)
        case _     => None
      }
    }
  }
}

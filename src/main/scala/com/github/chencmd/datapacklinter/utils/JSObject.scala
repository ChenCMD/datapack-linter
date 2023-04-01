package com.github.chencmd.datapacklinter.utils

import scala.scalajs.js
import scala.scalajs.js.WrappedDictionary

object JSObject {
  def isObject(value: js.Any): Boolean = {
    js.typeOf(value) == "object" && value != null
  }

  def toWrappedDictionary[A](value: js.Any): WrappedDictionary[A] = {
    WrappedDictionary(value.asInstanceOf[js.Dictionary[A]])
  }
}

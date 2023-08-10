package com.github.chencmd.datapacklinter.utils

import scala.language.dynamics

import scala.scalajs.js
import scala.scalajs.js.Dynamic
import scala.scalajs.js.WrappedDictionary

import org.scalablytyped.runtime.StringDictionary

object JSObject extends scala.Dynamic {
  def applyDynamicNamed(name: String)(fields: (String, js.Any)*): js.Object & js.Dynamic = {
    Dynamic.literal(fields*)
  }

  def applyDynamic(name: String)(fields: (String, js.Any)*): js.Object & js.Dynamic = {
    Dynamic.literal(fields*)
  }

  def isObject(value: js.Any): Boolean = {
    js.typeOf(value) == "object" && value != null
  }

  def entries[A](obj: StringDictionary[A]): List[(String, A)] = {
    JSObject.toWrappedDictionary[A](obj).toList
  }

  def toWrappedDictionary[A](value: js.Any): WrappedDictionary[A] = {
    WrappedDictionary(value.asInstanceOf[js.Dictionary[A]])
  }
}

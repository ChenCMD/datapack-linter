package com.github.chencmd.datapacklinter.generic

import scala.scalajs.js.WrappedDictionary

import org.scalablytyped.runtime.StringDictionary

object WrappedDictionaryExtra {
  extension [A](wDict: WrappedDictionary[A]) {
    def updateWith(key: String)(f: Option[A] => Option[A]): WrappedDictionary[A] = {
      f(wDict.get(key)) match {
        case Some(v) => wDict.addOne(key, v)
        case None    =>
          wDict.remove(key)
          wDict
      }
    }

    def toStringDictionary: StringDictionary[A] = {
      WrappedDictionary.toJSDictionary(wDict).asInstanceOf[StringDictionary[A]]
    }
  }
}

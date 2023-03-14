package com.github.chencmd.datapacklinter.utils

import scala.collection.mutable
import typings.jsoncParser.mod as jsonc
import scalajs.js
import cats.syntax.all.*

object Jsonc {
  def parse(rawJson: String): Option[js.Any] = {
    try jsonc.parse(rawJson).asInstanceOf[js.Any].some
    catch _ => None
  }
}

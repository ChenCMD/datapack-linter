package com.github.chencmd.datapacklinter.utils

import cats.implicits.*

import scalajs.js

import typings.jsoncParser.mod as jsonc

object Jsonc {
  def parse(rawJson: String): Option[js.Any] = {
    try jsonc.parse(rawJson).asInstanceOf[js.Any].some
    catch _ => None
  }
}

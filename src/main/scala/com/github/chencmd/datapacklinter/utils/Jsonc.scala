package com.github.chencmd.datapacklinter.utils

import scala.scalajs.js

import typings.jsoncParser.mod as jsonc

object Jsonc {
  def parse(rawJson: String): Option[js.Any] = {
    try Some(jsonc.parse(rawJson).asInstanceOf[js.Any])
    catch _ => None
  }
}

package com.github.chencmd.datapacklinter.utils

import scala.scalajs.js

import typings.node.cryptoMod as crypto
import typings.node.cryptoMod.HexBase64Latin1Encoding
import typings.objectHash.mod as objectHash

object Hash {
  def stringToHash(str: String): String = {
    crypto.createHash("sha1").update(str).digest(HexBase64Latin1Encoding.hex)
  }

  def objectToHash(obj: js.Object): String = {
    objectHash.sha1(obj)
  }
}

package com.github.chencmd.datapacklinter.utils

import com.github.chencmd.datapacklinter.term.Checksum

import scala.scalajs.js

import typings.node.cryptoMod as crypto
import typings.node.cryptoMod.BinaryToTextEncoding
import typings.objectHash.mod as objectHash

object Hash {
  def stringToHash(str: String): Checksum = {
    crypto.createHash("sha1").update(str).digest(BinaryToTextEncoding.hex).asInstanceOf[Checksum]
  }

  def objectToHash(obj: js.Object): Checksum = {
    objectHash.sha1(obj).asInstanceOf[Checksum]
  }
}

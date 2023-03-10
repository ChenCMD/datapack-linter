package com.github.chencmd.datapacklinter.generic

import cats.effect.IO

import typings.node.fsMod.promises as fsp
import typings.node.BufferEncoding

object FSPExtra {
  def pathAccessible(path: String): IO[Boolean] = {
    IO.fromPromise(IO(fsp.access(path)))
      .map(_ => true)
      .handleError(_ => false)
  }

  def readFile(path: String): IO[String] = {
    IO.fromPromise(IO(fsp.readFile(path, BufferEncoding.utf8)))
  }
}

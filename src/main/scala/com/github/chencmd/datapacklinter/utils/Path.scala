package com.github.chencmd.datapacklinter.utils

import typings.node.pathMod as path

opaque type Path = String

object Path {
  extension (p: Path) {
    inline def toString: String = p
  }

  inline def coerce(str: String): Path = str

  inline def extname(p: Path): Option[String] = {
    val dotIdx = p.lastIndexOf(".")
    Option.when(dotIdx != -1 && p.lastIndexOf("/") < dotIdx)(p.substring(dotIdx + 1))
  }

  inline def join(origin: Path, paths: (Path | String)*): Path = path.join((origin +: paths)*)

  inline def dirname(p: Path): Path = path.dirname(p)

  inline def relative(from: Path, to: Path): Path = path.relative(from, to)

  inline def parse(from: String): Path = URI.parse(from).fsPath

  object Converter {
    given Conversion[Path, String] with {
      inline def apply(x: Path): String = x
    }
  }
}

package com.github.chencmd.datapacklinter.utils

import typings.vscodeUri.libUmdUriMod.URI as UmdURI
import typings.vscodeUri.mod.URI as OriginURI

opaque type URI = OriginURI

object URI {
  import scala.language.implicitConversions
  given Conversion[UmdURI, OriginURI] with {
    inline def apply(x: UmdURI): OriginURI = x.asInstanceOf[OriginURI]
  }

  extension (uri: URI) {
    inline def fsPath: Path = Path.coerce(uri.fsPath)

    inline def path: String = uri.path

    inline def vs: OriginURI = uri
  }

  inline def coerce(uri: UmdURI | OriginURI): URI = uri

  inline def parse(uriString: String): URI = OriginURI.parse(uriString)

  inline def file(path: Path): URI = OriginURI.file(path.toString)

  inline def fromPath(path: Path): URI = URI.parse(path.toString)
}

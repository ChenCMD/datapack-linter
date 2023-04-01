package com.github.chencmd.datapacklinter.dls

import com.github.chencmd.datapacklinter.utils.JSObject

import scala.scalajs.js
import scala.scalajs.js.WrappedDictionary

private trait VersionManifestLatestInfo extends js.Object {
  val release: String
  val snapshot: String
}

private trait VersionManifestVersionData extends js.Object {
  val id: String
}

private trait VersionManifest extends js.Object {
  val latest: VersionManifestLatestInfo
  val versions: js.Array[VersionManifestVersionData]
}

private object VersionManifest {
  def attemptToVersionManifest(value: js.Any): Option[VersionManifest] = {
    Option(value)
      .collect { case v if JSObject.isObject(v) => JSObject.toWrappedDictionary[js.Any](v) }
      .filter {
        _.get("latest")
          .collect { case v if JSObject.isObject(v) => JSObject.toWrappedDictionary[js.Any](v) }
          .filter(_.get("release").exists(js.typeOf(_) == "string"))
          .filter(_.get("snapshot").exists(js.typeOf(_) == "string"))
          .isDefined
      }
      .filter {
        _.get("versions")
          .collect { case v if js.Array.isArray(v) => v.asInstanceOf[js.Array[js.Any]] }
          .collect {
            case v if v.forall(JSObject.isObject) => v.map(JSObject.toWrappedDictionary[js.Any])
          }
          .filter(_.forall(_.get("id").exists(js.typeOf(_) == "string")))
          .isDefined
      }
      .map(WrappedDictionary.toJSDictionary(_).asInstanceOf[VersionManifest])
  }
}

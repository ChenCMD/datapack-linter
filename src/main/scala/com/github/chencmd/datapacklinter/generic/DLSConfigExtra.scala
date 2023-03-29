package com.github.chencmd.datapacklinter.generic

import typings.spgodingDatapackLanguageServer.libTypesConfigMod as Config
import typings.spgodingDatapackLanguageServer.libTypesConfigMod.Config as DLSConfig

object DLSConfigExtra {
  extension (dlsConfig: DLSConfig) {
    def isRelIncluded(relativePath: String): Boolean = {
      Config.isRelIncluded(relativePath, dlsConfig)
    }
  }
}

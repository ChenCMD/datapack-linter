package com.github.chencmd.datapacklinter.ciplatform

import com.github.chencmd.datapacklinter.term.RestoreCacheOrSkip

trait CIPlatformCacheRestorationInstr[F[_]] {
  def shouldRestoreCache(): F[RestoreCacheOrSkip]
}
package com.github.chencmd.datapacklinter.ciplatform.local

import com.github.chencmd.datapacklinter.ciplatform.CIPlatformCacheRestorationInstr
import com.github.chencmd.datapacklinter.term.RestoreCacheOrSkip

import cats.effect.Async

object LocalCacheRestoration {
  def createInstr[F[_]: Async](): CIPlatformCacheRestorationInstr[F] = {
    new CIPlatformCacheRestorationInstr[F] {
      override def shouldRestoreCache(): F[RestoreCacheOrSkip] = Async[F].pure(RestoreCacheOrSkip.Restore)
    }
  }
}

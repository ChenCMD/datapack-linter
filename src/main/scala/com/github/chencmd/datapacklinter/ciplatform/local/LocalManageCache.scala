package com.github.chencmd.datapacklinter.ciplatform.local

import com.github.chencmd.datapacklinter.ciplatform.CIPlatformInteractionInstr
import com.github.chencmd.datapacklinter.ciplatform.CIPlatformManageCacheInstr
import com.github.chencmd.datapacklinter.generic.RaiseNec
import com.github.chencmd.datapacklinter.utils.FSAsync

import cats.effect.Async
import cats.implicits.*

object LocalManageCache {
  def createInstr[F[_]: Async]()(using
    ciInteraction: CIPlatformInteractionInstr[F]
  ): CIPlatformManageCacheInstr[F] = {
    new CIPlatformManageCacheInstr[F] {
      override def store(paths: List[String]): F[Unit] = {
        ciInteraction.printDebug("")
      }

      override def restore(paths: List[String])(using
        R: RaiseNec[F, String]
      ): F[Boolean] = for {
        _ <- ciInteraction.printDebug("The key is ignored because a local cache manager is used.")
        existsCacheFile <- paths.forallM(FSAsync.pathAccessible)
      } yield existsCacheFile
    }
  }
}
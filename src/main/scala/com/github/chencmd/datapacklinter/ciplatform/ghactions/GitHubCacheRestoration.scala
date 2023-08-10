package com.github.chencmd.datapacklinter.ciplatform.ghactions

import com.github.chencmd.datapacklinter.ciplatform.CIPlatformCacheRestorationInstr
import com.github.chencmd.datapacklinter.generic.EitherTExtra
import com.github.chencmd.datapacklinter.term.RestoreCacheOrSkip

import cats.effect.Async
import cats.implicits.*

import typings.octokitWebhooksTypes.mod.PushEvent

object GitHubCacheRestoration {
  def createInstr[F[_]: Async](): F[CIPlatformCacheRestorationInstr[F]] = for {
    ghCtx <- Async[F].delay {
      import typings.actionsGithub.mod.context
      context
    }
    instr = new CIPlatformCacheRestorationInstr[F] {
      override def shouldRestoreCache(): F[RestoreCacheOrSkip] = {
        import RestoreCacheOrSkip.*
        val commitMessages: List[String] = {
            Some(ghCtx).filter(_.eventName == "push")
              .map(_.payload.asInstanceOf[PushEvent])
              .map(_.commits.toList.map(_.message.toLowerCase()))
              .orEmpty
        }

        val program = for {
          _ <- EitherTExtra.exitWhenA(commitMessages.exists(_.contains("[regenerate cache]"))) {
            Skip("The cache is not used because the commit message contains '[regenerate cache]'.")
          }
          _ <- EitherTExtra.exitWhenA(ghCtx.eventName == "workflow_dispatch") {
            Skip("The cache is not used because it is executed from the workflow_dispatch event.")
          }
        } yield Restore
        program.merge
      }
    }
  } yield instr
}

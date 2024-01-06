package com.github.chencmd.datapacklinter.ciplatform.ghactions.utils

import com.github.chencmd.datapacklinter.generic.AsyncExtra

import cats.effect.kernel.Async
import cats.implicits.*

import scala.scalajs.js

private trait ActionsGitHub extends js.Object {
  val context: typings.actionsGithub.libContextMod.Context
}

object ActionsGitHub {
  def getContext[F[_]: Async](): F[typings.actionsGithub.libContextMod.Context] = {
    AsyncExtra.fromPromise(js.`import`[ActionsGitHub]("@actions/github")).map(_.context)
  }
}

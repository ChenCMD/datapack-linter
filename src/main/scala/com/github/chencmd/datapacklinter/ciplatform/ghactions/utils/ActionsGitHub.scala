package com.github.chencmd.datapacklinter.ciplatform.ghactions.utils

import com.github.chencmd.datapacklinter.generic.AsyncExtra

import cats.effect.kernel.Async

import scala.scalajs.js

import typings.actionsGithub.libContextMod.Context

object ActionsGitHub {
  def getContext[F[_]: Async](): F[Context] = AsyncExtra.fromPromise {
    js.dynamicImport {
      import typings.actionsGithub.mod.context
      context
    }
  }
}

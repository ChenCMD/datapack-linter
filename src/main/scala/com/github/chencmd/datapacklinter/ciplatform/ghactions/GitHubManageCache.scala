package com.github.chencmd.datapacklinter.ciplatform.ghactions

import com.github.chencmd.datapacklinter.ciplatform.CIPlatformManageCacheInstr
import com.github.chencmd.datapacklinter.generic.AsyncExtra
import com.github.chencmd.datapacklinter.generic.RaiseNec
import com.github.chencmd.datapacklinter.utils.Path

import cats.effect.kernel.Async
import cats.implicits.*

import scala.scalajs.js
import scala.scalajs.js.JSConverters.*

import typings.actionsCache.mod as cache

object GitHubManageCache {
  def createInstr[F[_]: Async](cacheVersion: Int): F[CIPlatformManageCacheInstr[F]] = for {
    ghCtx <- Async[F].delay {
      import typings.actionsGithub.mod.context
      context
    }
    instr = new CIPlatformManageCacheInstr[F] {
      override def store(paths: List[Path]): F[Unit] = AsyncExtra.fromPromise {
        cache.saveCache(
          paths.map(_.toString).toJSArray,
          makeCacheKey(ghCtx.ref, js.Date.now())
        ).`then`(_ => ())
      }

      override def restore(paths: List[Path])(using R: RaiseNec[F, String]): F[Boolean] = {
        def _restore(primary: String, fallbacks: List[String] = List.empty): F[Boolean] = {
          try {
            AsyncExtra
              .fromPromise {
                cache.restoreCache(paths.map(_.toString).toJSArray, primary, fallbacks.toJSArray)
              }
              .map(_.isDefined)
          } catch {
            case (e: js.JavaScriptException) => R.raiseOne(e.getMessage())
            case e                           => R.raiseOne(e.toString())
          }
        }

        _restore(makeCacheKey(ghCtx.ref), List(makeCacheKey()))
      }

      private def makeCacheKey(branch: String = "") = {
        s"datapack-linter-$cacheVersion-$branch"
      }

      private def makeCacheKey(branch: String, uniqueID: Double) = {
        s"datapack-linter-$cacheVersion-$branch-$uniqueID"
      }
    }
  } yield instr
}

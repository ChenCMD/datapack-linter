package com.github.chencmd.datapacklinter.ciplatform.ghactions

import com.github.chencmd.datapacklinter.ciplatform.CIPlatformInteractionInstr
import com.github.chencmd.datapacklinter.utils.FSAsync
import com.github.chencmd.datapacklinter.utils.JSObject
import com.github.chencmd.datapacklinter.utils.Path

import cats.effect.Async
import cats.effect.Resource
import cats.implicits.*

import scala.scalajs.js
import scala.scalajs.js.JSON

import typings.actionsCore.mod as core

object GitHubInteraction {
  private val matcher = JSObject(
    "problemMatcher" -> js.Array(
      JSObject(
        "owner"   -> "datapack-linter",
        "pattern" -> js.Array(
          JSObject(
            "regexp"   -> """^.*✗[^(]+\((.+)\)$""",
            "file"     -> 1
          ),
          JSObject(
            "regexp"   -> """^\s+(\d+):(\d+)\s+(Error|Warning)\s+(.*)$""",
            "line"     -> 1,
            "column"   -> 2,
            "severity" -> 3,
            "message"  -> 4,
            "loop"     -> true
          )
        )
      )
    )
  )

  def createInstr[F[_]: Async](dir: Path): Resource[F, CIPlatformInteractionInstr[F]] = {
    val instr   = new CIPlatformInteractionInstr[F] {
      override def printError(msg: String): F[Unit] = {
        // Error detection is more versatile with matcher, so here we use info for output.
        Async[F].delay(core.info(msg))
      }

      override def printWarning(msg: String): F[Unit] = {
        // Warning detection is more versatile with matcher, so here we use info for output.
        Async[F].delay(core.info(msg))
      }

      override def printInfo(msg: String): F[Unit] = {
        Async[F].delay(core.info(msg))
      }

      override def printDebug(msg: String): F[Unit] = {
        Async[F].delay(core.debug(msg))
      }

      override def startGroup(header: String): F[Unit] = {
        Async[F].delay(core.startGroup(header))
      }

      override def endGroup(): F[Unit] = {
        Async[F].delay(core.endGroup())
      }

      override def setOutput(key: String, value: Any): F[Unit] = {
        Async[F].delay(core.setOutput(key, value))
      }
    }
    val program = for {
      _ <- FSAsync.writeFile(Path.join(dir, "matcher.json"), JSON.stringify(matcher))
      _ <- instr.printInfo("::add-matcher::matcher.json")
    } yield instr

    Resource.make(program)(_ => FSAsync.removeFile(Path.join(dir, "matcher.json")))
  }
}

package com.github.chencmd.datapacklinter.ciplatform.ghactions

import com.github.chencmd.datapacklinter.ciplatform.CIPlatformInteractionInstr
import com.github.chencmd.datapacklinter.utils.FSAsync

import cats.data.EitherT
import cats.effect.{Async, Resource}
import cats.implicits.*

import typings.node.pathMod as path
import typings.actionsCore.mod as core

object GitHubInteraction {
  private val matcherJson = """
{
  "problemMatcher": [{
    "owner": "datapack-linter",
    "pattern": [
      {
        "regexp": "^.*✗[^(]+\\((.+)\\)$",
        "file": 1
      },
      {
        "regexp": "^\\s+(\\d+):(\\d+)\\s+(Error|Warning)\\s+(.*)$",
        "line": 1,
        "column": 2,
        "severity": 3,
        "message": 4,
        "loop": true
      }
    ]
  }]
}
"""

  def createInstr[F[_]: Async](
    dir: String
  ): Resource[[A] =>> EitherT[F, String, A], CIPlatformInteractionInstr[F]] = {
    val program = for {
      instr <- Async[F].delay {
        new CIPlatformInteractionInstr[F] {
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
      }
      _     <- FSAsync.writeFile(path.join(dir, "matcher.json"), matcherJson)
      _     <- instr.printInfo(":add-matcher::matcher.json")
    } yield instr

    Resource
      .make(program)(_ => FSAsync.removeFile(path.join(dir, "matcher.json")))
      .mapK(EitherT.liftK)
  }
}

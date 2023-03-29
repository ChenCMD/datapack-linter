package com.github.chencmd.datapacklinter.ciplatform.ghactions

import com.github.chencmd.datapacklinter.ciplatform.CIPlatformInteraction
import com.github.chencmd.datapacklinter.utils.FSAsync

import cats.data.EitherT
import cats.effect.Async
import cats.implicits.*

import scalajs.js

import typings.node.pathMod as path
import typings.actionsCore.mod as core

class GitHubInteraction[F[_]: Async] extends CIPlatformInteraction[F] {
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

  override def setOutput(key: String, value: js.Any): F[Unit] = {
    Async[F].delay(core.setOutput(key, value))
  }

  override def initialize(dir: String): EitherT[F, String, Unit] = {
    val matcher = """
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
    val program = for {
      _ <- FSAsync.writeFile(path.join(dir, "matcher.json"), matcher)
      _ <- printInfo(":add-matcher::matcher.json")
    } yield ()
    EitherT.liftF(program)
  }

  override def finalize(dir: String): EitherT[F, String, Unit] = {
    FSAsync.removeFile(path.join(dir, "matcher.json"))
  }
}

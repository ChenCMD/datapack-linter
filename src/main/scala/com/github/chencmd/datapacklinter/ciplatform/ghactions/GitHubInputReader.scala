package com.github.chencmd.datapacklinter.ciplatform.ghactions

import com.github.chencmd.datapacklinter.ciplatform.KeyedConfigReader

import cats.data.{OptionT, EitherT}
import cats.effect.Async
import cats.implicits.*

import typings.actionsCore.mod as core
import typings.actionsCore.mod.InputOptions

class GitHubInputReader[F[_]: Async] extends KeyedConfigReader[F] {
  override protected def readKey[A](key: String, required: Boolean, default: => Option[A])(using
    valueType: KeyedConfigReader.ConfigValueType[A]
  ): EitherT[F, String, A] = {
    EitherT(Async[F].delay {
      try {
        Some(core.getInput(key, InputOptions().setRequired(required)))
          .filter(_.nonEmpty)
          .traverse(v => valueType.tryCast(key, v))
          .map(_.getOrElse(default.get))
      } catch e => Left(e.getMessage())
    })
  }
}

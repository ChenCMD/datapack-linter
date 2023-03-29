package com.github.chencmd.datapacklinter.ciplatform.local

import com.github.chencmd.datapacklinter.ciplatform.KeyedConfigReader
import com.github.chencmd.datapacklinter.utils.{FSAsync, JSObject}

import cats.data.EitherT
import cats.effect.{Async, Ref}
import cats.implicits.*

import scala.util.chaining.*

import scalajs.js
import js.WrappedDictionary

import typings.node.processMod as process
import typings.node.pathMod as path

class LocalInputReader[F[_]: Async] extends KeyedConfigReader[F] {
  private val config: Ref[F, Option[WrappedDictionary[String]]] = Ref.unsafe(None)

  override protected def readKey[A](key: String, required: Boolean, default: => Option[A])(using
    valueType: KeyedConfigReader.ConfigValueType[A]
  ): EitherT[F, String, A] = {
    for {
      cfg <- EitherT.fromOptionF(config.get, "linter-config.json is not loaded.")

      value <- EitherT.fromEither {
        Right(cfg.get(key))
          .filterOrElse(_.isEmpty && required, s"Input required and not supplied: $key")
          .flatMap(_.traverse(v => valueType.tryCast(key, v)))
      }
    } yield value.getOrElse(default.get)
  }

  override def initialize(dir: String): EitherT[F, String, Unit] = {
    val configPath = path.join(dir, "linter-config.json")
    for {
      existsConfig <- FSAsync.pathAccessible[[A] =>> EitherT[F, String, A]](configPath)
      cfg          <- {
        if (existsConfig) {
          EitherT.right(FSAsync.readFile(configPath))
        } else {
          EitherT
            .left(FSAsync.writeFile(configPath, "{}"))
            .leftMap(_ => "linter-config.json does not exist")
        }
      }
      _ <- EitherT.liftF(config.set(Some(JSObject.toWrappedDictionary(js.JSON.parse(cfg)))))
    } yield ()
  }
}

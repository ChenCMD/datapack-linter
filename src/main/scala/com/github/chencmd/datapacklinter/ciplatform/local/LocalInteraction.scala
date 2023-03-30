package com.github.chencmd.datapacklinter.ciplatform.local

import com.github.chencmd.datapacklinter.ciplatform.CIPlatformInteraction

import cats.Monad
import cats.data.EitherT
import cats.effect.{Async, Ref}
import cats.implicits.*

import scalajs.js

class LocalInteraction[F[_]: Async] extends CIPlatformInteraction[F] {
  override def printError(msg: String): F[Unit] = {
    Async[F].delay(println(s"Error: $msg"))
  }

  override def printWarning(msg: String): F[Unit] = {
    Async[F].delay(println(s"Warning: $msg"))
  }

  override def printInfo(msg: String): F[Unit] = {
    Async[F].delay(println(msg))
  }

  override def printDebug(msg: String): F[Unit] = {
    Async[F].delay(println(s"::debug::$msg"))
  }

  override def startGroup(header: String): F[Unit] = {
    Async[F].delay(println(s"::group::$header"))
  }

  override def endGroup(): F[Unit] = {
    Async[F].delay(println("::endgroup::"))
  }

  private val outputs: Ref[F, Map[String, String]]            = Ref.unsafe(Map.empty)
  override def setOutput(key: String, value: Any): F[Unit] = {
    val strValue = value match {
      case v if v == js.undefined        => ""
      case v if v == null                => ""
      case v if js.typeOf(v) == "string" => v.asInstanceOf[String]
      case v                             => js.JSON.stringify(v.asInstanceOf[js.Any])
    }
    outputs.update(_ + (key -> strValue))
  }

  override def finalize(dir: String): EitherT[F, String, Unit] = {
    val program = for {
      outputs   <- outputs.get
      maxKeyLen <- Monad[F].pure(outputs.map(_._1.length()).foldLeft(0)(Math.max))
      _         <- outputs.toList.traverse {
        case (k, v) => Async[F].delay(println(s"%${maxKeyLen}s = %s".format(k, v)))
      }
    } yield ()
    EitherT.liftF(program)
  }
}

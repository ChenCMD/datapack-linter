package com.github.chencmd.datapacklinter.ciplatform.local

import com.github.chencmd.datapacklinter.ciplatform.CIPlatformInteractionInstr

import cats.effect.Async
import cats.effect.Ref
import cats.effect.Resource
import cats.implicits.*

import scala.scalajs.js
import scala.scalajs.js.JSON

object LocalInteraction {
  def createInstr[F[_]: Async](): Resource[F, CIPlatformInteractionInstr[F]] = {
    val outputs: Ref[F, Map[String, String]] = Ref.unsafe(Map.empty)

    val instr = new CIPlatformInteractionInstr[F] {
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

      override def setOutput(key: String, value: Any): F[Unit] = {
        val strValue = value match {
          case v if v == js.undefined        => ""
          case v if v == null                => ""
          case v if js.typeOf(v) == "string" => v.asInstanceOf[String]
          case v                             => JSON.stringify(v.asInstanceOf[js.Any])
        }
        outputs.update(_ + (key -> strValue))
      }
    }

    Resource.make(instr.pure[F]) { _ =>
      for {
        outputs <- outputs.get
        maxKeyLen = outputs.map(_._1.length()).foldLeft(0)(Math.max)
        _ <- outputs.toList.traverse_ {
          case (k, v) => Async[F].delay(println(s"%${maxKeyLen}s = %s".format(k, v)))
        }
      } yield ()
    }
  }
}

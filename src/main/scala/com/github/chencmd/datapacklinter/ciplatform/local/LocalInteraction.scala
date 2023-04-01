package com.github.chencmd.datapacklinter.ciplatform.local

import com.github.chencmd.datapacklinter.ciplatform.CIPlatformInteractionInstr

import cats.Monad
import cats.data.EitherT
import cats.effect.Async
import cats.effect.Ref
import cats.effect.kernel.Resource
import cats.implicits.*

import scala.scalajs.js

object LocalInteraction {
  def createInstr[F[_]: Async](): Resource[[A] =>> EitherT[F, String, A], CIPlatformInteractionInstr[F]] = {
    val outputs: Ref[F, Map[String, String]] = Ref.unsafe(Map.empty)

    val program = Async[F].delay {
      new CIPlatformInteractionInstr[F] {
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
            case v                             => js.JSON.stringify(v.asInstanceOf[js.Any])
          }
          outputs.update(_ + (key -> strValue))
        }
      }
    }

    Resource.make(program) { _ =>
      for {
        outputs   <- outputs.get
        maxKeyLen <- Monad[F].pure(outputs.map(_._1.length()).foldLeft(0)(Math.max))
        _         <- outputs.toList.traverse {
          case (k, v) => Async[F].delay(println(s"%${maxKeyLen}s = %s".format(k, v)))
        }
      } yield ()
    }.mapK(EitherT.liftK)
  }
}

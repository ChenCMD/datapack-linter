package com.github.chencmd.datapacklinter.ciplatform

import cats.Monad
import cats.data.EitherT
import cats.effect.Sync
import cats.implicits.*
import cats.mtl.Raise

trait CIPlatformReadKeyedConfigInstr[F[_]: Sync] {
  import CIPlatformReadKeyedConfigInstr.ConfigValueType

  final def readKeyOrElse[A](key: String, default: => A)(using
    R: Raise[F, String],
    ciInteraction: CIPlatformInteractionInstr[F],
    valueType: ConfigValueType[A]
  ): F[A] = readKey(key, false, Some(default))

  final def readKey[A](key: String)(using
    R: Raise[F, String],
    ciInteraction: CIPlatformInteractionInstr[F],
    valueType: ConfigValueType[A]
  ): F[A] = readKey(key, true, None)

  private def readKey[A](key: String, required: Boolean, default: => Option[A])(using
    R: Raise[F, String],
    ciInteraction: CIPlatformInteractionInstr[F],
    valueType: ConfigValueType[A]
  ): F[A] = {
    for {
      v <- read(key)
      _ <- ciInteraction.printDebug(s"read key: $key, result: $v")
      _ <- Monad[F].whenA(v.isEmpty && required) {
        R.raise(s"Input required and not supplied: $key")
      }
      b <- v.traverse(valueType.tryCast(key, _)) match {
        case Right(value) => Monad[F].pure(value)
        case Left(value)  => R.raise(value)
      }
    } yield b.getOrElse(default.get)
  }

  protected def read(key: String): F[Option[String]]
}

object CIPlatformReadKeyedConfigInstr {
  trait ConfigValueType[A] {
    def tryCast(key: String, value: String): Either[String, A]

    protected final def typeMismatchError(key: String, expectedType: String): String = {
      s"type $expectedType was requested but could not be interpreted as that type: $key"
    }
  }

  given ConfigValueType[String] with       {
    def tryCast(key: String, value: String): Either[String, String] = Right(value)
  }
  given ConfigValueType[Int] with          {
    def tryCast(key: String, value: String): Either[String, Int] =
      value.toIntOption.toRight(typeMismatchError(key, "Int"))
  }
  given ConfigValueType[Double] with       {
    def tryCast(key: String, value: String): Either[String, Double] =
      value.toDoubleOption.toRight(typeMismatchError(key, "Double"))
  }
  given ConfigValueType[Boolean] with      {
    val trueValue  = List("true", "True", "TRUE")
    val falseValue = List("false", "False", "FALSE")
    def tryCast(key: String, value: String): Either[String, Boolean] = {
      if (trueValue.contains(value)) return Right(true)
      if (falseValue.contains(value)) return Right(false)
      Left(typeMismatchError(key, "Boolean"))
    }
  }
  given ConfigValueType[List[String]] with {
    def tryCast(key: String, value: String): Either[String, List[String]] =
      Right(value.split("\n").toList)
  }
}

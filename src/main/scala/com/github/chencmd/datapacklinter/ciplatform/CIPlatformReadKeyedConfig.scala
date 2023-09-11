package com.github.chencmd.datapacklinter.ciplatform

import cats.data.EitherT
import cats.data.ValidatedNec
import cats.effect.Sync
import cats.implicits.*

trait CIPlatformReadKeyedConfigInstr[F[_]: Sync] {
  import CIPlatformReadKeyedConfigInstr.ConfigValueType

  final def readKeyOrElse[A](key: String, default: => A)(using
    ciInteraction: CIPlatformInteractionInstr[F],
    valueType: ConfigValueType[A]
  ): F[ValidatedNec[String, A]] = readKey(key, false, Some(default))

  final def readKey[A](key: String)(using
    ciInteraction: CIPlatformInteractionInstr[F],
    valueType: ConfigValueType[A]
  ): F[ValidatedNec[String, A]] = readKey(key, true, None)

  private def readKey[A](key: String, required: Boolean, default: => Option[A])(using
    ciInteraction: CIPlatformInteractionInstr[F],
    valueType: ConfigValueType[A]
  ): F[ValidatedNec[String, A]] = {
    val program = for {
      v <- EitherT.liftF(read(key))
      _ <- EitherT.liftF(ciInteraction.printDebug(s"read key: $key, result: $v"))
      _ <- EitherT.cond(v.isDefined || !required, (), s"Input required and not supplied: $key")
      b <- EitherT.fromEither(v.traverse(valueType.tryCast(key, _)))
    } yield b.getOrElse(default.get)
    program.toValidatedNec
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
    val trueValue                                                    = List("true", "True", "TRUE")
    val falseValue                                                   = List("false", "False", "FALSE")
    def tryCast(key: String, value: String): Either[String, Boolean] = {
      if (trueValue.contains(value)) return Right(true)
      if (falseValue.contains(value)) return Right(false)
      Left(typeMismatchError(key, "Boolean"))
    }
  }

  given [A](using A: ConfigValueType[A]): ConfigValueType[List[A]] with   {
    def tryCast(key: String, value: String): Either[String, List[A]] =
      value.split("\n").toList.traverse(A.tryCast(key, _))
  }
}

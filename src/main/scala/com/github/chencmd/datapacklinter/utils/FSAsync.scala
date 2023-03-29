package com.github.chencmd.datapacklinter.utils

import com.github.chencmd.datapacklinter.generic.AsyncExtra
import com.github.chencmd.datapacklinter.generic.EitherTExtra
import com.github.chencmd.datapacklinter.generic.ApplicativeExtra.*
import com.github.chencmd.datapacklinter.generic.MonoidExtra.*

import cats.Monad
import cats.data.EitherT
import cats.effect.Async
import cats.implicits.*
import cats.effect.implicits.*

import typings.node.pathMod as path
import typings.node.fsMod.promises as fsp
import typings.node.BufferEncoding

object FSAsync {
  def pathAccessible[F[_]: Async](path: String): F[Boolean] = {
    AsyncExtra
      .fromPromise(fsp.access(path))
      .map(_ => true)
      .handleError(_ => false)
  }

  def writeFile[F[_]: Async](targetPath: String, contents: String): F[Unit] = {
    AsyncExtra.fromPromise(fsp.writeFile(targetPath, contents))
  }

  def removeFile[F[_]: Async](targetPath: String): F[Unit] = {
    AsyncExtra.fromPromise(fsp.unlink(targetPath))
  }

  def readFile[F[_]: Async](targetPath: String): F[String] = {
    AsyncExtra.fromPromise(fsp.readFile(targetPath, BufferEncoding.utf8))
  }

  def readDir[F[_]: Async](dir: String): F[List[String]] = {
    AsyncExtra
      .fromPromise(fsp.readdir(dir))
      .map(_.toList.map(path.join(dir, _)))
  }

  def isDirectory[F[_]: Async](targetPath: String): F[Boolean] = {
    AsyncExtra.fromPromise(fsp.stat(targetPath)).map(_.isDirectory())
  }

  case class Path(abs: String, rel: String)

  def foreachFileRec[F[_]: Async, A](
    baseDir: String,
    targetDir: String,
    pathFilter: Path => Boolean,
    maxDepth: Int = Int.MaxValue
  )(
    f: Path => F[A]
  ): F[List[A]] = {
    def walk(dir: String, currentDepth: Int): F[List[A]] = {
      for {
        files  <- readDir(dir)
        result <- files
          .map(p => Path(p, path.relative(baseDir, p)))
          .parFlatTraverse { childPath =>
            val program = for {
              isDir    <- EitherT.liftF(isDirectory(childPath.abs))
              _        <- EitherTExtra.exitWhenA(isDir) {
                if (currentDepth < maxDepth) {
                  walk(childPath.abs, currentDepth + 1)
                } else {
                  Monad[F].pure(List.empty)
                }
              }
              fApplied <- EitherT.liftF(f(childPath))
            } yield List(fApplied)
            program.merge
              .whenOrPureNoneA(pathFilter(childPath))
              .map(_.getOrElse(List.empty))
          }
      } yield result
    }
    walk(targetDir, 0)
  }

  def foreachDirRec[F[_]: Async, A](
    baseDir: String,
    targetDir: String,
    pathFilter: Path => Boolean,
    maxDepth: Int = Int.MaxValue
  )(
    f: Path => F[A]
  ): F[List[A]] = {
    def walk(dir: String, currentDepth: Int): F[List[A]] = {
      for {
        files  <- readDir(dir)
        dirs   <- files
          .map(p => Path(p, path.relative(dir, p)))
          .filterA(p => isDirectory(p.abs))
        result <- dirs.parFlatTraverse { childPath =>
          val program = for {
            childDir <- {
              if (currentDepth < maxDepth) {
                walk(childPath.abs, currentDepth + 1)
              } else {
                Monad[F].pure(List.empty)
              }
            }
            fApplied <- f(childPath)
          } yield List(fApplied) ::: childDir
          program
            .whenOrPureNoneA(pathFilter(childPath))
            .map(_.getOrElse(List.empty))
        }
      } yield result
    }
    walk(targetDir, 0)
  }
}

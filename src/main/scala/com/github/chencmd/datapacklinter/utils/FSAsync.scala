package com.github.chencmd.datapacklinter.utils

import com.github.chencmd.datapacklinter.generic.AsyncExtra
import com.github.chencmd.datapacklinter.generic.ApplicativeExtra.*
import com.github.chencmd.datapacklinter.generic.EitherTExtra.*

import cats.data.EitherT
import cats.syntax.all.*
import cats.effect.kernel.Async
import cats.effect.implicits.parallelForGenSpawn

import typings.node.pathMod as path
import typings.node.fsMod.promises as fsp
import typings.node.BufferEncoding

object FSAsync {
  case class Path(abs: String, rel: String)

  def pathAccessible[F[_]: Async](path: String): F[Boolean] = {
    AsyncExtra.fromPromise(fsp.access(path))
      .map(_ => true)
      .handleError(_ => false)
  }

  def readFile[F[_]: Async](targetPath: String): F[String] = {
    AsyncExtra.fromPromise(fsp.readFile(targetPath, BufferEncoding.utf8))
  }

  def readDir[F[_]: Async](dir: String, maxDepth: Int = Int.MaxValue): F[List[Path]] = {
    AsyncExtra.fromPromise(fsp.readdir(dir))
      .map(_.toList.map { name =>
        Path(
          path.join(dir, name),
          path.relative(dir, name)
        )
      })
  }

  def isDirectory[F[_]: Async](targetPath: String): F[Boolean] = {
    AsyncExtra.fromPromise(fsp.stat(targetPath)).map(_.isDirectory())
  }

  def foreachDirRec[F[_]: Async,A](dir: String, pathFilter: Path => Boolean, maxDepth: Int = Int.MaxValue)(
    f: Path => F[A]
  ): F[List[A]] = {
    for {
      files  <- readDir(dir)
      result <- files.parFlatTraverse { childPath =>
        val program = for {
          isDir    <- EitherT.liftF(isDirectory(childPath.abs))
          _        <- foreachDirRec(childPath.abs, pathFilter, maxDepth)(f).exitWhenA(isDir)
          fApplied <- EitherT.liftF(f(childPath))
        } yield List(fApplied)
        program.merge
          .whenOrPureNoneA(pathFilter(childPath))
          .map(_.getOrElse(List.empty))
      }
    } yield result
  }
}

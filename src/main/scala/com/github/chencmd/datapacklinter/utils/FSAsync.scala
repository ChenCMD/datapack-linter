package com.github.chencmd.datapacklinter.utils

import com.github.chencmd.datapacklinter.generic.ApplicativeExtra
import com.github.chencmd.datapacklinter.generic.AsyncExtra
import com.github.chencmd.datapacklinter.generic.EitherTExtra

import cats.data.EitherT
import cats.effect.Async
import cats.implicits.*

import typings.node.bufferMod.global.BufferEncoding
import typings.node.fsMod.promises as fsp

object FSAsync {
  import scala.language.implicitConversions
  import Path.Converter.given

  def pathAccessible[F[_]: Async](path: Path): F[Boolean] = {
    AsyncExtra
      .fromPromise(fsp.access(path))
      .map(_ => true)
      .handleError(_ => false)
  }

  def writeFile[F[_]: Async](targetPath: Path, contents: String, encoding: BufferEncoding = BufferEncoding.utf8): F[Unit] = {
    AsyncExtra.fromPromise(fsp.writeFile(targetPath, contents, encoding))
  }

  def removeFile[F[_]: Async](targetPath: Path): F[Unit] = {
    AsyncExtra.fromPromise(fsp.unlink(targetPath))
  }

  def readFile[F[_]: Async](targetPath: Path): F[String] = {
    AsyncExtra.fromPromise(fsp.readFile(targetPath, BufferEncoding.utf8))
  }

  def readDir[F[_]: Async](dir: Path): F[List[Path]] = {
    AsyncExtra
      .fromPromise(fsp.readdir(dir))
      .map(_.toList.map(Path.join(dir, _)))
  }

  def readFileOpt[F[_]: Async](targetPath: Path): F[Option[String]] = for {
    existsFile <- pathAccessible(targetPath)
    contents   <- ApplicativeExtra.whenAOrPureNone(existsFile)(readFile(targetPath))
  } yield contents

  def readDirOpt[F[_]: Async](targetPath: Path): F[Option[List[Path]]] = for {
    existsFile <- pathAccessible(targetPath)
    contents   <- ApplicativeExtra.whenAOrPureNone(existsFile)(readDir(targetPath))
  } yield contents

  def isDirectory[F[_]: Async](targetPath: Path): F[Boolean] = {
    AsyncExtra.fromPromise(fsp.stat(targetPath)).map(_.isDirectory())
  }

  case class PathGroup(abs: Path, rel: String)

  def foreachFileRec[F[_]: Async, A](
    baseDir: Path,
    targetDir: Path,
    pathFilter: PathGroup => Boolean,
    maxDepth: Int = Int.MaxValue
  )(
    f: PathGroup => F[A]
  ): F[List[A]] = {
    def walk(dir: Path, currentDepth: Int): F[List[A]] = for {
      files  <- readDir(dir)
      result <- files
        .map(p => PathGroup(p, Path.relative(baseDir, p)))
        .flatTraverse { childPath =>
          val program = for {
            isDir    <- EitherT.liftF(isDirectory(childPath.abs))
            _        <- EitherTExtra.exitWhenF(isDir) {
              if (currentDepth < maxDepth) {
                walk(childPath.abs, currentDepth + 1)
              } else {
                List.empty.pure[F]
              }
            }
            fApplied <- EitherT.liftF(f(childPath))
          } yield List(fApplied)
          ApplicativeExtra
            .whenAOrPureNone(pathFilter(childPath))(program.merge)
            .map(_.orEmpty)
        }
    } yield result
    walk(targetDir, 0)
  }

  def foreachDirRec[F[_]: Async, A](
    baseDir: Path,
    targetDir: Path,
    pathFilter: PathGroup => Boolean,
    maxDepth: Int = Int.MaxValue
  )(
    f: PathGroup => F[A]
  ): F[List[A]] = {
    def walk(dir: Path, currentDepth: Int): F[List[A]] = for {
      files  <- readDir(dir)
      dirs   <- files
        .map(p => PathGroup(p, Path.relative(dir, p)))
        .filterA(p => isDirectory(p.abs))
      result <- dirs.flatTraverse { childPath =>
        val program = for {
          childDir <- {
            if (currentDepth < maxDepth) {
              walk(childPath.abs, currentDepth + 1)
            } else {
              List.empty.pure[F]
            }
          }
          fApplied <- f(childPath)
        } yield List(fApplied) ::: childDir
        ApplicativeExtra
          .whenAOrPureNone(pathFilter(childPath))(program)
          .map(_.orEmpty)
      }
    } yield result
    walk(targetDir, 0)
  }
}

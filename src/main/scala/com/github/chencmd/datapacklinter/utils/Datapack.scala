package com.github.chencmd.datapacklinter.utils

import com.github.chencmd.datapacklinter.utils.Path
import com.github.chencmd.datapacklinter.utils.URI

import cats.effect.Async
import cats.implicits.*

import scala.util.chaining.*

import scala.scalajs.js.JSConverters.*

import typings.spgodingDatapackLanguageServer.libServicesCommonMod as DLSCommon

object Datapack {
  def findDatapackRoots[F[_]: Async](dir: Path, maxDepth: Int): F[List[URI]] = for {
    rootCandidatePaths <- FSAsync.foreachDirRec(dir, dir, _ => true, maxDepth)(_.abs.pure[F]).map(x => dir :: x)
    rootPaths          <- rootCandidatePaths.filterA { p =>
      for {
        a <- FSAsync.pathAccessible(Path.join(p, "data"))
        b <- FSAsync.pathAccessible(Path.join(p, "pack.mcmeta"))
      } yield a && b
    }
    rootUris = rootPaths
      .map(URI.file(_).toString)
      .map(DLSCommon.getRootUri(_))
      .map(URI.coerce(_))
  } yield rootUris

  def getRoot(uri: URI, roots: List[URI]): Option[Path] = {
    val res = Option(DLSCommon.getRelAndRootIndex(uri.vs, roots.map(_.vs).toJSArray))
    res
      .map(_.rel)
      .orElse {
        uri.path
          .split("/")
          .reverse
          .dropWhile(seg => seg == "asset" && seg == "data")
          .reverse
          .mkString("/")
          .pipe(Option.apply)
          .filter(_.nonEmpty)
      }
      .map(Path.coerce(_))
  }
}

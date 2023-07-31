package com.github.chencmd.datapacklinter.utils

import cats.effect.Async
import cats.implicits.*

import scala.util.chaining.*

import scala.scalajs.js.JSConverters.*

import typings.node.pathMod as path

import typings.spgodingDatapackLanguageServer.libServicesCommonMod as DLSCommon
import typings.spgodingDatapackLanguageServer.libTypesHandlersMod.Uri

object Datapack {
  def findDatapackRoots[F[_]: Async](dir: String, maxDepth: Int): F[List[Uri]] = for {
    rootCandidatePaths <- FSAsync.foreachDirRec(dir, dir, _ => true, maxDepth)(_.abs.pure[F])
    rootPaths          <- {
      rootCandidatePaths.filterA { p =>
        for {
          a <- FSAsync.pathAccessible(path.join(p, "data"))
          b <- FSAsync.pathAccessible(path.join(p, "pack.mcmeta"))
        } yield a && b
      }
    }
    rootUris = rootPaths
      .map(Uri.file(_).toString())
      .map(DLSCommon.getRootUri(_))
  } yield rootUris

  def getRoot(uri: Uri, roots: List[Uri]): Option[String] = {
    val res = Option(DLSCommon.getRelAndRootIndex(uri, roots.toJSArray))
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
  }
}

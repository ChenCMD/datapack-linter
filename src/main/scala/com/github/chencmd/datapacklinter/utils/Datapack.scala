package com.github.chencmd.datapacklinter.utils

import com.github.chencmd.datapacklinter.utils.FSAsync

import cats.Monad
import cats.effect.Async
import cats.implicits.*

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
    rootUris           <- Monad[F].pure {
      rootPaths
        .map(Uri.file(_).toString())
        .map(DLSCommon.getRootUri(_))
    }
  } yield rootUris
}

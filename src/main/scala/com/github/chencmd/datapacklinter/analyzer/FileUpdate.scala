package com.github.chencmd.datapacklinter.analyzer

import com.github.chencmd.datapacklinter.dls.DLSHelper
import com.github.chencmd.datapacklinter.facade.DatapackLanguageService
import com.github.chencmd.datapacklinter.generic.MapExtra.*
import com.github.chencmd.datapacklinter.term.FileChecksums
import com.github.chencmd.datapacklinter.utils.Path

import cats.Align
import cats.data.Ior
import cats.implicits.*
import com.github.chencmd.datapacklinter.term.ResourcePath

enum FileUpdate private () {
  case Created
  case ContentUpdated
  case RefsUpdated
  case NotChanged
  case Deleted
}

object FileUpdate {
  def apply(checksumConversion: Ior[String, String]): FileUpdate = {
    checksumConversion match {
      case Ior.Left(_)              => Deleted
      case Ior.Right(_)             => Created
      case Ior.Both(a, b) if a != b => ContentUpdated
      case Ior.Both(_, _)           => NotChanged
    }
  }

  def diff(dls: DatapackLanguageService)(
    prevChecksums: FileChecksums,
    nextCheckSums: FileChecksums,
    refs: Map[ResourcePath, List[Path]]
  ): Map[Path, FileUpdate] = {
    val fileUpdates         = Align[Map[Path, _]].alignWith(prevChecksums, nextCheckSums)(FileUpdate.apply)
    val overrideRefsUpdated = fileUpdates.flatMap {
      case (k, Created | ContentUpdated | Deleted) => DLSHelper
          .getResourcePath(dls)(k)
          .flatMap(refs.getOrEmpty)
          .filter(fileUpdates.get(_).contains(NotChanged))
          .map(_ -> RefsUpdated)
      case _                                       => List.empty
    }

    fileUpdates ++ overrideRefsUpdated
  }
}

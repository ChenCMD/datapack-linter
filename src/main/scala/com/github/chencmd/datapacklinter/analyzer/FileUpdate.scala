package com.github.chencmd.datapacklinter.analyzer

import com.github.chencmd.datapacklinter.generic.MapExtra.*

import cats.Align
import cats.data.Ior
import cats.implicits.*

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

  def diff(
    prevChecksums: Map[String, String],
    nextCheckSums: Map[String, String],
    refs: Map[String, List[String]]
  ): Map[String, FileUpdate] = {
    val fileState = Align[Map[String, _]].alignWith(prevChecksums, nextCheckSums)(FileUpdate.apply)
    val overrideFileStateForRefs = fileState.flatMap {
      case (k, ContentUpdated | Deleted) =>
        refs.getOrEmpty(k).filter(fileState.get(_).contains(NotChanged)).map(_ -> RefsUpdated)
      case _                      => List.empty
    }

    fileState ++ overrideFileStateForRefs
  }
}

package com.github.chencmd.datapacklinter.analyzer

import com.github.chencmd.datapacklinter.generic.MapExtra.*

import cats.Align
import cats.data.Ior
import cats.implicits.*

enum FileState private () {
  case Created
  case Updated
  case RefsUpdated
  case NoChanged
  case Deleted
}

object FileState {
  def apply(checksumConversion: Ior[String, String]): FileState = {
    checksumConversion match {
      case Ior.Left(_)              => Deleted
      case Ior.Right(_)             => Created
      case Ior.Both(a, b) if a != b => Updated
      case Ior.Both(_, _)           => NoChanged
    }
  }

  def diff(
    prevChecksums: Map[String, String],
    nextCheckSums: Map[String, String],
    refs: Map[String, List[String]]
  ): Map[String, FileState] = {
    val fileState = Align[Map[String, _]].alignWith(prevChecksums, nextCheckSums)(FileState.apply)
    val overrideFileStateForRefs = fileState.flatMap {
      case (k, Updated | Deleted) =>
        refs.getOrEmpty(k).filter(fileState.get(_).contains(NoChanged)).map(_ -> RefsUpdated)
      case _                      => List.empty
    }

    fileState ++ overrideFileStateForRefs
  }
}

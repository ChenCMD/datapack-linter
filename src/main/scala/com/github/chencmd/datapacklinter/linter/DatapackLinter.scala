package com.github.chencmd.datapacklinter.linter

import com.github.chencmd.datapacklinter.analyzer.AnalysisResult
import com.github.chencmd.datapacklinter.analyzer.ErrorSeverity
import com.github.chencmd.datapacklinter.analyzer.FileUpdate
import com.github.chencmd.datapacklinter.ciplatform.CIPlatformInteractionInstr
import com.github.chencmd.datapacklinter.generic.MapExtra.*
import com.github.chencmd.datapacklinter.term.FileUpdates
import com.github.chencmd.datapacklinter.utils.AsciiColors

import cats.Monad
import cats.effect.Async
import cats.implicits.*

object DatapackLinter {
  def printFileUpdatesLog[F[_]: Async](
    fileUpdates: FileUpdates
  )(using ciInteraction: CIPlatformInteractionInstr[F]): F[Unit] = {
    def getHumanReadableUpdateState(updateKind: FileUpdate): String = {
      updateKind match {
        case FileUpdate.Created        => "add"
        case FileUpdate.ContentUpdated => "change"
        case FileUpdate.Deleted        => "delete"
        case FileUpdate.RefsUpdated    => "ref change"
        case FileUpdate.NotChanged     => "no change"
      }
    }

    val updatedPaths = fileUpdates.preImages
    List(FileUpdate.Created, FileUpdate.ContentUpdated, FileUpdate.RefsUpdated, FileUpdate.Deleted).traverse_ {
      updateKind =>
        updatedPaths.getOrEmpty(updateKind).traverse_ { path =>
          ciInteraction.printDebug(s"file ${getHumanReadableUpdateState(updateKind)} detected: $path")
        }
    }
  }

  def printResult[F[_]: Async](res: AnalysisResult, muteSuccessResult: Boolean)(using
    ciInteraction: CIPlatformInteractionInstr[F]
  ): F[Unit] = {
    val title = s"${res.resourcePath} (${res.dpFilePath})"

    if (!res.errors.exists(_.severity.severerThanOrEqualTo(ErrorSeverity.WARNING))) {
      Monad[F].unlessA(muteSuccessResult) {
        ciInteraction.printInfo(s"${AsciiColors.F_BlightGreen}✓${AsciiColors.Reset}  $title")
      }
    } else {
      for {
        _ <- ciInteraction.printInfo(s"${AsciiColors.F_Red}✗${AsciiColors.Reset}  $title")
        _ <- res.errors
          .filter(_.severity.severerThanOrEqualTo(ErrorSeverity.WARNING))
          .traverse_ { e =>
            val pos                   = e.range.start
            val paddedPosition        = f"${pos.line.toInt}%5d:${pos.character.toInt}%-5d"
            val indentAdjuster        = if e.severity == ErrorSeverity.ERROR then "  " else ""
            val humanReadableSeverity = e.severity.toDiagnosticLevelString
            val formattedError        = f" $indentAdjuster$paddedPosition $humanReadableSeverity%-7s  ${e.message}"
            e.severity match {
              case ErrorSeverity.ERROR   => ciInteraction.printError(formattedError)
              case ErrorSeverity.WARNING => ciInteraction.printWarning(formattedError)
              case _                     => Monad[F].unit
            }
          }
      } yield ()
    }
  }

  def extractErrorCount(result: List[AnalysisResult]): Map[ErrorSeverity, Int] = {
    result.flatMap(_.errors).groupBy[ErrorSeverity](_.severity).mapV(_.size)
  }
}

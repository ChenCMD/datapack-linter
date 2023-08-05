package com.github.chencmd.datapacklinter.linter

import com.github.chencmd.datapacklinter.analyzer.AnalysisResult
import com.github.chencmd.datapacklinter.analyzer.ErrorSeverity
import com.github.chencmd.datapacklinter.analyzer.FileUpdate
import com.github.chencmd.datapacklinter.ciplatform.CIPlatformInteractionInstr
import com.github.chencmd.datapacklinter.generic.MapExtra.*
import com.github.chencmd.datapacklinter.term.FileUpdates

import cats.Monad
import cats.effect.Async
import cats.implicits.*

import scala.util.chaining.*


object DatapackLinter {
  def printFileUpdatesLog[F[_]: Async](
    fileUpdates: FileUpdates
  )(using ciInteraction: CIPlatformInteractionInstr[F]): F[Unit] = {
    fileUpdates
      .groupMap(_._2)(t => t._1)
      .map { case (k, v) => k -> v.toList }
      .pipe { fm =>
        def log(state: FileUpdate, stateMes: String) = fm.getOrEmpty(state).traverse_ { file =>
          ciInteraction.printDebug(s"file $stateMes detected: $file")
        }
        log(FileUpdate.Created, "add")
          >> log(FileUpdate.ContentUpdated, "change")
          >> log(FileUpdate.RefsUpdated, "ref change")
          >> log(FileUpdate.Deleted, "delete")
      }
  }

  def printResult[F[_]: Async](res: AnalysisResult, muteSuccessResult: Boolean)(using
    ciInteraction: CIPlatformInteractionInstr[F]
  ): F[Unit] = {
    val title = s"${res.resourcePath} (${res.dpFilePath})"

    if (!res.errors.exists(_.severity <= 2)) {
      Monad[F].unlessA(muteSuccessResult) {
        ciInteraction.printInfo(s"\u001b[92m✓\u001b[39m  ${title}")
      }
    } else {
      for {
        _ <- ciInteraction.printInfo(s"\u001b[91m✗\u001b[39m  ${title}")
        _ <- res.errors
          .filter(_.severity <= 2)
          .map { e =>
            val pos                   = e.range.start
            val paddedPosition        = f"${pos.line.asInstanceOf[Int]}%5d:${pos.character.asInstanceOf[Int]}%-5d"
            val indentAdjuster        = " " * (if (e.severity == 1) then 2 else 0)
            val humanReadableSeverity = {
              val raw = e.severity match {
                case 1 => "Error"
                case 2 => "Warning"
                case _ => "Unknown"
              }
              f"${raw}%-7s"
            }
            (e.severity, s" $indentAdjuster$paddedPosition $humanReadableSeverity ${e.message}")
          }
          .traverse_ {
            case (1, res) => ciInteraction.printError(res)
            case (2, res) => ciInteraction.printWarning(res)
            case (_, res) => ciInteraction.printInfo(res)
          }
      } yield ()
    }
  }

  def extractErrorCount(result: List[AnalysisResult]): Map[ErrorSeverity, Int] = {
    result.foldLeft(Map.empty[ErrorSeverity, Int]) { (map, r) =>
      r.errors.foldLeft(map)((m, e) => m.updatedWith(e.severity)(a => Some(a.getOrElse(0) + 1)))
    }
  }
}

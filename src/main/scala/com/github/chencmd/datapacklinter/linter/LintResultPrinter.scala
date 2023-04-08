package com.github.chencmd.datapacklinter.linter

import com.github.chencmd.datapacklinter.analyze.AnalyzeResult
import com.github.chencmd.datapacklinter.ciplatform.CIPlatformInteractionInstr

import cats.Monad
import cats.data.EitherT
import cats.effect.kernel.Async
import cats.implicits.*

object LintResultPrinter {
  def print[F[_]: Async](res: AnalyzeResult, muteSuccessResult: Boolean)(using
    ciInteraction: CIPlatformInteractionInstr[F]
  ): F[Unit] = {
    val title = s"${res.resourcePath} (${res.dpFilePath})"

    if (res.errors.exists(_.severity <= 2)) {
      for {
        _ <- ciInteraction.printInfo(s"\u001b[91m✗\u001b[39m  ${title}")
        _ <- res.errors
          .filter(_.severity <= 2)
          .map { e =>
            val pos                   = e.range.start
            val paddedPosition        =
              f"${pos.line.asInstanceOf[Int]}%5d:${pos.character.asInstanceOf[Int]}%-5d"
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
          .traverse {
            case (1, res) => ciInteraction.printError(res)
            case (2, res) => ciInteraction.printWarning(res)
            case (_, res) => ciInteraction.printInfo(res)
          }
      } yield ()
    } else if (!muteSuccessResult) {
      ciInteraction.printInfo(s"\u001b[92m✓\u001b[39m  ${title}")
    } else {
      Monad[F].unit
    }
  }
}

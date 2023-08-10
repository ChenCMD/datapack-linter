package com.github.chencmd.datapacklinter.analyzer

import com.github.chencmd.datapacklinter.utils.Path

import cats.effect.Async
import cats.implicits.*

import scala.scalajs.js
import scala.scalajs.js.JSConverters.*

import typings.vscodeLanguageserverTextdocument.mod.TextDocument
import typings.vscodeLanguageserverTypes.mod.Diagnostic
import typings.vscodeLanguageserverTypes.mod.Range

import typings.spgodingDatapackLanguageServer.libNodesIdentityNodeMod.IdentityNode
import typings.spgodingDatapackLanguageServer.libTypesDatapackDocumentMod.DatapackDocument
import typings.spgodingDatapackLanguageServer.libTypesParsingErrorMod.ParsingError

trait JSAnalysisResult extends js.Object {
  val absolutePath: String
  val dpFilePath: String
  val resourcePath: String
  val errors: js.Array[JSDocumentError]
}

final case class AnalysisResult(
  absolutePath: Path,
  dpFilePath: Path,
  resourcePath: String,
  errors: List[DocumentError]
) {
  def toJSObject: JSAnalysisResult = {
    new JSAnalysisResult {
      val absolutePath = AnalysisResult.this.absolutePath.toString
      val dpFilePath   = AnalysisResult.this.dpFilePath.toString
      val resourcePath = AnalysisResult.this.resourcePath
      val errors       = AnalysisResult.this.errors.map(_.toJSObject).toJSArray
    }
  }
}

object AnalysisResult {
  def apply[F[_]: Async](
    root: Path,
    filePath: Path,
    id: IdentityNode,
    parsedDoc: DatapackDocument,
    doc: TextDocument
  ): F[AnalysisResult] = {
    parsedDoc.nodes
      .flatMap(_.errors)
      .toList
      .traverse(DocumentError(_, doc))
      .map { a =>
        AnalysisResult(
          filePath,
          Path.relative(Path.dirname(root), filePath),
          id.toString(),
          a.sortBy(_.range.start.line)
        )
      }
  }

  def fromJSObject(obj: JSAnalysisResult): AnalysisResult = {
    AnalysisResult(
      Path.coerce(obj.absolutePath),
      Path.coerce(obj.dpFilePath),
      obj.resourcePath,
      obj.errors.toList.map(DocumentError.fromJSObject)
    )
  }
}

type ErrorSeverity = 1 | 2 | 3 | 4

trait JSDocumentError extends js.Object {
  val message: String
  val severity: ErrorSeverity
  val range: Range
}

final case class DocumentError private (message: String, severity: ErrorSeverity, range: Range) {
  def toJSObject: JSDocumentError = {
    new JSDocumentError {
      val message  = DocumentError.this.message
      val severity = DocumentError.this.severity
      val range    = DocumentError.this.range
    }
  }
}

object DocumentError {
  def apply[F[_]: Async](docError: ParsingError, doc: TextDocument): F[DocumentError] = {
    Async[F].delay(docError.toDiagnostic(doc).asInstanceOf[Diagnostic]).map { doc =>
      apply(docError, doc.range)
    }
  }

  def apply(docError: ParsingError, rangeInfo: Range): DocumentError = {
    DocumentError(
      docError.message,
      docError.severity.asInstanceOf[ErrorSeverity],
      rangeInfo
    )
  }

  def fromJSObject(obj: JSDocumentError): DocumentError = {
    DocumentError(obj.message, obj.severity, obj.range)
  }
}

package com.github.chencmd.datapacklinter.analyzer

import cats.effect.Async
import cats.implicits.*

import scala.scalajs.js
import scala.scalajs.js.JSConverters.*

import typings.node.pathMod as path
import typings.vscodeLanguageserverTextdocument.mod.TextDocument
import typings.vscodeLanguageserverTypes.mod.Diagnostic
import typings.vscodeLanguageserverTypes.mod.Range

import typings.spgodingDatapackLanguageServer.libNodesIdentityNodeMod.IdentityNode
import typings.spgodingDatapackLanguageServer.libTypesDatapackDocumentMod.DatapackDocument
import typings.spgodingDatapackLanguageServer.libTypesParsingErrorMod.ParsingError

trait JSAnalyzeResult extends js.Object {
  val absolutePath: String
  val dpFilePath: String
  val resourcePath: String
  val errors: js.Array[JSDocumentError]
}

final case class AnalyzeResult(
  absolutePath: String,
  dpFilePath: String,
  resourcePath: String,
  errors: List[DocumentError]
) {
  def toJSObject: JSAnalyzeResult = {
    new JSAnalyzeResult {
      val absolutePath = AnalyzeResult.this.absolutePath
      val dpFilePath   = AnalyzeResult.this.dpFilePath
      val resourcePath = AnalyzeResult.this.resourcePath
      val errors       = AnalyzeResult.this.errors.map(_.toJSObject).toJSArray
    }
  }
}

object AnalyzeResult {
  def apply[F[_]: Async](
    root: String,
    filePath: String,
    id: IdentityNode,
    parsedDoc: DatapackDocument,
    doc: TextDocument
  ): F[AnalyzeResult] = {
    parsedDoc.nodes
      .flatMap(_.errors)
      .toList
      .traverse(DocumentError(_, doc))
      .map { a =>
        AnalyzeResult(
          filePath,
          path.relative(path.dirname(root), filePath),
          id.toString(),
          a.sortBy(_.range.start.line)
        )
      }
  }

  def fromJSObject(obj: JSAnalyzeResult): AnalyzeResult = {
    AnalyzeResult(
      obj.absolutePath,
      obj.dpFilePath,
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

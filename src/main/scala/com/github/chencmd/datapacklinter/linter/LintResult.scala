package com.github.chencmd.datapacklinter.linter

import cats.effect.Async
import cats.implicits.*

import typings.node.pathMod as path
import typings.vscodeLanguageserverTextdocument.mod.TextDocument
import typings.vscodeLanguageserverTypes.mod.Diagnostic
import typings.vscodeLanguageserverTypes.mod.Range

import typings.spgodingDatapackLanguageServer.libNodesIdentityNodeMod.IdentityNode
import typings.spgodingDatapackLanguageServer.libTypesDatapackDocumentMod.DatapackDocument
import typings.spgodingDatapackLanguageServer.libTypesParsingErrorMod.ParsingError

final case class LintResult(
  dpFilePath: String,
  resourcePath: String,
  errors: List[DocumentError],
  analyzedLength: Int
)

object LintResult {
  def apply[F[_]: Async](
    root: String,
    filePath: String,
    id: IdentityNode,
    parsedDoc: DatapackDocument,
    doc: TextDocument
  ): F[LintResult] = {
    parsedDoc.nodes
      .flatMap(_.errors)
      .toList
      .traverse(DocumentError(_, doc))
      .map { a =>
        LintResult(
          path.relative(path.dirname(root), filePath).replace("\\", "/"),
          id.toString(),
          a.sortBy(_.range.start.line),
          parsedDoc.nodes.length
        )
      }
  }
}

final case class DocumentError private (message: String, severity: Int, range: Range) {}

object DocumentError {
  def apply[F[_]: Async](docError: ParsingError, doc: TextDocument): F[DocumentError] = {
    Async[F].delay(docError.toDiagnostic(doc).asInstanceOf[Diagnostic]).map { doc =>
      apply(docError, doc.range)
    }
  }

  def apply(docError: ParsingError, rangeInfo: Range): DocumentError = {
    DocumentError(
      docError.message,
      docError.severity.asInstanceOf[Int],
      rangeInfo
    )
  }
}

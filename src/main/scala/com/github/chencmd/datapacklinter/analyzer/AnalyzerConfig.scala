package com.github.chencmd.datapacklinter.analyzer

import typings.minimatch.mod as minimatch
import typings.minimatch.mod.IOptions

final case class AnalyzerConfig(
  ignorePaths: List[String]
) {
  def ignorePathsIncludes(path: String): Boolean = {
    ignorePaths.exists(minimatch(path, _, IOptions().setDot(true)))
  }
}

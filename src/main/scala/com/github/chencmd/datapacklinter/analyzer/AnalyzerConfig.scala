package com.github.chencmd.datapacklinter.analyzer

import typings.minimatch.mod.Minimatch
import typings.minimatch.mod.IOptions

final case class AnalyzerConfig(ignorePaths: List[String]) {
  def ignorePathsIncludes(path: String): Boolean = {
    ignorePaths.exists(Minimatch(_, IOptions().setDot(true)).`match`(path))
  }
}

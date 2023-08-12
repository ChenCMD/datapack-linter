package com.github.chencmd.datapacklinter.analyzer

import typings.minimatch.mod.IOptions
import typings.minimatch.mod.Minimatch

final case class AnalyzerConfig(ignorePaths: List[String]) {
  def ignorePathsIncludes(path: String): Boolean = {
    ignorePaths.exists(Minimatch(_, IOptions().setDot(true)).`match`(path))
  }
}

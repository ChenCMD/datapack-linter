package com.github.chencmd.datapacklinter.analyzer

import scala.util.matching.Regex

final class AnalyzerConfig private (private val ignoreRegexps: List[Regex]) {
  def ignorePathsIncludes(path: String): Boolean = {
    ignoreRegexps.exists(r => r.matches(path))
  }
}

object AnalyzerConfig {
  private val REPLACERS = List(
    "?"   -> "[^:/]",
    "**/" -> ".{0,}",
    "**"  -> ".{0,}",
    "*"   -> "[^:/]{0,}"
  )

  def apply(ignorePaths: List[String]): AnalyzerConfig = {
    new AnalyzerConfig(ignorePaths.map(p => s"^${REPLACERS.foldLeft(p)(_.replace.tupled(_))}$$".r))
  }
}

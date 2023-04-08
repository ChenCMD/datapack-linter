package com.github.chencmd.datapacklinter.analyze

enum AnalyzeState(val root: String, val abs: String) {
  case Waiting(override val root: String, override val abs: String, val rel: String)
      extends AnalyzeState(root, abs)
  case Cached(override val root: String, override val abs: String, val res: AnalyzeResult)
      extends AnalyzeState(root, abs)
}

object AnalyzeState {
  given Ordering[AnalyzeState] = new Ordering[AnalyzeState] {
    override def compare(x: AnalyzeState, y: AnalyzeState): Int = x.abs compare y.abs
  }
}

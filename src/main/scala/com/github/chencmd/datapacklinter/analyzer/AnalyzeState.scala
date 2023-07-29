package com.github.chencmd.datapacklinter.analyzer

enum AnalyzeState {
  case Waiting(root: String, abs: String, rel: String)
  case Cached(cachedResult: AnalyzeResult)
}

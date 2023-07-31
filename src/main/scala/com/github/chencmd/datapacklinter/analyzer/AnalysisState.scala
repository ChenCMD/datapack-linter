package com.github.chencmd.datapacklinter.analyzer

enum AnalysisState {
  case Waiting(root: String, abs: String, rel: String)
  case Cached(cachedResult: AnalysisResult)
}

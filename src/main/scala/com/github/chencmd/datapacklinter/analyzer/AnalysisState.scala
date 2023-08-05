package com.github.chencmd.datapacklinter.analyzer

import com.github.chencmd.datapacklinter.utils.Path

enum AnalysisState {
  case Waiting(root: Path, abs: Path, rel: Path)
  case Cached(cachedResult: AnalysisResult)
}

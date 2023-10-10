package com.github.chencmd.datapacklinter

import com.github.chencmd.datapacklinter.analyzer.AnalysisResult
import com.github.chencmd.datapacklinter.analyzer.FileUpdate
import com.github.chencmd.datapacklinter.utils.Path

package term {
  type ResourcePath = String

  type Checksum = String

  type FileUpdates = Map[Path, FileUpdate]

  type FileChecksums = Map[Path, Checksum]

  type AnalysisCache = Map[Path, AnalysisResult]

  enum RestoreCacheOrSkip {
    case Restore
    case Skip(reason: String)
  }
}

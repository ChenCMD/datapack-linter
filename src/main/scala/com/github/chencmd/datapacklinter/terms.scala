package com.github.chencmd.datapacklinter

import com.github.chencmd.datapacklinter.analyzer.FileUpdate

package terms {
  type FilePath = String

  type Checksum = String

  /**
    * 解析の対象とするファイル
    */
  type AnalysisTargetFiles = String

  type FileUpdates = Map[FilePath, FileUpdate]

  type FileChecksums = Map[FilePath, Checksum]
}

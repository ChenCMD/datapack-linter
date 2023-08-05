package com.github.chencmd.datapacklinter.ciplatform

import com.github.chencmd.datapacklinter.generic.RaiseNec
import com.github.chencmd.datapacklinter.utils.Path

trait CIPlatformManageCacheInstr[F[_]] {
  def store(paths: List[Path]): F[Unit]

  def restore(paths: List[Path])(using R: RaiseNec[F, String]): F[Boolean]
}

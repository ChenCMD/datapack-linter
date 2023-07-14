package com.github.chencmd.datapacklinter.ciplatform

import com.github.chencmd.datapacklinter.generic.RaiseNec

trait CIPlatformManageCacheInstr[F[_]] {
  def store(paths: List[String]): F[Unit]

  def restore(paths: List[String])(using R: RaiseNec[F, String]): F[Boolean]
}

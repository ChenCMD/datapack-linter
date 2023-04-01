package com.github.chencmd.datapacklinter.ciplatform

trait CIPlatformInteractionInstr[F[_]] {
  def printError(msg: String): F[Unit]

  def printWarning(msg: String): F[Unit]

  def printInfo(msg: String): F[Unit]

  def printDebug(msg: String): F[Unit]

  def startGroup(header: String): F[Unit]

  def endGroup(): F[Unit]

  def setOutput(key: String, value: Any): F[Unit]
}

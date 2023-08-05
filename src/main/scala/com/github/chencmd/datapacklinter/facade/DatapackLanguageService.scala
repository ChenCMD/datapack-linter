package com.github.chencmd.datapacklinter.facade

import com.github.chencmd.datapacklinter.dls.DLSConfig
import com.github.chencmd.datapacklinter.utils.URI

import scala.scalajs.js

import typings.vscodeLanguageserverTextdocument.mod.TextDocument

import typings.spgodingDatapackLanguageServer.libDataVanillaDataMod.VanillaData
import typings.spgodingDatapackLanguageServer.libServicesDatapackLanguageServiceMod.DatapackLanguageServiceOptions
import typings.spgodingDatapackLanguageServer.libTypesClientCacheMod.CacheFile
import typings.spgodingDatapackLanguageServer.libTypesClientCacheMod.ClientCache
import typings.spgodingDatapackLanguageServer.libTypesDatapackDocumentMod.DatapackDocument
import typings.spgodingDatapackLanguageServer.mod.DatapackLanguageService as OriginDLS

opaque type DatapackLanguageService = OriginDLS

object DatapackLanguageService {
  def apply(options: DatapackLanguageServiceOptions): DatapackLanguageService = new OriginDLS(options)

  extension (dls: DatapackLanguageService) {
    inline def roots: List[URI] = dls.roots.toList.map(URI.coerce(_))

    inline def pushRoots(uris: List[URI]): Unit = dls.roots.push(uris.map(_.vs)*)

    inline def cacheFile: CacheFile = dls.cacheFile

    inline def init(): js.Promise[Unit] = dls.init()

    inline def getVanillaData(config: DLSConfig): js.Promise[VanillaData] = dls.getVanillaData(config)

    inline def parseDocument(document: TextDocument): js.Promise[js.UndefOr[DatapackDocument]] = {
      dls.parseDocument(document)
    }

    inline def onAddedFile(uri: URI): js.Promise[Unit]    = dls.onAddedFile(uri.vs)
    inline def onModifiedFile(uri: URI): js.Promise[Unit] = dls.onModifiedFile(uri.vs)
    inline def onDeletedFile(uri: URI): Unit              = dls.onDeletedFile(uri.vs)

    inline def clearCaches(): Unit = dls.caches.asInstanceOf[js.Map[String, ClientCache]].clear()
  }
}

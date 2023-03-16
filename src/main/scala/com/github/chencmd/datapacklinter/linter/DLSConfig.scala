package com.github.chencmd.datapacklinter.linter

import cats.effect.IO
import cats.data.OptionT
import cats.syntax.all.*

import com.github.chencmd.datapacklinter.utils.Jsonc
import com.github.chencmd.datapacklinter.generic.FSPExtra
import com.github.chencmd.datapacklinter.generic.CastOps.*
import com.github.chencmd.datapacklinter.generic.WrappedDictionaryExtra.*

import typings.spgodingDatapackLanguageServer.libTypesMod.Uri
import typings.spgodingDatapackLanguageServer.libTypesConfigMod as Cfg
import typings.spgodingDatapackLanguageServer.libTypesConfigMod.Config

import org.scalablytyped.runtime.StringDictionary

import scalajs.js

import scala.annotation.tailrec
import scala.util.chaining.*

object DLSConfig {
  def readConfig(configFilePath: String): IO[Config] = {
    val program = for {
      isAccessible <- OptionT.liftF(FSPExtra.pathAccessible(configFilePath))
      _            <- OptionT.when(isAccessible) {
        IO.println("Could not access the .vscode config. Use the default config file.")
      }
      rawJson      <- OptionT.liftF(FSPExtra.readFile(configFilePath))
      json         <- OptionT.fromOption {
        Jsonc
          .parse(rawJson)
          .filter(v => js.typeOf(v) == "object" && v != null)
          .map(_.asInstanceOf[StringDictionary[js.Any]])
      }
      customConfig <- OptionT.pure {
        json.iterator
          .foldLeft(StringDictionary.empty[js.Any]) {
            case (obj, (key, value)) => walk(obj, key.split(".").toList, value)
          }
      }
    } yield Cfg.constructConfig(customConfig)

    program.getOrElse(Cfg.constructConfig(StringDictionary.empty))
  }

  private def walk(
    obj: StringDictionary[js.Any],
    keyFragments: List[String],
    value: js.Any
  ): StringDictionary[js.Any] = {
    keyFragments match {
      case splittedKey :: tails if tails.nonEmpty =>
        obj
          .updateWith(splittedKey) { v =>
            walk(
              v.map(_.asInstanceOf[StringDictionary[js.Any]]).getOrElse(StringDictionary.empty),
              tails,
              value
            ).some
          }
          .toStringDictionary
      case splittedKey :: tails => obj.addOne(splittedKey, value).toStringDictionary
      case Nil                  => StringDictionary.empty
    }
  }
}

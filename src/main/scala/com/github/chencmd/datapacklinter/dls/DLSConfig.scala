package com.github.chencmd.datapacklinter.dls

import com.github.chencmd.datapacklinter.ciplatform.CIPlatformInteractionInstr
import com.github.chencmd.datapacklinter.generic.OptionTExtra
import com.github.chencmd.datapacklinter.generic.WrappedDictionaryExtra.*
import com.github.chencmd.datapacklinter.utils.FSAsync
import com.github.chencmd.datapacklinter.utils.Jsonc
import com.github.chencmd.datapacklinter.utils.Path

import cats.data.OptionT
import cats.effect.Async
import cats.implicits.*

import scala.scalajs.js

import org.scalablytyped.runtime.StringDictionary
import typings.spgodingDatapackLanguageServer.libTypesConfigMod as Cfg
import typings.spgodingDatapackLanguageServer.libTypesConfigMod.Config as OriginDLSConfig

type DLSConfig = OriginDLSConfig

object DLSConfig {
  def readConfig[F[_]: Async](
    configFilePath: Path
  )(using ciInteraction: CIPlatformInteractionInstr[F]): F[DLSConfig] = {
    val program = for {
      isAccessible <- FSAsync.pathAccessible[OptionT[F, _]](configFilePath)
      _            <- OptionTExtra.exitUnlessF(isAccessible) {
        ciInteraction.printInfo("Could not access the .vscode config. Use the default config file.")
      }
      rawJson      <- FSAsync.readFile[OptionT[F, _]](configFilePath)
      json         <- OptionT.fromOption {
        Jsonc
          .parse(rawJson)
          .filter(v => js.typeOf(v) == "object" && v != null)
          .map(_.asInstanceOf[StringDictionary[js.Any]])
      }
      customConfig <- OptionT.fromOption {
        json.toList
          .foldLeft(StringDictionary.empty[js.Any]) {
            case (obj, (key, value)) => walk(obj, key.split("\\.").toList, value)
          }
          .get("datapack")
          .filter(v => js.typeOf(v) == "object" && v != null)
          .map(_.asInstanceOf[StringDictionary[js.Any]])
      }
    } yield Cfg.constructConfig(customConfig)

    program.getOrElse(Cfg.constructConfig(StringDictionary.empty))
  }

  private def walk(
    obj: StringDictionary[js.Any],
    keyFragments: List[String],
    value: js.Any
  ): StringDictionary[js.Any] = keyFragments match {
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
    case splittedKey :: _                       => obj.addOne(splittedKey, value).toStringDictionary
    case Nil                                    => StringDictionary.empty
  }
}

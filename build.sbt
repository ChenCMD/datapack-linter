name := "Datapack Linter"
ThisBuild / version := "2.0.0-SNAPSHOT"

ThisBuild / scalaVersion := "3.2.1"

enablePlugins(ScalaJSPlugin)
enablePlugins(ScalaJSBundlerPlugin)
enablePlugins(ScalablyTypedConverterPlugin)

Compile / fastOptJS / webpackConfigFile := Some(baseDirectory.value / "webpack.config-fast.js")
Compile / fullOptJS / webpackConfigFile := Some(baseDirectory.value / "webpack.config-full.js")
Compile / npmDependencies ++= Seq(
  "@actions/core" -> "^1.10.0",
  "@actions/github" -> "^5.1.1",
  "@spgoding/datapack-language-server" -> "3.4.7",
  "jsonc-parser" -> "^3.2.0"
)
Compile / npmDevDependencies ++= Seq(
  "@types/node" -> "13.13.4"
)
useYarn := true

scalaJSLinkerConfig ~= { _.withModuleKind(ModuleKind.CommonJSModule) }

scalaJSUseMainModuleInitializer := true

javaOptions ++= Seq(
  "-Xmx2G",
  "-XX:+UseG1GC"
)

libraryDependencies ++= Seq(
  "org.typelevel" %%% "cats-effect" % "3.4.8"
)
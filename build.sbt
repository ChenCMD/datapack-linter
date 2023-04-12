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
  "jsonc-parser" -> "^3.2.0",
  "minimatch" -> "^3.0.4"
)
Compile / npmDevDependencies ++= Seq(
  "@types/node" -> "13.13.4"
)
useYarn := true

webpack / version := "5.54.0"

scalaJSLinkerConfig ~= { _.withModuleKind(ModuleKind.CommonJSModule) }

scalaJSUseMainModuleInitializer := true

ThisBuild / scalafixDependencies ++= Seq(
  "com.github.liancheng" %% "organize-imports" % "0.5.0"
)
semanticdbEnabled := true
semanticdbVersion := scalafixSemanticdb.revision

scalacOptions ++= Seq(
  "-deprecation",
  "-Ykind-projector:underscores"
)

javaOptions ++= Seq(
  "-Xmx4G",
  "-XX:+UseG1GC"
)

libraryDependencies ++= Seq(
  "org.typelevel" %%% "cats-effect" % "3.4.8"
)

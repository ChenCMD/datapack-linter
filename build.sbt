name                := "Datapack Linter"
ThisBuild / version := "2.0.0-SNAPSHOT"

ThisBuild / scalaVersion := "3.3.0"

enablePlugins(ScalaJSPlugin)
enablePlugins(ScalaJSBundlerPlugin)
enablePlugins(ScalablyTypedConverterPlugin)

Compile / fastOptJS / webpackConfigFile := Some(baseDirectory.value / "webpack.config-fast.js")
Compile / fullOptJS / webpackConfigFile := Some(baseDirectory.value / "webpack.config-full.js")
Compile / npmDependencies ++= Seq(
  "@actions/cache"                     -> "^3.2.1",
  "@actions/core"                      -> "^1.10.0",
  "@actions/github"                    -> "^5.1.1",
  "@spgoding/datapack-language-server" -> "3.4.7",
  "jsonc-parser"                       -> "^3.2.0",
  "minimatch"                          -> "^3.0.4",
  "@octokit/webhooks-types"            -> "^7.0.3",
  "object-hash"                        -> "^3.0.0",
  "filter-console"                     -> "^1.0.0"
)
Compile / npmDevDependencies ++= Seq(
  "@types/node"        -> "13.13.4",
  "@types/object-hash" -> "^3.0.2"
)
useYarn                                 := true

webpack / version := "5.54.0"

scalaJSLinkerConfig ~= { _.withModuleKind(ModuleKind.CommonJSModule) }

scalaJSUseMainModuleInitializer := true

semanticdbEnabled := true
semanticdbVersion := scalafixSemanticdb.revision

scalacOptions ++= Seq(
  "-deprecation",
  "-feature",
  "-unchecked",
  "-Ykind-projector:underscores",
  "-no-indent",
  "-Wunused:all",
  "-source:future"
)

javaOptions ++= Seq(
  "-Xmx4G",
  "-XX:+UseG1GC"
)

libraryDependencies ++= Seq(
  "org.typelevel" %%% "cats-effect" % "3.4.8",
  "org.typelevel" %%% "cats-mtl"    % "1.3.0"
)

name                := "Datapack Linter"
ThisBuild / version := "2.0.0-SNAPSHOT"

ThisBuild / scalaVersion := "3.3.0"

enablePlugins(ScalaJSPlugin)
enablePlugins(ScalaJSBundlerPlugin)
enablePlugins(ScalablyTypedConverterPlugin)

Compile / fastOptJS / webpackConfigFile := Some(baseDirectory.value / "webpack.config-fast.js")
Compile / fullOptJS / webpackConfigFile := Some(baseDirectory.value / "webpack.config-full.js")
Compile / npmDependencies ++= Seq(
  "@actions/cache"                     -> "3.2.2",
  "@actions/core"                      -> "1.10.1",
  "@actions/github"                    -> "6.0.0",
  "@spgoding/datapack-language-server" -> "3.4.18",
  "jsonc-parser"                       -> "3.2.0",
  "@octokit/webhooks-types"            -> "7.3.1",
  "object-hash"                        -> "3.0.0",
  "filter-console"                     -> "1.0.0"
)
Compile / npmDevDependencies ++= Seq(
  "@types/node"           -> "16.18.40",
  "@types/object-hash"    -> "3.0.2",
  "string-replace-loader" -> "3.1.0"
)
useYarn                                 := true

webpack / version := "5.88.2"

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

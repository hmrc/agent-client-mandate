import play.routes.compiler.InjectedRoutesGenerator
import play.sbt.routes.RoutesCompiler.defaultSettings
import play.sbt.routes.RoutesKeys
import play.sbt.routes.RoutesKeys.routesGenerator
import sbt.*
import sbt.Keys.*
import uk.gov.hmrc.DefaultBuildSettings.scalaSettings
import uk.gov.hmrc.sbtdistributables.SbtDistributablesPlugin
import uk.gov.hmrc.versioning.SbtGitVersioning.autoImport.majorVersion
import uk.gov.hmrc.DefaultBuildSettings

val appName = "agent-client-mandate"

ThisBuild / majorVersion := 1
ThisBuild / scalaVersion := "2.13.16"

lazy val appDependencies: Seq[ModuleID] = AppDependencies()
lazy val plugins: Seq[Plugins] = Seq.empty
lazy val playSettings: Seq[Setting[?]] = Seq.empty

lazy val microservice = Project(appName, file("."))
  .enablePlugins((Seq( play.sbt.PlayScala, SbtDistributablesPlugin ) ++ plugins) *)
  .settings(CodeCoverageSettings.settings *)
  .settings(playSettings *)
  .settings(defaultSettings *)
  .settings(RoutesKeys.routesImport ++= Seq("uk.gov.hmrc.agentclientmandate.binders.DelegationPathBinders._"))
  .settings(
    libraryDependencies ++= appDependencies,
    retrieveManaged := true,
    routesGenerator := InjectedRoutesGenerator,
    Test / parallelExecution := true,
    Test / fork := true,
    scalacOptions += "-Wconf:src=routes/.*:s",
    scalacOptions ++= Seq("-feature")
  )
  .disablePlugins(JUnitXmlReportPlugin)
  .settings(
    resolvers ++= Seq()
  )

lazy val it = project
  .enablePlugins(PlayScala)
  .dependsOn(microservice % "test->test") // the "test->test" allows reusing test code and test dependencies
  .settings(DefaultBuildSettings.itSettings())
  .settings(libraryDependencies ++= AppDependencies.itDependencies)
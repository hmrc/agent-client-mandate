import play.routes.compiler.InjectedRoutesGenerator
import play.sbt.routes.RoutesKeys
import play.sbt.routes.RoutesKeys.routesGenerator
import sbt.Keys._
import sbt._
import uk.gov.hmrc.DefaultBuildSettings.{addTestReportOption, defaultSettings, scalaSettings}
import uk.gov.hmrc.SbtAutoBuildPlugin
import uk.gov.hmrc.sbtdistributables.SbtDistributablesPlugin
import uk.gov.hmrc.versioning.SbtGitVersioning
import uk.gov.hmrc.versioning.SbtGitVersioning.autoImport.majorVersion

val appName = "agent-client-mandate"

lazy val appDependencies: Seq[ModuleID] = AppDependencies()
lazy val plugins: Seq[Plugins] = Seq.empty
lazy val playSettings: Seq[Setting[_]] = Seq.empty

lazy val scoverageSettings = {
  import scoverage.ScoverageKeys
  Seq(
    ScoverageKeys.coverageExcludedPackages := "<empty>;Reverse.*;app.Routes.*;prod.*;.*testOnly.*;" +
      "uk.gov.hmrc.BuildInfo*;.*binders.*;.*MicroserviceAuditConnector*;.*MicroserviceAuthConnector*;" +
      ".*WSHttp*;uk.gov.hmrc.agentclientmandate.config.*;",
    ScoverageKeys.coverageMinimumStmtTotal := 80,
    ScoverageKeys.coverageFailOnMinimum := true,
    ScoverageKeys.coverageHighlighting := true
  )
}

lazy val microservice = Project(appName, file("."))
  .enablePlugins(Seq(play.sbt.PlayScala, SbtAutoBuildPlugin, SbtGitVersioning, SbtDistributablesPlugin) ++ plugins: _*)
  .settings(playSettings: _*)
  .settings(majorVersion := 1)
  .configs(IntegrationTest)
  .settings(scalaSettings: _*)
  .settings(defaultSettings(): _*)
  .settings(RoutesKeys.routesImport ++= Seq("uk.gov.hmrc.agentclientmandate.binders.DelegationPathBinders._"))
  .settings(playSettings ++ scoverageSettings: _*)
  .settings(
    addTestReportOption(IntegrationTest, "int-test-reports"),
    inConfig(IntegrationTest)(Defaults.itSettings),
    scalaVersion := "2.13.8",
    libraryDependencies ++= appDependencies,
    retrieveManaged := true,
    routesGenerator := InjectedRoutesGenerator,
    Test / parallelExecution := true,
    Test / fork := true,
    IntegrationTest / Keys.fork := false,
    IntegrationTest / unmanagedSourceDirectories := (IntegrationTest / baseDirectory) (base => Seq(base / "it")).value,
    IntegrationTest / parallelExecution := false,
    scalacOptions += "-Wconf:src=routes/.*:s",
    scalacOptions ++= Seq("-feature")
  )
  .configs(IntegrationTest)
  .disablePlugins(JUnitXmlReportPlugin)
  .settings(
    resolvers += Resolver.jcenterRepo
  )


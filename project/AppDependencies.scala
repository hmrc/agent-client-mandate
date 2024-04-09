import sbt.*
import play.sbt.PlayImport.*

private object AppDependencies {

  val compile: Seq[ModuleID] = Seq(
    ws,
    "uk.gov.hmrc.mongo" %% "hmrc-mongo-play-30"         % "1.7.0",
    "uk.gov.hmrc"       %% "bootstrap-backend-play-30"  % "8.5.0",
    "uk.gov.hmrc"       %% "domain-play-30"             % "9.0.0",
  )

  val test: Seq[ModuleID] = Seq(
    "uk.gov.hmrc"                  %% "bootstrap-test-play-30"    % "8.5.0",
    "uk.gov.hmrc.mongo"            %% "hmrc-mongo-test-play-30"   % "1.7.0",
    "org.mockito"                  %  "mockito-core"              % "5.11.0",
    "org.mockito"                  %% "mockito-scala"             % "1.17.31",
    "org.mockito"                  %% "mockito-scala-scalatest"   % "1.17.31",
    "org.scalatestplus"            %% "scalacheck-1-17"           % "3.2.17.0",
    "org.apache.pekko" %% "pekko-testkit" % "1.0.2"
//    "com.fasterxml.jackson.module" %% "jackson-module-scala"      % "2.15.3",
//    "com.github.tomakehurst"       %  "wiremock-jre8"             % "2.35.1",
  ).map(_ % Test)

  val itDependencies: Seq[ModuleID] = Seq()

  def apply(): Seq[ModuleID] = compile ++ test
}
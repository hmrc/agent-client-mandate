import sbt.*
import play.sbt.PlayImport.*

private object AppDependencies {

  val compile: Seq[ModuleID] = Seq(
    ws,
    "uk.gov.hmrc.mongo" %% "hmrc-mongo-play-30"         % "1.7.0",
    "uk.gov.hmrc"       %% "bootstrap-frontend-play-30" % "8.5.0",
    "uk.gov.hmrc"       %% "domain-play-30"             % "9.0.0",
  )

  val test: Seq[ModuleID] = Seq(
    "uk.gov.hmrc"                  %% "bootstrap-test-play-30"   % "8.5.0",
    "uk.gov.hmrc.mongo"            %% "hmrc-mongo-test-play-30"   % "1.7.0",
    "org.scalatestplus"            %% "scalacheck-1-17"           % "3.2.18.0",
    "com.typesafe.akka"            %% "akka-testkit"              % "2.9.0-M2"
  ).map(_ % Test)

  val itDependencies: Seq[ModuleID] = Seq()

  def apply(): Seq[ModuleID] = compile ++ test
}
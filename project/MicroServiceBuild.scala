import sbt._

object MicroServiceBuild extends Build with MicroService {

  val appName = "agent-client-mandate"

  override lazy val appDependencies: Seq[ModuleID] = AppDependencies()
}

private object AppDependencies {

  import play.sbt.PlayImport._
  import play.core.PlayVersion

  val compile: Seq[ModuleID] = Seq(
    "uk.gov.hmrc" %% "simple-reactivemongo" % "7.22.0-play-26",
    ws,
    "uk.gov.hmrc" %% "bootstrap-play-26" % "1.3.0",
    "uk.gov.hmrc" %% "domain" % "5.6.0-play-26",
    "uk.gov.hmrc" %% "mongo-lock" % "6.15.0-play-26",
    "uk.gov.hmrc" %% "play-scheduling" % "7.1.0-play-26",
    "uk.gov.hmrc" %% "auth-client" % "2.32.1-play-26",
    "com.typesafe.play" %% "play-json-joda" % "2.6.10"
  )

  trait TestDependencies {
    lazy val scope: String = "test,it"
    lazy val test: Seq[ModuleID] = ???
  }

  object Test {
    def apply(): Seq[ModuleID] = new TestDependencies {
      override lazy val test: Seq[ModuleID] = Seq(
        "uk.gov.hmrc"             %% "hmrctest"           % "3.9.0-play-26"     % scope,
        "org.scalatest"           %% "scalatest"          % "3.0.5"             % scope,
        "org.scalatestplus.play"  %% "scalatestplus-play" % "3.1.2"             % scope,
        "org.pegdown"              % "pegdown"            % "1.6.0"             % scope,
        "com.typesafe.play"       %% "play-test"          % PlayVersion.current % scope,
        "uk.gov.hmrc"             %% "reactivemongo-test" % "4.16.0-play-26"    % scope,
        "com.typesafe.akka"        % "akka-testkit_2.11"  % "2.5.23"            % scope,
        "org.mockito"              % "mockito-core"       % "2.24.5"            % scope,
        "org.scalacheck"          %% "scalacheck"         % "1.14.0"            % scope,
        "com.github.tomakehurst"   % "wiremock-jre8"      % "2.23.2"            % IntegrationTest withSources()
      )
    }.test
  }


  def apply(): Seq[ModuleID] = compile ++ Test()
}


import sbt._

object AppDependencies {

  import play.core.PlayVersion
  import play.sbt.PlayImport._

  val compile: Seq[ModuleID] = Seq(
    "uk.gov.hmrc" %% "simple-reactivemongo" % "7.31.0-play-27",
    "com.typesafe.akka" %% "akka-actor" % "2.6.14",
    "com.typesafe.akka" %% "akka-protobuf" % "2.6.14",
    "com.typesafe.akka" %% "akka-stream" % "2.6.14",
    "com.typesafe.akka" %% "akka-slf4j" % "2.6.14",
    ws,
    "uk.gov.hmrc"%% "bootstrap-backend-play-27"  % "3.4.0",
    "uk.gov.hmrc" %% "domain" % "5.11.0-play-27",
    "uk.gov.hmrc" %% "mongo-lock" % "6.24.0-play-27",
    "uk.gov.hmrc" %% "play-scheduling-play-27" % "7.10.0",
    "uk.gov.hmrc" %% "auth-client" % "3.3.0-play-27",
    "com.typesafe.play" %% "play-json-joda" % "2.7.4"
  )

  trait TestDependencies {
    lazy val scope: String = "test,it"
    lazy val test: Seq[ModuleID] = ???
  }

  object Test {
    def apply(): Seq[ModuleID] = new TestDependencies {
      override lazy val test: Seq[ModuleID] = Seq(
        "org.scalatest"           %% "scalatest"          % "3.0.9"             % scope,
        "org.scalatestplus.play"  %% "scalatestplus-play" % "4.0.3"             % scope,
        "org.pegdown"              % "pegdown"            % "1.6.0"             % scope,
        "com.typesafe.play"       %% "play-test"          % PlayVersion.current % scope,
        "uk.gov.hmrc"             %% "reactivemongo-test" % "4.22.0-play-27"    % scope,
        "com.typesafe.akka"       %% "akka-testkit"       % "2.6.14"             % scope,
        "org.mockito"              % "mockito-core"       % "3.3.3"            % scope,
        "org.scalacheck"          %% "scalacheck"         % "1.14.3"            % scope,
        "com.github.tomakehurst"   % "wiremock-jre8"      % "2.23.2"            % IntegrationTest withSources()
      )
    }.test
  }


  def apply(): Seq[ModuleID] = compile ++ Test()
}

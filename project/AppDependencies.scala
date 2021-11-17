import sbt._

object AppDependencies {

  import play.core.PlayVersion
  import play.sbt.PlayImport._

  val compile: Seq[ModuleID] = Seq(
    "uk.gov.hmrc" %% "simple-reactivemongo" % "8.0.0-play-28",
    "com.typesafe.akka" %% "akka-actor" % "2.6.17",
    "com.typesafe.akka" %% "akka-protobuf" % "2.6.17",
    "com.typesafe.akka" %% "akka-stream" % "2.6.17",
    "com.typesafe.akka" %% "akka-slf4j" % "2.6.17",
    "com.typesafe.akka" %% "akka-actor-typed" % "2.6.17",
    "com.typesafe.akka" %% "akka-serialization-jackson" % "2.6.17",
    ws,
    "uk.gov.hmrc"%% "bootstrap-backend-play-28" % "5.16.0",
    "uk.gov.hmrc" %% "domain" % "6.2.0-play-28",
    "uk.gov.hmrc" %% "mongo-lock" % "7.0.0-play-28",
    "com.typesafe.play" %% "play-json-joda" % "2.9.2"
  )

  trait TestDependencies {
    lazy val scope: String = "test,it"
    lazy val test: Seq[ModuleID] = ???
  }

  object Test {
    def apply(): Seq[ModuleID] = new TestDependencies {
      override lazy val test: Seq[ModuleID] = Seq(
        "uk.gov.hmrc"             %% "bootstrap-test-play-28" % "5.16.0"        % scope,
        "org.pegdown"              % "pegdown"            % "1.6.0"             % scope,
        "com.typesafe.play"       %% "play-test"          % PlayVersion.current % scope,
        "uk.gov.hmrc"             %% "reactivemongo-test" % "5.0.0-play-28"     % scope,
        "com.typesafe.akka"       %% "akka-testkit"       % "2.6.17"             % scope,
        "org.mockito"            %  "mockito-core"            % "4.0.0"          % scope,
        "org.mockito"            %% "mockito-scala"           % "1.16.46"        % scope,
        "org.mockito"            %% "mockito-scala-scalatest" % "1.16.46"        % scope,
        "org.scalatestplus"      %% "scalacheck-1-15"       % "3.2.10.0"        % scope,
        "com.fasterxml.jackson.module" %% "jackson-module-scala"   % "2.13.0"    % scope,
        "com.github.tomakehurst"   % "wiremock-jre8"      % "2.31.0"            % IntegrationTest withSources()
      )
    }.test
  }


  def apply(): Seq[ModuleID] = compile ++ Test()
}

import sbt._

object AppDependencies {

  import play.core.PlayVersion
  import play.sbt.PlayImport._

  val compile: Seq[ModuleID] = Seq(
    "uk.gov.hmrc.mongo" %% "hmrc-mongo-play-28"         % "0.70.0",
//    "com.typesafe.akka" %% "akka-actor"                 % "2.6.21",
//    "com.typesafe.akka" %% "akka-protobuf"              % "2.6.21",
//    "com.typesafe.akka" %% "akka-stream"                % "2.6.21",
//    "com.typesafe.akka" %% "akka-slf4j"                 % "2.6.21",
//    "com.typesafe.akka" %% "akka-actor-typed"           % "2.6.21",
//    "com.typesafe.akka" %% "akka-serialization-jackson" % "2.6.21",
//    ws,
    "uk.gov.hmrc"       %% "bootstrap-backend-play-28"  % "7.21.0",
    "uk.gov.hmrc"       %% "domain"                     % "8.3.0-play-28",
    "com.typesafe.play" %% "play-json-joda"             % "2.9.4"
  )

  trait TestDependencies {
    lazy val scope: String = "test,it"
    lazy val test: Seq[ModuleID] = ???
  }

  object Test {
    def apply(): Seq[ModuleID] = new TestDependencies {
      override lazy val test: Seq[ModuleID] = Seq(
        "uk.gov.hmrc"                  %% "bootstrap-test-play-28"    % "7.21.0"            % scope,
        "com.typesafe.play"            %% "play-test"                 % PlayVersion.current % scope,
        "uk.gov.hmrc.mongo"            %% "hmrc-mongo-test-play-28"   % "0.70.0"            % scope,
        "com.typesafe.akka"            %% "akka-testkit"              % "2.6.21"            % scope,
        "org.mockito"                  %  "mockito-core"              % "5.4.0"             % scope,
        "org.mockito"                  %% "mockito-scala"             % "1.17.14"           % scope,
        "org.mockito"                  %% "mockito-scala-scalatest"   % "1.17.14"           % scope,
        "org.scalatestplus"            %% "scalacheck-1-17"           % "3.2.16.0"          % scope,
        "com.fasterxml.jackson.module" %% "jackson-module-scala"      % "2.15.2"            % scope,
        "com.github.tomakehurst"       %  "wiremock-jre8"             % "2.35.0"            % IntegrationTest withSources()
      )
    }.test
  }

  def apply(): Seq[ModuleID] = compile ++ Test()
}

import sbt._

object AppDependencies {

  import play.core.PlayVersion

  val compile: Seq[ModuleID] = Seq(
    "uk.gov.hmrc.mongo" %% "hmrc-mongo-play-28"         % "1.3.0",
    "uk.gov.hmrc"       %% "bootstrap-backend-play-28"  % "7.22.0",
    "uk.gov.hmrc"       %% "domain"                     % "8.3.0-play-28"
  )

  trait TestDependencies {
    lazy val scope: String = "test,it"
    lazy val test: Seq[ModuleID] = ???
  }

  object Test {
    def apply(): Seq[ModuleID] = new TestDependencies {
      override lazy val test: Seq[ModuleID] = Seq(
        "uk.gov.hmrc"                  %% "bootstrap-test-play-28"    % "7.22.0"            % scope,
        "uk.gov.hmrc.mongo"            %% "hmrc-mongo-test-play-28"   % "1.3.0"             % scope,
        "com.typesafe.akka"            %% "akka-testkit"              % "2.6.21"            % scope,
        "org.mockito"                  %  "mockito-core"              % "5.6.0"             % scope,
        "org.mockito"                  %% "mockito-scala"             % "1.17.27"           % scope,
        "org.mockito"                  %% "mockito-scala-scalatest"   % "1.17.27"           % scope,
        "org.scalatestplus"            %% "scalacheck-1-17"           % "3.2.17.0"          % scope,
        "com.fasterxml.jackson.module" %% "jackson-module-scala"      % "2.15.3"            % scope,
        "com.github.tomakehurst"       %  "wiremock-jre8"             % "2.35.1"            % IntegrationTest withSources()
      )
    }.test
  }

  def apply(): Seq[ModuleID] = compile ++ Test()
}

import sbt._

object MicroServiceBuild extends Build with MicroService {

  val appName = "agent-client-mandate"

  override lazy val appDependencies: Seq[ModuleID] = AppDependencies()
}

private object AppDependencies {

  import play.sbt.PlayImport._
  import play.core.PlayVersion

  val compile = Seq(
    "uk.gov.hmrc" %% "simple-reactivemongo" % "7.19.0-play-25",
    ws,
    "uk.gov.hmrc" %% "microservice-bootstrap" % "10.6.0",
    "uk.gov.hmrc" %% "domain" % "5.6.0-play-25",
    "com.typesafe.akka" %% "akka-contrib" % "2.3.4",
    "uk.gov.hmrc" %% "mongo-lock" % "6.12.0-play-25",
    "uk.gov.hmrc" %% "play-scheduling" % "6.0.0",
    "uk.gov.hmrc" %% "auth-client" % "2.22.0-play-25"
  )

  trait TestDependencies {
    lazy val scope: String = "test"
    lazy val test: Seq[ModuleID] = ???
  }

  object Test {
    def apply() = new TestDependencies {
      override lazy val test = Seq(
        "uk.gov.hmrc" %% "hmrctest" % "3.9.0-play-25" % scope,
        "org.scalatest" %% "scalatest" % "3.0.5" % scope,
        "org.scalatestplus.play" %% "scalatestplus-play" % "2.0.1" % scope,
        "org.pegdown" % "pegdown" % "1.6.0" % scope,
        "com.typesafe.play" %% "play-test" % PlayVersion.current % scope,
        "uk.gov.hmrc" %% "reactivemongo-test" % "4.14.0-play-25" % scope,
        "com.typesafe.akka" % "akka-testkit_2.11" % "2.5.21" % scope,
        "org.mockito" % "mockito-core" % "2.24.5" % scope,
        "org.scalacheck" %% "scalacheck" % "1.14.0" % scope
      )
    }.test
  }


  def apply() = compile ++ Test()
}


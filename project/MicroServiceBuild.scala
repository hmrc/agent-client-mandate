import sbt._

object MicroServiceBuild extends Build with MicroService {

  val appName = "agent-client-mandate"

  override lazy val appDependencies: Seq[ModuleID] = AppDependencies()
}

private object AppDependencies {

  import play.sbt.PlayImport._
  import play.core.PlayVersion

  private val microserviceBootstrapVersion = "6.18.0"
  private val domainVersion = "5.2.0"
  private val hmrcTestVersion = "3.1.0"
  private val scalaTestVersion = "3.0.5"
  private val pegdownVersion = "1.6.0"
  private val akkaContribVersion = "2.3.4"
  private val playReactivemongoVersion = "5.2.0"
  private val reactivemongoTestVersion = "2.0.0"
  private val mockitoVersion = "1.9.0"
  private val scalatestPlusPlayVersion = "2.0.1"
  private val mongoLockVersion = "4.1.0"
  private val playSchedulingVersion = "4.1.0"
  private val authClientVersion = "2.16.0-play-25"


  val compile = Seq(
    "uk.gov.hmrc" %% "play-reactivemongo" % playReactivemongoVersion,
    ws,
    "uk.gov.hmrc" %% "microservice-bootstrap" % microserviceBootstrapVersion,
    "uk.gov.hmrc" %% "domain" % domainVersion,
    "com.typesafe.akka" %% "akka-contrib" % akkaContribVersion,
    "uk.gov.hmrc" %% "mongo-lock" % mongoLockVersion,
    "uk.gov.hmrc" %% "play-scheduling" % playSchedulingVersion,
    "com.kenshoo" %% "metrics-play" % "2.3.0_0.1.8",
    "com.codahale.metrics" % "metrics-graphite" % "3.0.2",
    "uk.gov.hmrc" %% "auth-client" % authClientVersion
  )

  trait TestDependencies {
    lazy val scope: String = "test"
    lazy val test: Seq[ModuleID] = ???
  }

  object Test {
    def apply() = new TestDependencies {
      override lazy val test = Seq(
        "uk.gov.hmrc" %% "hmrctest" % hmrcTestVersion % scope,
        "org.scalatest" %% "scalatest" % scalaTestVersion % scope,
        "org.scalatestplus.play" %% "scalatestplus-play" % scalatestPlusPlayVersion,
        "org.pegdown" % "pegdown" % pegdownVersion % scope,
        "com.typesafe.play" %% "play-test" % PlayVersion.current % scope,
        "uk.gov.hmrc" %% "reactivemongo-test" % reactivemongoTestVersion % scope,
        "com.typesafe.akka" % "akka-testkit_2.11" % akkaContribVersion % scope,
        "org.mockito" % "mockito-core" % mockitoVersion % scope
      )
    }.test
  }


  def apply() = compile ++ Test()
}


import sbt.*

private object AppDependencies {

  val compile: Seq[ModuleID] = Seq(
    "uk.gov.hmrc.mongo" %% "hmrc-mongo-play-30"        % "1.9.0",
    "uk.gov.hmrc"       %% "bootstrap-backend-play-30" % "8.6.0",
    "uk.gov.hmrc"       %% "domain-play-30"            % "9.0.0"
  )

  val test: Seq[ModuleID] = Seq(
    "uk.gov.hmrc"       %% "bootstrap-test-play-30" % "8.6.0",
    "org.apache.pekko"  %  "pekko-testkit_2.13"     % "1.0.2",
    "org.scalatestplus" %% "scalacheck-1-17"        % "3.2.18.0"
  ).map(_ % Test)

  val itDependencies: Seq[ModuleID] = Seq()

  def apply(): Seq[ModuleID] = compile ++ test
}
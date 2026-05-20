import sbt.*

object AppDependencies {
  private val mongoVersion = "2.12.0"
  private val bootstrapVersion = "10.7.0"

  val compile: Seq[ModuleID] = Seq(
    "uk.gov.hmrc.mongo"          %% "hmrc-mongo-play-30"        % mongoVersion,
    "uk.gov.hmrc"                %% "bootstrap-backend-play-30" % bootstrapVersion,
    "uk.gov.hmrc"                %% "domain-play-30"            % "13.0.0",
    "com.github.java-json-tools" %% "json-schema-validator"     % "2.2.14" cross CrossVersion.for3Use2_13,
  )

  val test: Seq[ModuleID] = Seq(
    "uk.gov.hmrc"             %%  "bootstrap-test-play-30"      % bootstrapVersion,
    "uk.gov.hmrc.mongo"       %%  "hmrc-mongo-test-play-30"     % mongoVersion,
    "org.scalatestplus"       %%  "scalacheck-1-17"             % "3.2.18.0"
  ).map(_ % Test)
}
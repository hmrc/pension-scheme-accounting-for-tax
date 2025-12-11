import sbt.*

object AppDependencies {
  private val mongoVersion = "2.11.0"
  private val bootstrapVersion = "10.4.0"

  val compile: Seq[ModuleID] = Seq(
    "uk.gov.hmrc.mongo"             %% "hmrc-mongo-play-30"                % mongoVersion,
    "uk.gov.hmrc"                   %% "bootstrap-backend-play-30"         % bootstrapVersion,
    "uk.gov.hmrc"                   %% "domain-play-30"                    % "12.1.0",
    "com.github.java-json-tools"    %% "json-schema-validator"             % "2.2.14" cross CrossVersion.for3Use2_13,
    "com.fasterxml.jackson.module"  %% "jackson-module-scala"              % "2.18.3"
  )

  val test: Seq[ModuleID] = Seq(
    "uk.gov.hmrc"             %%  "bootstrap-test-play-30"      % bootstrapVersion,
    "uk.gov.hmrc.mongo"       %%  "hmrc-mongo-test-play-30"     % mongoVersion,
    "com.vladsch.flexmark"    %   "flexmark-all"                % "0.64.8",
    "org.scalatest"           %%  "scalatest"                   % "3.2.19",
    "org.scalatestplus.play"  %%  "scalatestplus-play"          % "7.0.1",
    "org.scalatestplus"       %%  "mockito-4-6"                 % "3.2.15.0",
    "org.scalatestplus"       %%  "scalacheck-1-17"             % "3.2.18.0"
  ).map(_ % Test)
}
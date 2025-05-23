import sbt.*

object AppDependencies {
  private val mongoVersion = "2.6.0"
  private val bootstrapVersion = "9.11.0"

  val compile: Seq[ModuleID] = Seq(
    "uk.gov.hmrc.mongo"             %% "hmrc-mongo-play-30"                % mongoVersion,
    "uk.gov.hmrc"                   %% "bootstrap-backend-play-30"         % bootstrapVersion,
    "uk.gov.hmrc"                   %% "domain-play-30"                    % "12.0.0",
    "com.github.java-json-tools"    %% "json-schema-validator"             % "2.2.14" cross CrossVersion.for3Use2_13,
    "com.fasterxml.jackson.module"  %% "jackson-module-scala"              % "2.18.3"
  )

  val test: Seq[ModuleID] = Seq(
    "uk.gov.hmrc"             %%  "bootstrap-test-play-30"      % bootstrapVersion    % Test,
    "uk.gov.hmrc.mongo"       %%  "hmrc-mongo-test-play-30"     % mongoVersion        % Test,
    "com.vladsch.flexmark"    %   "flexmark-all"                % "0.64.8"            % Test,
    "org.scalatest"           %%  "scalatest"                   % "3.2.19"            % Test,
    "org.scalatestplus.play"  %%  "scalatestplus-play"          % "7.0.1"             % Test,
    "org.scalatestplus"       %%  "mockito-4-6"                 % "3.2.15.0"          % Test,
    "org.scalatestplus"       %%  "scalacheck-1-17"             % "3.2.18.0"          % Test,
    "org.pegdown"             %   "pegdown"                     % "1.6.0"             % Test
  )
}
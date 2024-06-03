import play.sbt.PlayImport.ehcache
import sbt._

object AppDependencies {
  val compile: Seq[ModuleID] = Seq(
    "uk.gov.hmrc.mongo"             %% "hmrc-mongo-play-30"                % "1.7.0",
    "uk.gov.hmrc"                   %% "bootstrap-backend-play-30"         % "8.4.0",
    "uk.gov.hmrc"                   %% "domain-play-30"                    % "9.0.0",
    "com.github.java-json-tools"    %% "json-schema-validator"             % "2.2.14",
    "com.fasterxml.jackson.module"  %% "jackson-module-scala"              % "2.16.1",
    ehcache
  )

  val test: Seq[ModuleID] = Seq(
    "uk.gov.hmrc"             %%  "bootstrap-test-play-30"      % "8.4.0"    % Test,
    "uk.gov.hmrc.mongo"       %%  "hmrc-mongo-test-play-30"     % "1.7.0"    % Test,
    "com.vladsch.flexmark"    %   "flexmark-all"                % "0.64.6"   % "test, it",
    "org.scalatest"           %%  "scalatest"                   % "3.2.15"   % Test,
    "org.scalatestplus.play"  %%  "scalatestplus-play"          % "5.1.0"    % Test,
    "org.scalatestplus"       %%  "mockito-4-6"                 % "3.2.15.0" % Test,
    "org.scalatestplus"       %%  "scalacheck-1-17"             % "3.2.15.0" % Test,
    "org.pegdown"             %   "pegdown"                     % "1.6.0"    % "test, it"
  )
}
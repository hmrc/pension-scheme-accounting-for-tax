import play.core.PlayVersion.current
import play.sbt.PlayImport.ehcache
import sbt._

object AppDependencies {

  val compile = Seq(

    "uk.gov.hmrc"             %% "simple-reactivemongo"       % "8.0.0-play-28",
    "uk.gov.hmrc"             %% "bootstrap-backend-play-28"  % "5.14.0",
    "uk.gov.hmrc"             %% "domain"                     % "6.2.0-play-28",
    "com.networknt"     %  "json-schema-validator"      % "1.0.3",
    ehcache
  )

  val test = Seq(
    "uk.gov.hmrc"             %% "bootstrap-test-play-28"   % "5.14.0"                % Test,
    "com.typesafe.play"       %% "play-test"                % current                 % Test,
    "com.vladsch.flexmark"    % "flexmark-all"              % "0.36.8"                % "test, it",
    "org.scalatestplus"       %% "scalatestplus-scalacheck" % "3.1.0.0-RC2"           % "test",
    "com.typesafe.play"       %% "play-cache"               % current                 % "test",
    "org.mockito"             % "mockito-core"              % "4.0.0"                 % "test",
    "org.mockito"             %% "mockito-scala"            % "1.16.42"               % "test",
    "org.mockito"             %% "mockito-scala-scalatest"  % "1.16.42"               % "test",
    "com.github.tomakehurst"  %  "wiremock"                 % "2.26.0"                % "test",
    "org.scalacheck"          %% "scalacheck"               % "1.14.0"                % "test",
    "org.pegdown"             %  "pegdown"                  % "1.6.0"                 % "test, it",
    "com.github.tomakehurst"  %  "wiremock-jre8"            % "2.26.0"                % "test",
    "com.github.simplyscala"  %% "scalatest-embedmongo"     % "0.2.4"                 % "test"
  )
}

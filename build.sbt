import play.sbt.routes.RoutesKeys
import scoverage.ScoverageKeys

import scala.collection.Seq

val appName = "pension-scheme-accounting-for-tax"


lazy val microservice = Project(appName, file("."))
  .disablePlugins(JUnitXmlReportPlugin)
  .enablePlugins(PlayScala, SbtDistributablesPlugin, SbtAutoBuildPlugin)
  .settings(
    majorVersion := 0,
    libraryDependencies ++= AppDependencies.compile ++ AppDependencies.test,
    scalaVersion := "3.0.0",
    scoverageSettings,
    RoutesKeys.routesImport ++= Seq(
      "models.enumeration.JourneyType",
      "models.enumeration.SchemeAdministratorType",
      "models.SchemeReferenceNumber"),
    PlayKeys.devSettings += "play.server.http.port" -> "8207",
    scalacOptions ++= Seq(
      "-feature",
      "-Xfatal-warnings",
      "-Wconf:src=routes/.*:silent", // Suppress warnings from routes files
      "-Wconf:src=twirl/.*:silent",  // Suppress warnings from twirl files
      "-Wconf:src=target/.*:silent", // Suppress warnings from target files
      "-Wconf:msg=Flag.*repeatedly:silent" // Suppress repeated flag warnings
    ),
    Test / fork := true,
    Test / javaOptions += "-Dconfig.file=conf/test.application.conf",
    resolvers += Resolver.jcenterRepo,
    Test / parallelExecution := true
  )

lazy val scoverageSettings = {
  Seq(
    ScoverageKeys.coverageExcludedFiles :=
      "<empty>;Reverse.*;.*repository.*;" + ".*BuildInfo.*;.*javascript.*;.*Routes.*;.*GuiceInjector;",
    ScoverageKeys.coverageMinimumStmtTotal := 80,
    ScoverageKeys.coverageFailOnMinimum := true,
    ScoverageKeys.coverageHighlighting := true
  )
}

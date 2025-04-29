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
    scalaVersion := "3.6.4",
    scoverageSettings,
    RoutesKeys.routesImport ++= Seq(
      "models.enumeration.JourneyType",
      "models.enumeration.SchemeAdministratorType",
      "models.SchemeReferenceNumber"),
    PlayKeys.devSettings += "play.server.http.port" -> "8207",
    scalacOptions ++= Seq(
      "-feature",
      "-Xfatal-warnings",                        // Treat all warnings as errors
      "-Wconf:src=target/.*:s",                  // silence warnings from compiled files
      "-Wconf:src=routes/.*:silent",             // Suppress warnings from routes files
      "-Wconf:msg=Flag.*repeatedly:silent",      // Suppress warnings for repeated flags
      "-Wconf:msg=.*-Wunused.*:silent",          // Suppress unused warnings
      "-Wconf:src=.*StartupModule\\.scala.*:silent", // Suppress warning about unused Environment as it's needed
      "-Wconf:cat=deprecation:silent" // Suppressing deprecation warn for Retrievals.name -< to be refactored
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

import play.sbt.routes.RoutesKeys
import scoverage.ScoverageKeys

val appName = "pension-scheme-accounting-for-tax"

lazy val microservice = Project(appName, file("."))
  .disablePlugins(JUnitXmlReportPlugin)
  .enablePlugins(PlayScala, SbtDistributablesPlugin)
  .settings(
    majorVersion := 0,
    libraryDependencies ++= AppDependencies.compile ++ AppDependencies.test,
    scalaVersion := "2.13.16",
    RoutesKeys.routesImport ++= Seq(
      "models.enumeration.JourneyType",
      "models.enumeration.SchemeAdministratorType",
      "models.SchemeReferenceNumber"),
    PlayKeys.devSettings += "play.server.http.port" -> "8207",
    scalacOptions += "-Wconf:src=routes/.*:s",
    Test / fork := true,
    Test / javaOptions += "-Dconfig.file=conf/test.application.conf",
    ScoverageKeys.coverageExcludedFiles := "<empty>;Reverse.*;.*repository.*;" +
      ".*BuildInfo.*;.*javascript.*;.*Routes.*;.*GuiceInjector;",
    ScoverageKeys.coverageMinimumStmtTotal := 80,
    ScoverageKeys.coverageFailOnMinimum := true,
    ScoverageKeys.coverageHighlighting := true,
    resolvers += Resolver.jcenterRepo,
    Test / parallelExecution := true
  )


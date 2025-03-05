import play.sbt.routes.RoutesKeys
import scoverage.ScoverageKeys
import uk.gov.hmrc.DefaultBuildSettings.integrationTestSettings

val appName = "pension-scheme-accounting-for-tax"

lazy val microservice = Project(appName, file("."))
  .disablePlugins(JUnitXmlReportPlugin)
  .enablePlugins(PlayScala, SbtDistributablesPlugin)
  .settings(
    majorVersion := 0,
    libraryDependencies ++= AppDependencies.compile ++ AppDependencies.test
  )
  .settings(scalaVersion := "2.13.12")
  .settings(
    RoutesKeys.routesImport ++= Seq(
      "models.enumeration.JourneyType",
      "models.enumeration.SchemeAdministratorType",
      "models.SchemeReferenceNumber"),
    PlayKeys.devSettings += "play.server.http.port" -> "8207",
    scalacOptions += "-Wconf:src=routes/.*:s",
    scalacOptions += "-Wconf:cat=unused-imports&src=routes/.*:s"
  )
  .configs(IntegrationTest)
  .settings(integrationTestSettings(): _*)
  .settings(
    Test / fork := true,
    Test / javaOptions += "-Dconfig.file=conf/test.application.conf"
  )
  .settings(
    ScoverageKeys.coverageExcludedFiles := "<empty>;Reverse.*;.*repository.*;" +
      ".*BuildInfo.*;.*javascript.*;.*Routes.*;.*GuiceInjector;",
    ScoverageKeys.coverageMinimumStmtTotal := 80,
    ScoverageKeys.coverageFailOnMinimum := true,
    ScoverageKeys.coverageHighlighting := true
  )
  .settings(resolvers += Resolver.jcenterRepo)
  .settings(
    Test / parallelExecution := true
  )


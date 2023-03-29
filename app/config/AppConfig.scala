/*
 * Copyright 2023 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package config

import play.api.Configuration
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import javax.inject.{Inject, Singleton}

@Singleton
class AppConfig @Inject()(config: Configuration, servicesConfig: ServicesConfig, runModeConfiguration: Configuration) {

  lazy val appName: String = config.get[String](path = "appName")
  val authBaseUrl: String = servicesConfig.baseUrl(serviceName = "auth")

  val auditingEnabled: Boolean = config.get[Boolean](path = "auditing.enabled")
  val graphiteHost: String = config.get[String](path = "microservice.metrics.graphite.host")

  private val ifURL: String = servicesConfig.baseUrl(serviceName = "if-hod")

  lazy val desEnvironment: String = runModeConfiguration.getOptional[String]("microservice.services.des-hod.env").getOrElse("local")
  lazy val authorization: String = "Bearer " + runModeConfiguration.getOptional[String]("microservice.services.des-hod.authorizationToken").getOrElse("local")

  val mongoDBAFTBatchesUserDataBatchSize: Int = config.get[Int](path = "mongodb.aft-cache.aft-batches.userDataBatchSize")
  val mongoDBAFTBatchesCollectionName: String = config.get[String](path = "mongodb.aft-cache.aft-batches.name")
  val mongoDBAFTBatchesTTL: Int = config.get[Int](path = "mongodb.aft-cache.aft-batches.timeToLiveInSeconds")
  val mongoDBAFTBatchesMaxTTL: Int = config.get[Int](path = "mongodb.aft-cache.aft-batches.maxTimeToLiveInSeconds")

  lazy val integrationframeworkEnvironment: String = runModeConfiguration.getOptional[String](
    path = "microservice.services.if-hod.env").getOrElse("local")
  lazy val integrationframeworkAuthorization: String = "Bearer " + runModeConfiguration.getOptional[String](
    path = "microservice.services.if-hod.authorizationToken").getOrElse("local")

  val fileAFTReturnURL: String = s"$ifURL${config.get[String](path = "serviceUrls.file-aft-return")}"

  def getAftOverviewUrl = s"$ifURL${config.get[String](path = "serviceUrls.get-aft-overview")}"

  def getAftDetailsUrl = s"$ifURL${config.get[String](path = "serviceUrls.get-aft-details")}"

  def getAftVersionUrl = s"$ifURL${config.get[String](path = "serviceUrls.get-aft-version")}"

  def psaFinancialStatementUrl = s"$ifURL${config.get[String](path = "serviceUrls.psa-financial-statement")}"

  def psaFinancialStatementMaxUrl = s"$ifURL${config.get[String](path = "serviceUrls.psa-financial-statement-max")}"

  def schemeFinancialStatementUrl = s"$ifURL${config.get[String](path = "serviceUrls.scheme-financial-statement")}"

  def schemeFinancialStatementMaxUrl = s"$ifURL${config.get[String](path = "serviceUrls.scheme-financial-statement-max")}"
}

/*
 * Copyright 2020 HM Revenue & Customs
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

import javax.inject.{Inject, Singleton}
import play.api.Configuration
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

@Singleton
class AppConfig @Inject()(config: Configuration, servicesConfig: ServicesConfig, runModeConfiguration: Configuration) {
  val authBaseUrl: String = servicesConfig.baseUrl(serviceName = "auth")

  val auditingEnabled: Boolean = config.get[Boolean](path = "auditing.enabled")
  val graphiteHost: String = config.get[String](path = "microservice.metrics.graphite.host")

  private val baseURL: String = servicesConfig.baseUrl(serviceName = "des-hod")
  val fileAFTReturnURL: String = s"$baseURL${config.get[String](path = "serviceUrls.file-aft-return")}"
  lazy val desEnvironment: String = runModeConfiguration.getOptional[String]("microservice.services.des-hod.env").getOrElse("local")
  lazy val authorization: String = "Bearer " + runModeConfiguration.getOptional[String]("microservice.services.des-hod.authorizationToken").getOrElse("local")

  def getAftDetailsUrl = s"$baseURL${config.get[String](path = "serviceUrls.get-aft-details")}"
  def getAftVersionUrl = s"$baseURL${config.get[String](path = "serviceUrls.get-aft-version")}"
}

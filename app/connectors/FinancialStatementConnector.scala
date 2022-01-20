/*
 * Copyright 2022 HM Revenue & Customs
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

package connectors

import audit.FinancialInfoAuditService
import com.google.inject.Inject
import config.AppConfig
import models.FeatureToggle.Enabled
import models.FeatureToggleName.FinancialInformationAFT
import models.{FeatureToggleName, PsaFS, SchemeFS}
import play.api.Logger
import play.api.http.Status._
import play.api.libs.json._
import play.api.mvc.RequestHeader
import services.FeatureToggleService
import uk.gov.hmrc.http.{HttpClient, _}
import utils.HttpResponseHelper

import scala.concurrent.{ExecutionContext, Future}

class FinancialStatementConnector @Inject()(
                                             http: HttpClient,
                                             config: AppConfig,
                                             headerUtils: HeaderUtils,
                                             financialInfoAuditService: FinancialInfoAuditService,
                                             featureToggleService : FeatureToggleService
                                           )
  extends HttpErrorFunctions with HttpResponseHelper {

  private val logger = Logger(classOf[FinancialStatementConnector])

  def getPsaFS(psaId: String)
              (implicit headerCarrier: HeaderCarrier, ec: ExecutionContext, request: RequestHeader): Future[Seq[PsaFS]] = {

    val url: String = config.psaFinancialStatementUrl.format(psaId)

    implicit val hc: HeaderCarrier = headerCarrier.withExtraHeaders(headers = headerUtils.integrationFrameworkHeader: _*)

    lazy val financialStatementsTransformer: Reads[JsArray] =
      __.read[JsArray].map {
        case JsArray(values) => JsArray(values.filterNot(charge =>
          (charge \ "chargeType").as[String].equals("00600100") || (charge \ "chargeType").as[String].equals("57962925")
        ))
      }

    http.GET[HttpResponse](url)(implicitly, hc, implicitly).map { response =>

      response.status match {
        case OK =>
          logger.debug(s"Ok response received from psaFinInfo api with body: ${response.body}")
          Json.parse(response.body).transform(financialStatementsTransformer) match {
            case JsSuccess(statements, _) =>
              statements.validate[Seq[PsaFS]](Reads.seq(PsaFS.rds)) match {
                case JsSuccess(values, _) =>
                  logger.debug(s"Response received from psaFinInfo api transformed successfully to $values")
                  values
                case JsError(errors) =>
                  throw JsResultException(errors)
              }
            case JsError(errors) =>
              throw JsResultException(errors)
          }
        case NOT_FOUND =>
          Seq.empty[PsaFS]
        case _ =>
          handleErrorResponse("GET", url)(response)
      }
    } andThen financialInfoAuditService.sendPsaFSAuditEvent(psaId)
  }

  //scalastyle:off cyclomatic.complexity
  private def transformSchemeFS(pstr: String, url: String, toggleValue:Boolean)(implicit
             hc: HeaderCarrier, ec: ExecutionContext, request: RequestHeader):Future[Seq[SchemeFS]] = {
println("\n>>>toggle=" + toggleValue)
    val reads: Reads[SchemeFS] = if (toggleValue) SchemeFS.rdsMax else SchemeFS.rds

    lazy val financialStatementsTransformer: Reads[JsArray] =
      __.read[JsArray].map {
        case JsArray(values) => JsArray(values.filterNot(charge =>
          (charge \ "chargeType").as[String].equals("00600100") || (charge \ "chargeType").as[String].equals("56962925")
        ))
      }
    http.GET[HttpResponse](url)(implicitly, hc, implicitly).map { response =>
      response.status match {
        case OK =>
          println("\n>>>>>>>>>" + response.body)
          logger.debug(s"Ok response received from schemeFinInfo api with body: ${response.body}")
          Json.parse(response.body).transform(financialStatementsTransformer) match {
            case JsSuccess(statements, _) =>
              println("\n>>>>HH")
              statements.validate[Seq[SchemeFS]](Reads.seq(reads)) match {
                case JsSuccess(values, _) =>
                  logger.debug(s"Response received from schemeFinInfo api transformed successfully to $values")
                  values
                case JsError(errors) =>
                  throw JsResultException(errors)
              }
            case JsError(errors) =>
              throw JsResultException(errors)
          }
        case NOT_FOUND =>
          Seq.empty[SchemeFS]
        case _ =>
          handleErrorResponse("GET", url)(response)
      }
    } andThen financialInfoAuditService.sendSchemeFSAuditEvent(pstr)
  }

  def getSchemeFS(pstr: String)
                 (implicit headerCarrier: HeaderCarrier, ec: ExecutionContext, request: RequestHeader): Future[Seq[SchemeFS]] = {

    val futureURL = featureToggleService.get(FinancialInformationAFT).map{
      case Enabled(_) => Tuple2(config.schemeFinancialStatementMaxUrl.format(pstr), true)
      case _ => Tuple2(config.schemeFinancialStatementUrl.format(pstr), false)
    }

    implicit val hc: HeaderCarrier = headerCarrier.withExtraHeaders(headers = headerUtils.integrationFrameworkHeader: _*)

    futureURL.flatMap{
      case (url, toggleValue) =>
      transformSchemeFS(pstr, url, toggleValue)(hc, implicitly, implicitly)
    }
  }
}

/*
 * Copyright 2024 HM Revenue & Customs
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
import models.{PsaFS, PsaFSDetail, SchemeFS, SchemeFSDetail}
import play.api.Logger
import play.api.http.Status._
import play.api.libs.json._
import play.api.mvc.RequestHeader
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.{HttpClient, _}
import utils.HttpResponseHelper

import scala.concurrent.duration.{Duration, SECONDS}
import scala.concurrent.{ExecutionContext, Future}

class FinancialStatementConnector @Inject()(
                                             http: HttpClient,
                                             httpClient2: HttpClientV2,
                                             config: AppConfig,
                                             headerUtils: HeaderUtils,
                                             financialInfoAuditService: FinancialInfoAuditService
                                           )
  extends HttpErrorFunctions with HttpResponseHelper {

  private val logger = Logger(classOf[FinancialStatementConnector])

  def getPsaFS(psaId: String)
              (implicit headerCarrier: HeaderCarrier, ec: ExecutionContext, request: RequestHeader): Future[PsaFS] = {

    implicit val hc: HeaderCarrier = headerCarrier.withExtraHeaders(headers = headerUtils.integrationFrameworkHeader: _*)

    transformPSAFS(psaId, config.psaFinancialStatementMaxUrl.format(psaId))(hc, implicitly, implicitly)
  }

  //scalastyle:off cyclomatic.complexity
  private def transformPSAFS(psaId: String, url: String)
                            (implicit hc: HeaderCarrier, ec: ExecutionContext, request: RequestHeader): Future[PsaFS] = {

    val reads: Reads[PsaFS] = PsaFS.rdsPsaFSMax

    http.GET[HttpResponse](url)(implicitly, hc, implicitly).map { response =>
      response.status match {
        case OK =>
          logger.debug(s"Ok response received from psaFinInfo api with body: ${response.body}")
          Json.parse(response.body).validate[PsaFS](reads) match {
            case JsSuccess(psaFS, _) =>
              logger.debug(s"Response received from psaFinInfo api transformed successfully to $psaFS")
              PsaFS(
                inhibitRefundSignal = psaFS.inhibitRefundSignal,
                seqPsaFSDetail = psaFS.seqPsaFSDetail.filterNot(charge => charge.chargeType.equals("Repayment Interest"))
              )
            case JsError(errors) =>
              throw JsResultException(errors)
          }
        case NOT_FOUND =>
          PsaFS(
            inhibitRefundSignal = false,
            seqPsaFSDetail = Seq.empty[PsaFSDetail]
          )
        case _ =>
          handleErrorResponse("GET", url)(response)
      }
    } andThen financialInfoAuditService.sendPsaFSAuditEvent(psaId)
  }

  def getSchemeFS(pstr: String)
                 (implicit headerCarrier: HeaderCarrier, ec: ExecutionContext, request: RequestHeader): Future[SchemeFS] = {
    implicit val hc: HeaderCarrier = headerCarrier.withExtraHeaders(headers = headerUtils.integrationFrameworkHeader: _*)

    val reads: Reads[SchemeFS] = SchemeFS.rdsSchemeFSMax

    val url = url"${config.schemeFinancialStatementMaxUrl.format(pstr)}" //this call is timing out/not responding to IFS


    httpClient2
              .get(url)(hc)
              .setHeader(headerUtils.integrationFrameworkHeader:_*)
      .transform(_.withRequestTimeout(Duration(40, SECONDS)))
              .execute[HttpResponse].map{ response =>
      response.status match {
        case OK =>
          logger.debug(s"Ok response received from schemeFinInfo api with body: ${response.body}")
          Json.parse(response.body).validate[SchemeFS](reads) match {
            case JsSuccess(schemeFS, _) =>
              logger.debug(s"Response received from schemeFinInfo api transformed successfully to $schemeFS")
              SchemeFS(
                inhibitRefundSignal = schemeFS.inhibitRefundSignal,
                seqSchemeFSDetail = schemeFS.seqSchemeFSDetail.filterNot(charge => charge.chargeType.equals("Repayment interest"))
              )
            case JsError(errors) =>
              throw JsResultException(errors)
          }
        case NOT_FOUND =>
          SchemeFS(
            inhibitRefundSignal = false,
            seqSchemeFSDetail = Seq.empty[SchemeFSDetail]
          )
        case _ =>
          handleErrorResponse("GET", url.toString)(response)
        }
      } andThen financialInfoAuditService.sendSchemeFSAuditEvent(pstr)


  }
}


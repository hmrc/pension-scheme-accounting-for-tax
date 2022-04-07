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
import models.{PsaFS, PsaFSDetail, SchemeFS, SchemeFSDetail, SourceChargeInfo}
import play.api.Logger
import play.api.http.Status._
import play.api.libs.json._
import play.api.mvc.RequestHeader
import services.FeatureToggleService
import uk.gov.hmrc.http.{HttpClient, _}
import utils.HttpResponseHelper

import java.time.LocalDate
import scala.concurrent.{ExecutionContext, Future}

class FinancialStatementConnector @Inject()(
                                             http: HttpClient,
                                             config: AppConfig,
                                             headerUtils: HeaderUtils,
                                             financialInfoAuditService: FinancialInfoAuditService,
                                             featureToggleService: FeatureToggleService,
                                             aftConnector: AFTConnector
                                           )
  extends HttpErrorFunctions with HttpResponseHelper {

  private val logger = Logger(classOf[FinancialStatementConnector])

  def getPsaFS(psaId: String)
              (implicit headerCarrier: HeaderCarrier, ec: ExecutionContext, request: RequestHeader): Future[PsaFS] = {

    val psaInfoUrl = featureToggleService.get(FinancialInformationAFT).map {
      case Enabled(_) => Tuple2(config.psaFinancialStatementMaxUrl.format(psaId), true)
      case _ => Tuple2(config.psaFinancialStatementUrl.format(psaId), false)
    }

    implicit val hc: HeaderCarrier = headerCarrier.withExtraHeaders(headers = headerUtils.integrationFrameworkHeader: _*)

    psaInfoUrl.flatMap {
      case (url, toggle) =>
        transformPSAFS(psaId, url, toggle)(hc, implicitly, implicitly)
    }
  }

  //scalastyle:off cyclomatic.complexity
  private def transformPSAFS(psaId: String, url: String, toggleValue: Boolean)
                            (implicit hc: HeaderCarrier, ec: ExecutionContext, request: RequestHeader): Future[PsaFS] = {

    val reads: Reads[PsaFS] = if (toggleValue) {
      PsaFS.rdsPsaFSMax
    } else {
      PsaFS.rdsPsaFSMedium
    }

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

    val futureURL = featureToggleService.get(FinancialInformationAFT).map {
      case Enabled(_) => Tuple2(config.schemeFinancialStatementMaxUrl.format(pstr), true)
      case _ => Tuple2(config.schemeFinancialStatementUrl.format(pstr), false)
    }
    implicit val hc: HeaderCarrier = headerCarrier.withExtraHeaders(headers = headerUtils.integrationFrameworkHeader: _*)

    futureURL.flatMap {
      case (url, toggleValue) =>
        transformSchemeFS(pstr, url, toggleValue)(hc, implicitly, implicitly)
    }
  }

  private def callAFTDetails(
                              pstr: String,
                              seqSchemeFSDetails: Seq[SchemeFSDetail],
                              toggleValue: Boolean)(implicit hc: HeaderCarrier, ec: ExecutionContext, request: RequestHeader): Future[Seq[SchemeFSDetail]] = {
    if (toggleValue) {
      Future.sequence(
        seqSchemeFSDetails.map { schemeFSDetail =>
          schemeFSDetail.sourceChargeInfo match {
            case Some(sci) =>
              sci.formBundleNumber match {
                case Some(fb) =>
                  aftConnector.getAftDetails(pstr, fb).map { jsValue =>
                    val optVersion = (jsValue \ "aftVersion").asOpt[Int]
                    val optReceiptDate = (jsValue \ "submitterDetails" \ "receiptDate").asOpt[LocalDate]
                    val newSourceChargeInfo = SourceChargeInfo(
                      index = sci.index,
                      formBundleNumber = sci.formBundleNumber,
                      version = optVersion,
                      receiptDate = optReceiptDate
                    )
                    schemeFSDetail copy (
                      sourceChargeInfo = Some(newSourceChargeInfo)
                      )
                  }
                case _ => Future.successful(schemeFSDetail)
              }
            case _ => Future.successful(schemeFSDetail)
          }
        }
      )
    } else {
      Future.successful(seqSchemeFSDetails)
    }
  }

  //scalastyle:off cyclomatic.complexity
  private def transformSchemeFS(pstr: String, url: String, toggleValue: Boolean)
                               (implicit hc: HeaderCarrier, ec: ExecutionContext, request: RequestHeader): Future[SchemeFS] = {

    val reads: Reads[SchemeFS] = if (toggleValue) {
      SchemeFS.rdsSchemeFSMax
    } else {
      SchemeFS.rdsSchemeFSMedium
    }

    http.GET[HttpResponse](url)(implicitly, hc, implicitly).flatMap { response =>
      response.status match {
        case OK =>
          logger.debug(s"Ok response received from schemeFinInfo api with body: ${response.body}")
          Json.parse(response.body).validate[SchemeFS](reads) match {
            case JsSuccess(schemeFS, _) =>
              logger.debug(s"Response received from schemeFinInfo api transformed successfully to $schemeFS")
              callAFTDetails(
                pstr,
                schemeFS.seqSchemeFSDetail.filterNot(charge => charge.chargeType.equals("Repayment interest")), toggleValue).map { f =>
                SchemeFS(
                  inhibitRefundSignal = schemeFS.inhibitRefundSignal,
                  seqSchemeFSDetail = f
                )
              }
            case JsError(errors) =>
              throw JsResultException(errors)
          }
        case NOT_FOUND =>
          Future.successful(
            SchemeFS(
              inhibitRefundSignal = false,
              seqSchemeFSDetail = Seq.empty[SchemeFSDetail]
            )
          )
        case _ =>
          handleErrorResponse("GET", url)(response)
      }
    } andThen financialInfoAuditService.sendSchemeFSAuditEvent(pstr)
  }
}


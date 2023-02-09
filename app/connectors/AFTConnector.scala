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

package connectors

import audit._
import com.google.inject.Inject
import config.AppConfig
import models.{AFTOverview, AFTVersion}
import play.api.Logger
import play.api.http.Status
import play.api.http.Status._
import play.api.libs.json._
import play.api.mvc.RequestHeader
import services.AFTService
import uk.gov.hmrc.http.{HttpClient, _}
import utils.HttpResponseHelper

import scala.concurrent.{ExecutionContext, Future}

class AFTConnector @Inject()(
                              http: HttpClient,
                              config: AppConfig,
                              auditService: AuditService,
                              fileAFTReturnAuditService: FileAFTReturnAuditService,
                              aftVersionsAuditEventService: GetAFTVersionsAuditService,
                              aftDetailsAuditEventService: GetAFTDetailsAuditService,
                              aftService: AFTService,
                              headerUtils: HeaderUtils
                            )
  extends HttpErrorFunctions
    with HttpResponseHelper {

  private val logger = Logger(classOf[AFTConnector])

  def fileAFTReturn(pstr: String, journeyType: String, data: JsValue)
                   (implicit headerCarrier: HeaderCarrier, ec: ExecutionContext, request: RequestHeader): Future[HttpResponse] = {
    val fileAFTReturnURL = config.fileAFTReturnURL.format(pstr)
    logger.warn("File AFT return (IF) called - URL:" + fileAFTReturnURL)
    implicit val hc: HeaderCarrier = headerCarrier.withExtraHeaders(headers = integrationFrameworkHeader: _*)
    if (aftService.isChargeZeroedOut(data)) {
      http.POST[JsValue, HttpResponse](fileAFTReturnURL, data)(implicitly, implicitly, hc, implicitly) map {
        response =>
          response.status match {
            case OK => response
            case _ => handleErrorResponse("POST", fileAFTReturnURL, journeyType)(response)
          }
      } andThen
        fileAFTReturnAuditService.sendFileAFTReturnAuditEvent(pstr, journeyType, data) andThen
        fileAFTReturnAuditService.sendFileAFTReturnWhereOnlyOneChargeWithNoValueAuditEvent(pstr, journeyType, data)
    } else {
      http.POST[JsValue, HttpResponse](fileAFTReturnURL, data)(implicitly, implicitly, hc, implicitly) map {
        response =>
          response.status match {
            case OK => response
            case _ => handleErrorResponse("POST", fileAFTReturnURL, journeyType)(response)
          }
      } andThen
        fileAFTReturnAuditService.sendFileAFTReturnAuditEvent(pstr, journeyType, data)
    }
  }

  //scalastyle:off cyclomatic.complexity
  def getAftOverview(pstr: String, startDate: String, endDate: String)
                    (implicit headerCarrier: HeaderCarrier, ec: ExecutionContext): Future[Seq[AFTOverview]] = {

    val getAftVersionUrl: String = config.getAftOverviewUrl.format(pstr, startDate, endDate)

    logger.warn("Get overview (IF) called - URL:" + getAftVersionUrl)

    implicit val hc: HeaderCarrier = headerCarrier.withExtraHeaders(headers = integrationFrameworkHeader: _*)

    http.GET[HttpResponse](getAftVersionUrl)(implicitly, hc, implicitly).map { response =>
      response.status match {
        case OK =>
          Json.parse(response.body).validate[Seq[AFTOverview]](Reads.seq(AFTOverview.rds)) match {
            case JsSuccess(versions, _) => versions
            case JsError(errors) => throw JsResultException(errors)
          }
        case NOT_FOUND =>
          val singleError = (Json.parse(response.body) \ "code").asOpt[String]
          val multipleError = (Json.parse(response.body) \ "failures").asOpt[JsArray]
          (singleError, multipleError) match {
            case (Some(err), _) if err.equals("NO_REPORT_FOUND") =>
              logger.info("The remote endpoint has indicated No Scheme report was found for the given period.")
              Seq.empty[AFTOverview]
            case (_, Some(seqErr)) =>
              val isAnyNoReportFound = seqErr.value.exists(jsValue => (jsValue \ "code").asOpt[String].contains("NO_REPORT_FOUND"))
              if (isAnyNoReportFound) {
                logger.info("The remote endpoint has indicated No Scheme report was found for the given period.")
                Seq.empty[AFTOverview]
              } else {
                handleErrorResponse("GET", getAftVersionUrl)(response)
              }
            case _ => handleErrorResponse("GET", getAftVersionUrl)(response)
          }
        case _ =>
          handleErrorResponse("GET", getAftVersionUrl)(response)
      }
    }
  }

  def getAftDetails(pstr: String, startDate: String, aftVersion: String)
                   (implicit headerCarrier: HeaderCarrier, ec: ExecutionContext, request: RequestHeader): Future[JsValue] = {

    val getAftUrl: String = config.getAftDetailsUrl.format(pstr, startDate, aftVersion)
    implicit val hc: HeaderCarrier = headerCarrier.withExtraHeaders(headers = integrationFrameworkHeader: _*)

    http.GET[HttpResponse](getAftUrl)(implicitly, hc, implicitly) map {
      response =>
        response.status match {
          case OK => Json.parse(response.body)
          case _ => handleErrorResponse("GET", getAftUrl)(response)
        }
    } andThen aftDetailsAuditEventService.sendAFTDetailsAuditEvent(pstr, startDate)
  }

  def getAftDetails(pstr: String, fbNumber: String)
                   (implicit headerCarrier: HeaderCarrier, ec: ExecutionContext, request: RequestHeader): Future[Option[JsValue]] = {

    val getAftUrl: String = config.getAftFbnDetailsUrl.format(pstr)
    val headers = integrationFrameworkHeader :+ "fbNumber" -> fbNumber
    implicit val hc: HeaderCarrier = headerCarrier.withExtraHeaders(headers = headers: _*)

    http.GET[HttpResponse](getAftUrl)(implicitly, hc, implicitly) map {
      response =>
        response.status match {
          case OK => Some(Json.parse(response.body))
          case NOT_FOUND => None
          case _ => handleErrorResponse("GET", getAftUrl)(response)
        }
    } andThen aftDetailsAuditEventService.sendOptionAFTDetailsAuditEvent(pstr, fbNumber)
  }

  def getAftVersions(pstr: String, startDate: String)
                    (implicit headerCarrier: HeaderCarrier, ec: ExecutionContext, request: RequestHeader): Future[Seq[AFTVersion]] = {

    val getAftVersionUrl: String = config.getAftVersionUrl.format(pstr, startDate)
    implicit val hc: HeaderCarrier = headerCarrier.withExtraHeaders(headers = integrationFrameworkHeader: _*)

    http.GET[HttpResponse](getAftVersionUrl)(implicitly, hc, implicitly).map {
      response =>
        response.status match {
          case OK =>
            auditService.sendEvent(GetAFTVersions(pstr, startDate, Status.OK, Some(Json.parse(response.body))))

            Json.parse(response.body).validate[Seq[AFTVersion]](Reads.seq(AFTVersion.rds)) match {
              case JsSuccess(versions, _) => versions
              case JsError(errors) => throw JsResultException(errors)
            }
          case _ =>
            handleErrorResponse("GET", getAftVersionUrl)(response)
        }
    } andThen aftVersionsAuditEventService.sendAFTVersionsAuditEvent(pstr, startDate)
  }

  private def integrationFrameworkHeader: Seq[(String, String)] = {
    Seq("Environment" -> config.integrationframeworkEnvironment,
      "Authorization" -> config.integrationframeworkAuthorization,
      "Content-Type" -> "application/json", "CorrelationId" -> headerUtils.getCorrelationId)
  }
}

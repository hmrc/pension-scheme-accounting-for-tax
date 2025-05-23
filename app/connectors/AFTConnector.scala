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

import audit._
import com.google.inject.Inject
import config.AppConfig
import models.{AFTOverview, AFTVersion}
import play.api.Logger
import play.api.http.Status
import play.api.http.Status._
import play.api.libs.json._
import play.api.libs.ws.JsonBodyWritables.writeableOf_JsValue
import play.api.mvc.RequestHeader
import services.AFTService
import uk.gov.hmrc.http._
import uk.gov.hmrc.http.client.HttpClientV2
import utils.HttpResponseHelper

import java.net.URL
import scala.concurrent.{ExecutionContext, Future}

class AFTConnector @Inject()(
                              httpClient2: HttpClientV2,
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
    val fileAFTReturnURL = url"${config.fileAFTReturnURL.format(pstr)}"
    logger.debug("File AFT return (IF) called - URL:" + fileAFTReturnURL)
    val header = integrationFrameworkHeader
    if (aftService.isChargeZeroedOut(data)) {
      httpPostRequest(fileAFTReturnURL, data, journeyType, header) andThen
        fileAFTReturnAuditService.sendFileAFTReturnAuditEvent(pstr, journeyType, data) andThen
        fileAFTReturnAuditService.sendFileAFTReturnWhereOnlyOneChargeWithNoValueAuditEvent(pstr, journeyType, data)
    } else {
      httpPostRequest(fileAFTReturnURL, data, journeyType, header) andThen
        fileAFTReturnAuditService.sendFileAFTReturnAuditEvent(pstr, journeyType, data)
    }
  }

  private def httpPostRequest(url: URL, data: JsValue, journeyType: String, header: Seq[(String, String)]) (implicit hc: HeaderCarrier, ec: ExecutionContext) =
    httpClient2.post(url).withBody(data).setHeader(header *).execute[HttpResponse] map {
      response =>
        response.status match {
          case OK => response
          case FORBIDDEN if response.body.contains("RETURN_ALREADY_SUBMITTED") => response
          case _ => handleErrorResponse("POST", url.toString, journeyType)(response)
        }
    }

  private def integrationFrameworkHeader: Seq[(String, String)] = {
    Seq("Environment" -> config.integrationframeworkEnvironment,
      "Authorization" -> config.integrationframeworkAuthorization,
      "Content-Type" -> "application/json", "CorrelationId" -> headerUtils.getCorrelationId)
  }

  //scalastyle:off cyclomatic.complexity
  def getAftOverview(pstr: String, startDate: String, endDate: String)
                    (implicit headerCarrier: HeaderCarrier, ec: ExecutionContext): Future[Seq[AFTOverview]] = {

    val getAftVersionUrl = url"${config.getAftOverviewUrl.format(pstr, startDate, endDate)}"

    logger.debug("Get overview (IF) called - URL:" + getAftVersionUrl)

    httpClient2.get(getAftVersionUrl).setHeader(integrationFrameworkHeader *).execute[HttpResponse].map { response =>
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
                handleErrorResponse("GET", getAftVersionUrl.toString)(response)
              }
            case _ => handleErrorResponse("GET", getAftVersionUrl.toString)(response)
          }
        case _ =>
          handleErrorResponse("GET", getAftVersionUrl.toString)(response)
      }
    }
  }

  def getAftDetails(pstr: String, startDate: String, aftVersion: String)
                   (implicit headerCarrier: HeaderCarrier, ec: ExecutionContext, request: RequestHeader): Future[JsValue] = {

    val getAftUrl = url"${config.getAftDetailsUrl.format(pstr)}"
    val headers: Seq[(String, String)] = integrationFrameworkHeader :+ "quarterStartDate" -> startDate :+ "aftVersion" -> aftVersion

    logger.debug(s"GET AFT DETAILS CALLED (IF): aftVersion: $aftVersion and full headers: $headers")

    implicit val hc: HeaderCarrier = headerCarrier.withExtraHeaders(headers = headers *)


    logger.debug(s"GET AFT DETAILS CALLED (IF): HC other headers = " +
      s"${hc.otherHeaders} and HC extra headers = ${hc.extraHeaders} and request headers = ${request.headers}")

    logger.warn(s"getAftDetails from (IF) started for version: $aftVersion")
    val res = httpClient2
      .get(getAftUrl)(hc)
      .setHeader(headers *)
      .transform(_.withRequestTimeout(config.ifsTimeout))
      .execute[HttpResponse].map{ response =>
        response.status match {
          case OK => Json.parse(response.body)
          case _ => handleErrorResponse("GET", getAftUrl.toString)(response)
        }
      } andThen aftDetailsAuditEventService.sendAFTDetailsAuditEvent(pstr, startDate)
    logger.warn(s"getAftDetails from (IF) finished for version: $aftVersion")
    res
  }

  def getAftDetails(pstr: String, fbNumber: String)
                   (implicit headerCarrier: HeaderCarrier, ec: ExecutionContext, request: RequestHeader): Future[Option[JsValue]] = {

    val getAftUrl = url"${config.getAftDetailsUrl.format(pstr)}"
    val headers = integrationFrameworkHeader :+ "fbNumber" -> fbNumber
    implicit val hc: HeaderCarrier = headerCarrier.withExtraHeaders(headers = headers *)
    httpClient2
      .get(getAftUrl)(hc)
      .setHeader(headers *)
      .transform(_.withRequestTimeout(config.ifsTimeout))
      .execute[HttpResponse].map {
      response =>
        response.status match {
          case OK => Some(Json.parse(response.body))
          case NOT_FOUND | FORBIDDEN => None
          case _ => handleErrorResponse("GET", getAftUrl.toString)(response)
        }
    } andThen aftDetailsAuditEventService.sendOptionAFTDetailsAuditEvent(pstr, fbNumber)
  }

  def getAftVersions(pstr: String, startDate: String)
                    (implicit headerCarrier: HeaderCarrier, ec: ExecutionContext, request: RequestHeader): Future[Seq[AFTVersion]] = {

    val getAftVersionUrl = url"${config.getAftVersionUrl.format(pstr, startDate)}"

    logger.warn(s"getAftVersions from (IF) started")
    val res = httpClient2.get(getAftVersionUrl).setHeader(integrationFrameworkHeader *).execute[HttpResponse].map {
      response =>
        response.status match {
          case OK =>
            auditService.sendEvent(GetAFTVersions(pstr, startDate, Status.OK, Some(Json.parse(response.body))))

            Json.parse(response.body).validate[Seq[AFTVersion]](Reads.seq(AFTVersion.rds)) match {
              case JsSuccess(versions, _) => versions
              case JsError(errors) => throw JsResultException(errors)
            }
          case _ =>
            handleErrorResponse("GET", getAftVersionUrl.toString)(response)
        }
    } andThen aftVersionsAuditEventService.sendAFTVersionsAuditEvent(pstr, startDate)
    logger.warn(s"getAftVersions from (IF) finished")
    res
  }
}

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

package connectors

import java.util.UUID.randomUUID

import audit._
import com.google.inject.Inject
import config.AppConfig
import models.AFTVersion
import models.AFTOverview
import play.Logger
import play.api.http.Status
import play.api.libs.json.{JsError, JsResultException, JsSuccess, JsValue, Reads}
import play.api.mvc.RequestHeader
import services.AFTService
import uk.gov.hmrc.http._
import uk.gov.hmrc.play.bootstrap.http.HttpClient

import scala.concurrent.{ExecutionContext, Future}

class DesConnector @Inject()(http: HttpClient, config: AppConfig, auditService: AuditService,
                             fileAFTReturnAuditService: FileAFTReturnAuditService,
                             aftVersionsAuditEventService: GetAFTVersionsAuditService,
                             aftDetailsAuditEventService: GetAFTDetailsAuditService,
                             aftService: AFTService) extends HttpErrorFunctions {

  def fileAFTReturn(pstr: String, data: JsValue)
                   (implicit headerCarrier: HeaderCarrier, ec: ExecutionContext, request: RequestHeader): Future[HttpResponse] = {
    val fileAFTReturnURL = config.fileAFTReturnURL.format(pstr)

    implicit val hc: HeaderCarrier = HeaderCarrier(extraHeaders = desHeader(implicitly[HeaderCarrier](headerCarrier)))

    if (aftService.isOnlyOneChargeWithNoValue(data)) {
      http.POST[JsValue, HttpResponse](fileAFTReturnURL, data)(implicitly, implicitly, hc, implicitly) andThen
        fileAFTReturnAuditService.sendFileAFTReturnAuditEvent(pstr, data) andThen
        fileAFTReturnAuditService.sendFileAFTReturnWhereOnlyOneChargeWithNoValueAuditEvent(pstr, data)
    } else {
      http.POST[JsValue, HttpResponse](fileAFTReturnURL, data)(implicitly, implicitly, hc, implicitly) andThen
        fileAFTReturnAuditService.sendFileAFTReturnAuditEvent(pstr, data)
    }
  }

  def getAftDetails(pstr: String, startDate: String, aftVersion: String)
                   (implicit headerCarrier: HeaderCarrier, ec: ExecutionContext, request: RequestHeader): Future[JsValue] = {

    val getAftUrl: String = config.getAftDetailsUrl.format(pstr, startDate, aftVersion)
    implicit val hc: HeaderCarrier = HeaderCarrier(extraHeaders = desHeader(implicitly[HeaderCarrier](headerCarrier)))

    http.GET[JsValue](getAftUrl)(implicitly, hc, implicitly) andThen
      aftDetailsAuditEventService.sendAFTDetailsAuditEvent(pstr, startDate)
  }

  def getAftVersions(pstr: String, startDate: String)
                         (implicit headerCarrier: HeaderCarrier, ec: ExecutionContext, request: RequestHeader): Future[Seq[AFTVersion]] = {

    val getAftVersionUrl: String = config.getAftVersionUrl.format(pstr, startDate)
    implicit val hc: HeaderCarrier = HeaderCarrier(extraHeaders = desHeader(implicitly[HeaderCarrier](headerCarrier)))

    http.GET[JsValue](getAftVersionUrl)(implicitly, hc, implicitly).map { responseJson =>
      auditService.sendEvent(GetAFTVersions(pstr, startDate, Status.OK, Some(responseJson)))

      responseJson.validate[Seq[AFTVersion]](Reads.seq(AFTVersion.rds)) match {
        case JsSuccess(versions, _) => versions
        case JsError(errors) => throw JsResultException(errors)
      }
    }
  } andThen aftVersionsAuditEventService.sendAFTVersionsAuditEvent(pstr, startDate) recoverWith {
    case _: NotFoundException => Future.successful(Nil)
  }

  def getAftOverview(pstr: String, startDate: String, endDate: String)
                    (implicit headerCarrier: HeaderCarrier, ec: ExecutionContext, request: RequestHeader): Future[Seq[AFTOverview]] = {

    val getAftVersionUrl: String = config.getAftOverviewUrl.format(pstr, startDate, endDate)
    implicit val hc: HeaderCarrier = HeaderCarrier(extraHeaders = desHeader(implicitly[HeaderCarrier](headerCarrier)))

    http.GET[JsValue](getAftVersionUrl)(implicitly, hc, implicitly).map { responseJson =>

      responseJson.validate[Seq[AFTOverview]](Reads.seq(AFTOverview.rds)) match {
        case JsSuccess(versions, _) => versions
        case JsError(errors) => throw JsResultException(errors)
      }
    }
  } recoverWith {
    case _: NotFoundException => Future.successful(Nil)
  }

  private def desHeader(implicit hc: HeaderCarrier): Seq[(String, String)] = {
    val requestId = getCorrelationId(hc.requestId.map(_.value))

    Seq("Environment" -> config.desEnvironment, "Authorization" -> config.authorization,
      "Content-Type" -> "application/json", "CorrelationId" -> requestId)
  }

  def getCorrelationId(requestId: Option[String]): String = {
    requestId.getOrElse {
      Logger.error("No Request Id found")
      randomUUID.toString
    }.replaceAll("(govuk-tax-)", "").slice(0, 36)
  }
}

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

package audit

import com.google.inject.Inject
import play.api.http.Status
import play.api.libs.json.{JsObject, JsValue, Json}
import play.api.mvc.RequestHeader
import uk.gov.hmrc.http.{HttpException, UpstreamErrorResponse}

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success, Try}

class GetAFTDetailsAuditService @Inject()(auditService: AuditService) {

  type OptionalResultEvent = PartialFunction[Try[Option[JsValue]], Unit]
  type MandatoryResultEvent = PartialFunction[Try[JsValue], Unit]

  def sendAFTDetailsAuditEvent(pstr: String, startDate: String)
                              (implicit ec: ExecutionContext, request: RequestHeader): MandatoryResultEvent = {
    case Success(response) =>
      sendEvent(pstr, startDate, Status.OK, Some(response))
    case Failure(error: UpstreamErrorResponse) =>
      sendEvent(pstr, startDate, error.statusCode, None)
    case Failure(error: HttpException) =>
      sendEvent(pstr, startDate, error.responseCode, None)
  }

  def sendOptionAFTDetailsAuditEvent(pstr: String, startDate: String)
                                    (implicit ec: ExecutionContext, request: RequestHeader): OptionalResultEvent = {
    case Success(response) =>
      sendEvent(pstr, startDate, Status.OK, response)
    case Failure(error: UpstreamErrorResponse) =>
      sendEvent(pstr, startDate, error.statusCode, None)
    case Failure(error: HttpException) =>
      sendEvent(pstr, startDate, error.responseCode, None)
  }

  private def sendEvent(pstr: String, startDate: String, status: Int, response: Option[JsValue])
                       (implicit ec: ExecutionContext, request: RequestHeader): Unit = {
    auditService.sendEvent(GetAFTDetails(
      pstr,
      startDate,
      status,
      response
    ))
  }
}

case class GetAFTDetails(
                          pstr: String,
                          startDate: String,
                          status: Int,
                          response: Option[JsValue]
                        ) extends AuditEvent {

  override def auditType: String = "AFTGet"

  override def details: JsObject = Json.obj(
    "pstr" -> pstr,
    "quarterStartDate" -> startDate,
    "aftStatus" -> response.flatMap(res => (res \ "aftDetails" \ "aftStatus").asOpt[String]),
    "status" -> status.toString,
    "response" -> response
  )
}



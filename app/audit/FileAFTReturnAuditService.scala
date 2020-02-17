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

package audit

import com.google.inject.Inject
import play.api.http.Status
import play.api.libs.json.{Format, JsValue, Json}
import play.api.mvc.RequestHeader
import uk.gov.hmrc.http.{HttpException, HttpResponse, UpstreamErrorResponse}

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success, Try}

class FileAFTReturnAuditService @Inject()(auditService: AuditService) {

  def sendFileAFTReturnAuditEvent(pstr: String, data: JsValue)
                                 (implicit ec: ExecutionContext, request: RequestHeader): PartialFunction[Try[HttpResponse], Unit] = {
    case Success(httpResponse) =>
      auditService.sendEvent(FileAftReturn(pstr, Status.OK, data, Some(httpResponse.json)))
    case Failure(error: UpstreamErrorResponse) =>
      auditService.sendEvent(FileAftReturn(pstr, error.upstreamResponseCode, data, None))
    case Failure(error: HttpException) =>
      auditService.sendEvent(FileAftReturn(pstr, error.responseCode, data, None))
  }

  def sendFileAFTReturnWhereOnlyOneChargeWithOneMemberAndNoValueAuditEvent(pstr: String, data: JsValue)
                                 (implicit ec: ExecutionContext, request: RequestHeader): PartialFunction[Try[HttpResponse], Unit] = {
    case Success(httpResponse) =>
      auditService.sendEvent(FileAFTReturnWhereOnlyOneChargeWithOneMemberAndNoValue(pstr, Status.OK, data, Some(httpResponse.json)))
    case Failure(error: UpstreamErrorResponse) =>
      auditService.sendEvent(FileAFTReturnWhereOnlyOneChargeWithOneMemberAndNoValue(pstr, error.upstreamResponseCode, data, None))
    case Failure(error: HttpException) =>
      auditService.sendEvent(FileAFTReturnWhereOnlyOneChargeWithOneMemberAndNoValue(pstr, error.responseCode, data, None))
  }
}

case class FileAftReturn(
                          pstr: String,
                          status: Int,
                          request: JsValue,
                          response: Option[JsValue]
                        ) extends AuditEvent {
  override def auditType: String = "AftPost"

  override def details: Map[String, String] = Map(
    "pstr" -> pstr,
    "quarterStartDate" -> (request \ "aftDetails" \ "quarterStartDate").asOpt[String].getOrElse(""),
    "aftStatus" -> (request \ "aftDetails" \ "aftStatus").asOpt[String].getOrElse(""),
    "status" -> status.toString,
    "request" -> Json.stringify(request),
    "response" -> {
      response match {
        case Some(json) => Json.stringify(json)
        case _ => ""
      }
    }
  )
}

case class FileAFTReturnWhereOnlyOneChargeWithOneMemberAndNoValue(
                          pstr: String,
                          status: Int,
                          request: JsValue,
                          response: Option[JsValue]
                        ) extends AuditEvent {
  override def auditType: String = "AftPostWhereOnlyOneChargeWithOneMemberAndNoValue"

  override def details: Map[String, String] = Map(
    "pstr" -> pstr,
    "quarterStartDate" -> (request \ "aftDetails" \ "quarterStartDate").asOpt[String].getOrElse(""),
    "aftStatus" -> (request \ "aftDetails" \ "aftStatus").asOpt[String].getOrElse(""),
    "status" -> status.toString,
    "request" -> Json.stringify(request),
    "response" -> {
      response match {
        case Some(json) => Json.stringify(json)
        case _ => ""
      }
    }
  )
}

object FileAftReturn {
  implicit val formats: Format[FileAftReturn] = Json.format[FileAftReturn]
}

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

package audit

import com.google.inject.Inject
import play.api.http.Status
import play.api.libs.json._
import play.api.mvc.RequestHeader
import uk.gov.hmrc.http.{HttpException, HttpResponse, UpstreamErrorResponse}

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success, Try}

class FileAFTReturnAuditService @Inject()(auditService: AuditService) {

  def sendFileAFTReturnAuditEvent(pstr: String, journeyType: String, data: JsValue)
                                 (implicit ec: ExecutionContext, request: RequestHeader): PartialFunction[Try[HttpResponse], Unit] = {
    case Success(httpResponse) =>
      auditService.sendEvent(FileAftReturn(pstr, journeyType, Status.OK, data, Some(httpResponse.json)))
    case Failure(error: UpstreamErrorResponse) =>
      auditService.sendEvent(FileAftReturn(pstr, journeyType, error.statusCode, data, None))
    case Failure(error: HttpException) =>
      auditService.sendEvent(FileAftReturn(pstr, journeyType, error.responseCode, data, None))
  }

  def sendFileAFTReturnWhereOnlyOneChargeWithNoValueAuditEvent(pstr: String, journeyType: String, data: JsValue)
                                                              (implicit ec: ExecutionContext,
                                                               request: RequestHeader): PartialFunction[Try[HttpResponse], Unit] = {
    case Success(httpResponse) =>
      auditService.sendEvent(FileAFTReturnOneChargeAndNoValue(pstr, journeyType, Status.OK, data, Some(httpResponse.json)))
    case Failure(error: UpstreamErrorResponse) =>
      auditService.sendEvent(FileAFTReturnOneChargeAndNoValue(pstr, journeyType, error.statusCode, data, None))
    case Failure(error: HttpException) =>
      auditService.sendEvent(FileAFTReturnOneChargeAndNoValue(pstr, journeyType, error.responseCode, data, None))
  }

  def sendFileAftReturnSchemaValidatorAuditEvent(psaOrPspId: String, pstr: String, chargeType: String, data: JsValue,
                                                 failureResponse: String, numberOfFailures: Int)
                                                (implicit ec: ExecutionContext, request: RequestHeader): Unit = {
    auditService.sendEvent(FileAftReturnSchemaValidator(psaOrPspId, pstr, chargeType, data,
      failureResponse, numberOfFailures))
  }
}

case class FileAftReturn(
                          pstr: String,
                          journeyType: String,
                          status: Int,
                          request: JsValue,
                          response: Option[JsValue]
                        ) extends AuditEvent {
  override def auditType: String = "AFTPost"

  override def details: JsObject = Json.obj(
    "pstr" -> pstr,
    "quarterStartDate" -> (request \ "aftDetails" \ "quarterStartDate").asOpt[String],
    "aftStatus" -> journeyType,
    "status" -> status.toString,
    "request" -> request,
    "response" -> response
  )
}

case class FileAftReturnSchemaValidator(
                                         psaOrPspId: String,
                                         pstr: String,
                                         chargeType: String,
                                         request: JsValue,
                                         failureResponse: String,
                                         numberOfFailures: Int
                                       ) extends AuditEvent {
  override def auditType: String = "AFTSchemaValidationPostCheck"

  override def details: JsObject = Json.obj(
    "psaOrPspId" -> psaOrPspId,
    "pstr" -> pstr,
    "chargeType" -> chargeType,
    "request" -> request,
    "failureResponse" -> failureResponse,
    "numberOfFailures" -> numberOfFailures.toString
  )
}

case class FileAFTReturnOneChargeAndNoValue(
                                             pstr: String,
                                             journeyType: String,
                                             status: Int,
                                             request: JsValue,
                                             response: Option[JsValue]
                                           ) extends AuditEvent {
  override def auditType: String = "AFTPostOneChargeWithNoValue"

  override def details: JsObject = Json.obj(
    "pstr" -> pstr,
    "quarterStartDate" -> (request \ "aftDetails" \ "quarterStartDate").asOpt[String],
    "aftStatus" -> journeyType,
    "status" -> status.toString,
    "request" -> request,
    "response" -> response,
    "requestSizeInBytes" -> request.toString().getBytes.length      //TODO: This audit event may not be triggered due to frontend validation of non-zero Total Amount, but it is being retained for potential future use cases.
  )
}

object FileAftReturn {
  implicit val formats: Format[FileAftReturn] = Json.format[FileAftReturn]
}

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
import play.api.libs.json.{JsValue, Json}
import play.api.mvc.RequestHeader
import uk.gov.hmrc.http.{HttpException, UpstreamErrorResponse}

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success, Try}

class GetAFTDetailsAuditService @Inject()(auditService: AuditService) {

  def sendAFTDetailsAuditEvent(pstr: String, startDate: String)
                              (implicit ec: ExecutionContext, request: RequestHeader): PartialFunction[Try[JsValue], Unit] = {
    case Success(response) =>
      sendEvent(pstr, startDate, Status.OK, Some(response))
    case Failure(error: UpstreamErrorResponse) =>
      sendEvent(pstr, startDate, error.upstreamResponseCode, None)
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

  override def auditType: String = "AftGet"

  override def details: Map[String, String] = Map(
    "pstr" -> pstr,
    "quarterStartDate" -> startDate,
    "aftStatus" -> response.flatMap(res => (res \ "aftDetails" \ "aftStatus").asOpt[String]).getOrElse(""),
    "status" -> status.toString,
    "response" -> {
      response match {
        case Some(json) => Json.stringify(json)
        case _ => ""
      }
    }
  )
}


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
import models.AFTVersion
import play.api.libs.json.{JsObject, JsValue, Json}
import play.api.mvc.RequestHeader
import uk.gov.hmrc.http.{HttpException, UpstreamErrorResponse}

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Try}

class GetAFTVersionsAuditService @Inject()(auditService: AuditService) {

  def sendAFTVersionsAuditEvent(pstr: String, startDate: String)
                               (implicit ec: ExecutionContext, request: RequestHeader): PartialFunction[Try[Seq[AFTVersion]], Unit] = {
    case Failure(e: UpstreamErrorResponse) =>
      auditService.sendEvent(
        GetAFTVersions(pstr, startDate, e.statusCode, None)
      )
    case Failure(e: HttpException) =>
      auditService.sendEvent(
        GetAFTVersions(pstr, startDate, e.responseCode, None)
      )
  }
}

case class GetAFTVersions(
                           pstr: String,
                           startDate: String,
                           status: Int,
                           response: Option[JsValue]
                         ) extends AuditEvent {

  override def auditType: String = "GetReportVersions"

  override def details: JsObject = Json.obj(
    "pstr" -> pstr,
    "quarterStartDate" -> startDate,
    "status" -> status.toString,
    "response" -> response
  )
}



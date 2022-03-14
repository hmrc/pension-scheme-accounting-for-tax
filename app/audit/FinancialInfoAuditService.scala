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
import models.{PsaFS, SchemeFSDetail}
import play.api.http.Status
import play.api.libs.json.{JsObject, JsValue, Json}
import play.api.mvc.RequestHeader
import uk.gov.hmrc.http.{HttpException, UpstreamErrorResponse}

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success, Try}

class FinancialInfoAuditService @Inject()(auditService: AuditService) {

  def sendPsaFSAuditEvent(psaId: String)
                         (implicit ec: ExecutionContext, request: RequestHeader): PartialFunction[Try[Seq[PsaFS]], Unit] = {
    case Success(response) if response.isEmpty =>
      auditService.sendEvent(GetPsaFS(psaId, Status.NOT_FOUND, None))
    case Success(response) =>
      auditService.sendEvent(GetPsaFS(psaId, Status.OK, Some(Json.toJson(response))))
    case Failure(error: UpstreamErrorResponse) =>
      auditService.sendEvent(GetPsaFS(psaId, error.statusCode, None))
    case Failure(error: HttpException) =>
      auditService.sendEvent(GetPsaFS(psaId, error.responseCode, None))
  }

  def sendSchemeFSAuditEvent(pstr: String)
                            (implicit ec: ExecutionContext, request: RequestHeader): PartialFunction[Try[Seq[SchemeFSDetail]], Unit] = {
    case Success(response) if response.isEmpty =>
      auditService.sendEvent(GetSchemeFS(pstr, Status.NOT_FOUND, None))
    case Success(response) =>
      auditService.sendEvent(GetSchemeFS(pstr, Status.OK, Some(Json.toJson(response))))
    case Failure(error: UpstreamErrorResponse) =>
      auditService.sendEvent(GetSchemeFS(pstr, error.statusCode, None))
    case Failure(error: HttpException) =>
      auditService.sendEvent(GetSchemeFS(pstr, error.responseCode, None))
  }

}

case class GetPsaFS(psaId: String, status: Int, response: Option[JsValue]) extends AuditEvent {

  override def auditType: String = "PsaFinancialInfoGet"

  override def details: JsObject = Json.obj(
    "psaId" -> psaId,
    "status" -> status.toString,
    "response" -> response
  )
}

case class GetSchemeFS(pstr: String, status: Int, response: Option[JsValue]) extends AuditEvent {

  override def auditType: String = "SchemeFinancialInfoGet"

  override def details: JsObject = Json.obj(
    "pstr" -> pstr,
    "status" -> status.toString,
    "response" -> response
  )
}

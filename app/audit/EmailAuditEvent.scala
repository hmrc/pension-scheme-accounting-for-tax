/*
 * Copyright 2021 HM Revenue & Customs
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

import models.enumeration.SchemeAdministratorType.SchemeAdministratorType
import models.Event
import models.enumeration.{JourneyType, SchemeAdministratorType}
import play.api.libs.json.{Json, JsObject}

case class EmailAuditEvent(psaOrPspId: String, submittedBy: SchemeAdministratorType, emailAddress: String, event: Event,
  journeyType: JourneyType.Name, requestId: String) extends AuditEvent {

  override def auditType: String = s"${journeyType.toString}EmailEvent"

  override def details: JsObject = {
    val psaOrPspIdJson = submittedBy match {
      case SchemeAdministratorType.PSA => Json.obj("psaId" -> psaOrPspId)
      case _ => Json.obj("pspId" -> psaOrPspId)
    }
    Json.obj(fields = "email-initiation-request-id" -> requestId, "emailAddress" -> emailAddress,
      "event" -> event.toString, "submittedBy" -> submittedBy.toString) ++ psaOrPspIdJson
  }
}

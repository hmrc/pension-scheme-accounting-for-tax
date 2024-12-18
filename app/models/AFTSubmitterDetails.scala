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

package models

import play.api.libs.json.{Format, Json, Reads}

import java.time.{LocalDate, LocalDateTime}

case class AFTSubmitterDetails(submitterType: String, submitterName: String, submitterID: String, authorisingPsaId: Option[String], receiptDate: LocalDate)

object AFTSubmitterDetails {
  implicit val formats: Format[AFTSubmitterDetails] = Json.format[AFTSubmitterDetails]

  implicit val readAftDetailsFromIF: Reads[AFTSubmitterDetails] = Reads { json =>
    for {
      submitterType <- (json \ "aftDeclarationDetails" \ "submittedBy").validate[String]
      submitterName <- (json \ "aftDeclarationDetails" \ "submitterName").validate[String]
      submitterID <- (json \ "aftDeclarationDetails" \ "submitterId").validate[String]
      authorisingPsaId = (json \ "aftDeclarationDetails" \ "psaId").asOpt[String]
      receiptDate <- (json \ "aftDetails" \ "receiptDate").validate[String].map { dateTime =>
        LocalDateTime.parse(dateTime.dropRight(1)).toLocalDate
      }
    } yield AFTSubmitterDetails(submitterType, submitterName, submitterID, authorisingPsaId, receiptDate)
  }

}

case class VersionsWithSubmitter(versionDetails: AFTVersion, submitterDetails: Option[AFTSubmitterDetails])

object VersionsWithSubmitter {
  implicit val formats: Format[VersionsWithSubmitter] = Json.format[VersionsWithSubmitter]
}

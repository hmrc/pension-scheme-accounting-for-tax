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
}

case class AFTInput(
    aftDeclarationDetails: AFTDeclarationDetails,
    aftDetails: AFTDetails
)

case class AFTDeclarationDetails(
    submittedBy: String,
    submitterId: String,
    submitterName: String,
    psaId: Option[String]
)

case class AFTDetails(
    receiptDate: String
)

object AFTInput {
  implicit val readsAFTDeclarationDetails: Format[AFTDeclarationDetails] =
    Json.format[AFTDeclarationDetails]
  implicit val readsAFTDetails: Format[AFTDetails] = Json.format[AFTDetails]
  implicit val readsAFTInput: Format[AFTInput] = Json.format[AFTInput]
}

object AFTTransformer {
  def transform(input: AFTInput): AFTSubmitterDetails = {
    AFTSubmitterDetails(
      submitterType = input.aftDeclarationDetails.submittedBy,
      submitterName = input.aftDeclarationDetails.submitterName,
      submitterID = input.aftDeclarationDetails.submitterId,
      authorisingPsaId = input.aftDeclarationDetails.psaId,
      receiptDate = LocalDateTime
        .parse(input.aftDetails.receiptDate.dropRight(1))
        .toLocalDate
    )
  }
}

case class VersionsWithSubmitter(versionDetails: AFTVersion, submitterDetails: Option[AFTSubmitterDetails])

object VersionsWithSubmitter {
  implicit val formats: Format[VersionsWithSubmitter] = Json.format[VersionsWithSubmitter]
}

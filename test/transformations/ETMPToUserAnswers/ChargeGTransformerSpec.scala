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

package transformations.ETMPToUserAnswers

import org.scalatest.FreeSpec
import play.api.libs.json.JsObject
import transformations.generators.AFTETMPResponseGenerators

class ChargeGTransformerSpec extends FreeSpec with AFTETMPResponseGenerators {

  "A Charge G Transformer" - {
    "must transform ChargeGDetails from ETMP ChargeGDetails to UserAnswers" in {
      forAll(chargeGUserAnswersGenerator) {
        generatedValues =>
          val (etmpResponseJson, userAnswersJson) = generatedValues
          val transformer = new ChargeGTransformer
          val transformedJson = etmpResponseJson.transform(transformer.transformToUserAnswers).asOpt.value

          transformedJson \ "chargeGDetails" \ "members" \ 0 \ "memberDetails" \ "firstName" mustBe
            etmpResponseJson \ "chargeTypeGDetails" \ "memberDetails" \ 0 \ "individualsDetails" \ "firstName"
          transformedJson \ "chargeGDetails" \ "members" \ 0 \ "memberDetails" \ "lastName" mustBe
            etmpResponseJson \ "chargeTypeGDetails" \ "memberDetails" \ 0 \ "individualsDetails" \ "lastName"
          transformedJson \ "chargeGDetails" \ "members" \ 0 \ "memberDetails" \ "dob" mustBe
            etmpResponseJson \ "chargeTypeGDetails" \ "memberDetails" \ 0 \ "individualsDetails" \ "dateOfBirth"

          transformedJson \ "chargeGDetails" \ "members" \ 0 \ "chargeDetails" \ "qropsReferenceNumber" mustBe
            etmpResponseJson \ "chargeTypeGDetails" \ "memberDetails" \ 0 \ "qropsReference"

          transformedJson \ "chargeGDetails" \ "members" \ 0 \ "chargeDetails" \ "qropsTransferDate" mustBe
            etmpResponseJson \ "chargeTypeGDetails" \ "memberDetails" \ 0 \ "dateOfTransfer"

          transformedJson \ "chargeGDetails" \ "members" \ 0 \ "chargeAmounts" \ "amountTransferred" mustBe
            etmpResponseJson \ "chargeTypeGDetails" \ "memberDetails" \ 0 \ "amountTransferred"

          transformedJson \ "chargeGDetails" \ "members" \ 0 \ "chargeAmounts" \ "amountTaxDue" mustBe
            etmpResponseJson \ "chargeTypeGDetails" \ "memberDetails" \ 0 \ "amountOfTaxDeducted"

          transformedJson \ "chargeGDetails" \ "totalChargeAmount" mustBe
            etmpResponseJson \ "chargeTypeGDetails" \ "totalOTCAmount"

          transformedJson \ "chargeGDetails" \ "members" \ 1 \ "memberDetails" \ "firstName" mustBe
            etmpResponseJson \ "chargeTypeGDetails" \ "memberDetails" \ 1 \ "individualsDetails" \ "firstName"

          (transformedJson \ "chargeGDetails" \ "members").as[Seq[JsObject]].size mustBe 2

      }
    }
  }

}

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

package transformations.userAnswersToETMP

import org.scalatest.FreeSpec
import play.api.libs.json.{JsDefined, JsObject, JsString, Json}
import transformations.generators.AFTUserAnswersGenerators

class ChargeGTransformerSpec extends FreeSpec with AFTUserAnswersGenerators {

  "A ChargeG Transformer" - {
    "must transform ChargeGDetails from UserAnswers to ETMP ChargeGDetails" in {
      forAll(chargeGUserAnswersGenerator) {
        userAnswersJson =>

          val transformer = new ChargeGTransformer
          val transformedJson = userAnswersJson.transform(transformer.transformToETMPData).asOpt.value

          transformedJson \ "chargeDetails" \ "chargeTypeGDetails" \ "memberDetails" \ 0 \ "individualsDetails" \ "firstName" mustBe
            userAnswersJson \ "chargeGDetails" \ "members" \ 0 \ "memberDetails" \ "firstName"
          transformedJson \ "chargeDetails" \ "chargeTypeGDetails" \ "memberDetails" \ 0 \ "individualsDetails" \ "lastName" mustBe
            userAnswersJson \ "chargeGDetails" \ "members" \ 0 \ "memberDetails" \ "lastName"
          transformedJson \ "chargeDetails" \ "chargeTypeGDetails" \ "memberDetails" \ 0 \ "individualsDetails" \ "dateOfBirth" mustBe
            userAnswersJson \ "chargeGDetails" \ "members" \ 0 \ "memberDetails" \ "dob"
          transformedJson \ "chargeDetails" \ "chargeTypeGDetails" \ "memberDetails" \ 0 \ "individualsDetails" \ "nino" mustBe
            userAnswersJson \ "chargeGDetails" \ "members" \ 0 \ "memberDetails" \ "nino"
          transformedJson \ "chargeDetails" \ "chargeTypeGDetails" \ "memberDetails" \ 0 \ "qropsReference" mustBe
            userAnswersJson \ "chargeGDetails" \ "members" \ 0 \ "chargeDetails" \ "qropsReferenceNumber"
          transformedJson \ "chargeDetails" \ "chargeTypeGDetails" \ "memberDetails" \ 0 \ "dateOfTransfer" mustBe
            userAnswersJson \ "chargeGDetails" \ "members" \ 0 \ "chargeDetails" \ "qropsTransferDate"
          transformedJson \ "chargeDetails" \ "chargeTypeGDetails" \ "memberDetails" \ 0 \ "amountTransferred" mustBe
            userAnswersJson \ "chargeGDetails" \ "members" \ 0 \ "chargeAmounts" \ "amountTransferred"
          transformedJson \ "chargeDetails" \ "chargeTypeGDetails" \ "memberDetails" \ 0 \ "amountOfTaxDeducted" mustBe
            userAnswersJson \ "chargeGDetails" \ "members" \ 0 \ "chargeAmounts" \ "amountTaxDue"
          transformedJson \ "chargeDetails" \ "chargeTypeGDetails" \ "memberDetails" \ 0 \ "memberStatus" mustBe
            JsDefined(JsString("New"))
          transformedJson \ "chargeDetails" \ "chargeTypeGDetails" \ "totalAmount" mustBe
            userAnswersJson \ "chargeGDetails" \ "totalChargeAmount"

          transformedJson \ "chargeDetails" \ "chargeTypeGDetails" \ "memberDetails" \ 1 \ "individualsDetails" \ "firstName" mustBe
            userAnswersJson \ "chargeGDetails" \ "members" \ 1 \ "memberDetails" \ "firstName"

          (transformedJson \ "chargeDetails" \ "chargeTypeGDetails" \ "memberDetails").as[Seq[JsObject]].size mustBe 5

      }
    }

    "must not pass ChargeG to ETMP if total amount is 0" in {
      forAll(chargeGAllDeletedUserAnswersGenerator) {
        userAnswersJson =>
          val transformer = new ChargeGTransformer
          val transformedJson = userAnswersJson.transform(transformer.transformToETMPData).asOpt.value

          transformedJson.as[JsObject] mustBe Json.obj()

      }
    }
  }

}

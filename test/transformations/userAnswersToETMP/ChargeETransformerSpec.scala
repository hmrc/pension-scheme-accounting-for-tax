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

class ChargeETransformerSpec extends FreeSpec with AFTUserAnswersGenerators {

  "A Charge E Transformer" - {
    "must transform ChargeEDetails from UserAnswers to ETMP ChargeEDetails" in {
      forAll(chargeEUserAnswersGenerator) {
        userAnswersJson =>
          val transformer = new ChargeETransformer
          val transformedJson = userAnswersJson.transform(transformer.transformToETMPData).asOpt.value
          transformedJson \ "chargeDetails" \ "chargeTypeEDetails" \ "memberDetails" \ 0 \ "individualsDetails" \ "firstName" mustBe
            userAnswersJson \ "chargeEDetails" \ "members" \ 0 \ "memberDetails" \ "firstName"
          transformedJson \ "chargeDetails" \ "chargeTypeEDetails" \ "memberDetails" \ 0 \ "individualsDetails" \ "lastName" mustBe
            userAnswersJson \ "chargeEDetails" \ "members" \ 0 \ "memberDetails" \ "lastName"
          transformedJson \ "chargeDetails" \ "chargeTypeEDetails" \ "memberDetails" \ 0 \ "amountOfCharge" mustBe
            userAnswersJson \ "chargeEDetails" \ "members" \ 0 \ "chargeDetails" \ "chargeAmount"
          transformedJson \ "chargeDetails" \ "chargeTypeEDetails" \ "memberDetails" \ 0 \ "dateOfNotice" mustBe
            userAnswersJson \ "chargeEDetails" \ "members" \ 0 \ "chargeDetails" \ "dateNoticeReceived"
          transformedJson \ "chargeDetails" \ "chargeTypeEDetails" \ "memberDetails" \ 0 \ "paidUnder237b" mustBe
            (if((userAnswersJson \ "chargeEDetails" \ "members" \ 0 \ "chargeDetails" \ "isPaymentMandatory").get.as[Boolean]) {
              JsDefined(JsString("Yes"))
            } else {
              JsDefined(JsString("No"))
            })
          transformedJson \ "chargeDetails" \ "chargeTypeEDetails" \ "memberDetails" \ 0 \ "taxYearEnding" mustBe
            userAnswersJson \ "chargeEDetails" \ "members" \ 0 \ "annualAllowanceYear"
          transformedJson \ "chargeDetails" \ "chargeTypeEDetails" \ "memberDetails" \ 0 \ "memberStatus" mustBe
            JsDefined(JsString("New"))
          transformedJson \ "chargeDetails" \ "chargeTypeEDetails" \ "totalAmount" mustBe
            userAnswersJson \ "chargeEDetails" \ "totalChargeAmount"

          transformedJson \ "chargeDetails" \ "chargeTypeEDetails" \ "memberDetails" \ 1 \ "individualsDetails" \ "firstName" mustBe
            userAnswersJson \ "chargeEDetails" \ "members" \ 1 \ "memberDetails" \ "firstName"

          (transformedJson \ "chargeDetails" \ "chargeTypeEDetails" \ "memberDetails").as[Seq[JsObject]].size mustBe 5

      }
    }

    "must not pass ChargeE to ETMP if total amount is 0" in {
      forAll(chargeEAllDeletedUserAnswersGenerator) {
        userAnswersJson =>
          val transformer = new ChargeETransformer
          val transformedJson = userAnswersJson.transform(transformer.transformToETMPData).asOpt.value

          transformedJson.as[JsObject] mustBe Json.obj()

      }
    }
  }

}

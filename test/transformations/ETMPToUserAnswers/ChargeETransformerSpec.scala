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
import play.api.libs.json.{JsBoolean, JsDefined, JsObject}
import transformations.generators.AFTETMPResponseGenerators

class ChargeETransformerSpec extends FreeSpec with AFTETMPResponseGenerators {

  "A Charge E Transformer" - {
    "must transform ChargeEDetails from ETMP ChargeEDetails to UserAnswers" in {
      forAll(chargeEUserAnswersGenerator) {
        generatedValues =>
          val (etmpResponseJson, userAnswersJson) = generatedValues
          val transformer = new ChargeETransformer
          val transformedJson = etmpResponseJson.transform(transformer.transformToUserAnswers).asOpt.value
          transformedJson \ "chargeEDetails" \ "members" \ 0 \ "memberDetails" \ "firstName" mustBe
            etmpResponseJson \ "chargeTypeEDetails" \ "memberDetails" \ 0 \ "individualsDetails" \ "firstName"
          transformedJson \ "chargeEDetails" \ "members" \ 0 \ "memberDetails" \ "lastName" mustBe
            etmpResponseJson \ "chargeTypeEDetails" \ "memberDetails" \ 0 \ "individualsDetails" \ "lastName"
          transformedJson \ "chargeEDetails" \ "members" \ 0 \ "chargeDetails" \ "chargeAmount" mustBe
            etmpResponseJson \ "chargeTypeEDetails" \ "memberDetails" \ 0 \ "amountOfCharge"
          transformedJson \ "chargeEDetails" \ "members" \ 0 \ "chargeDetails" \ "dateNoticeReceived" mustBe
            etmpResponseJson \ "chargeTypeEDetails" \ "memberDetails" \ 0 \ "dateOfNotice"
          transformedJson \ "chargeEDetails" \ "members" \ 0 \ "chargeDetails" \ "isPaymentMandatory" mustBe
            JsDefined(JsBoolean(
              (etmpResponseJson \ "chargeTypeEDetails" \ "memberDetails" \ 0 \ "paidUnder237b").get.as[String].equals("Yes")
            ))


          transformedJson \ "chargeEDetails" \ "members" \ 0 \ "annualAllowanceYear" mustBe
            etmpResponseJson \ "chargeTypeEDetails" \ "memberDetails" \ 0 \ "taxYearEnding"

          transformedJson \ "chargeEDetails" \ "totalChargeAmount" mustBe
            etmpResponseJson \ "chargeTypeEDetails" \ "totalAmount"

          transformedJson \ "chargeEDetails" \ "members" \ 1 \ "memberDetails" \ "firstName" mustBe
            etmpResponseJson \ "chargeTypeEDetails" \ "memberDetails" \ 1 \ "individualsDetails" \ "firstName"

          (transformedJson \ "chargeEDetails" \ "members").as[Seq[JsObject]].size mustBe 2
          transformedJson mustBe userAnswersJson

      }
    }
  }

}

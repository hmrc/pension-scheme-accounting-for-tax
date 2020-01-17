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
import play.api.libs.json.{JsBoolean, JsDefined, JsLookupResult, JsObject}
import transformations.generators.AFTETMPResponseGenerators

class ChargeETransformerSpec extends FreeSpec with AFTETMPResponseGenerators {

  "A Charge E Transformer" - {
    "must transform ChargeEDetails from ETMP ChargeEDetails to UserAnswers" in {
      forAll(chargeEETMPGenerator) {
        etmpResponseJson =>
          val transformer = new ChargeETransformer
          val transformedJson = etmpResponseJson.transform(transformer.transformToUserAnswers).asOpt.value

          def membersUAPath(i: Int): JsLookupResult = transformedJson \ "chargeEDetails" \ "members" \ i

          def membersETMPPath(i: Int): JsLookupResult = etmpResponseJson \ "chargeTypeEDetails" \ "memberDetails" \ i


          membersUAPath(0) \ "memberDetails" \ "firstName" mustBe membersETMPPath(0) \ "individualsDetails" \ "firstName"
          membersUAPath(0) \ "memberDetails" \ "lastName" mustBe membersETMPPath(0) \ "individualsDetails" \ "lastName"
          membersUAPath(0) \ "memberDetails" \ "nino" mustBe membersETMPPath(0) \ "individualsDetails" \ "nino"

          membersUAPath(0) \ "chargeDetails" \ "chargeAmount" mustBe membersETMPPath(0) \ "amountOfCharge"
          membersUAPath(0) \ "chargeDetails" \ "dateNoticeReceived" mustBe membersETMPPath(0) \ "dateOfNotice"
          membersUAPath(0) \ "chargeDetails" \ "isPaymentMandatory" mustBe JsDefined(JsBoolean(
              (membersETMPPath(0) \ "paidUnder237b").get.as[String].equals("Yes")))


          membersUAPath(0) \ "annualAllowanceYear" mustBe membersETMPPath(0) \ "taxYearEnding"

          transformedJson \ "chargeEDetails" \ "totalChargeAmount" mustBe etmpResponseJson \ "chargeTypeEDetails" \ "totalAmount"

          membersUAPath(1) \ "memberDetails" \ "firstName" mustBe membersETMPPath(1) \ "individualsDetails" \ "firstName"

          (transformedJson \ "chargeEDetails" \ "members").as[Seq[JsObject]].size mustBe 2


      }
    }
  }

}

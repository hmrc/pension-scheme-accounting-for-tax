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
import play.api.libs.json._
import transformations.generators.AFTUserAnswersGenerators

class ChargeETransformerSpec extends FreeSpec with AFTUserAnswersGenerators {

  "A Charge E Transformer" - {
    "must transform ChargeEDetails from UserAnswers to ETMP ChargeEDetails" in {
      forAll(chargeEUserAnswersGenerator) {
        userAnswersJson =>
          val transformer = new ChargeETransformer
          val transformedJson = userAnswersJson.transform(transformer.transformToETMPData).asOpt.value

          def etmpMemberPath(i: Int): JsLookupResult = transformedJson \ "chargeDetails" \ "chargeTypeEDetails" \ "memberDetails" \ i

          def uaMemberPath(i: Int): JsLookupResult = userAnswersJson \ "chargeEDetails" \ "members" \ i

          (etmpMemberPath(0) \ "individualsDetails" \ "firstName").as[String] mustBe (uaMemberPath(0) \ "memberDetails" \ "firstName").as[String]
          (etmpMemberPath(0) \ "individualsDetails" \ "lastName").as[String] mustBe (uaMemberPath(0) \ "memberDetails" \ "lastName").as[String]
          (etmpMemberPath(0) \ "individualsDetails" \ "nino").as[String] mustBe (uaMemberPath(0) \ "memberDetails" \ "nino").as[String]
          (etmpMemberPath(1) \ "individualsDetails" \ "firstName").as[String] mustBe (uaMemberPath(1) \ "memberDetails" \ "firstName").as[String]

          (etmpMemberPath(0) \ "amountOfCharge").as[BigDecimal] mustBe (uaMemberPath(0) \ "chargeDetails" \ "chargeAmount").as[BigDecimal]
          (etmpMemberPath(0) \ "dateOfNotice").as[String] mustBe (uaMemberPath(0) \ "chargeDetails" \ "dateNoticeReceived").as[String]
          (etmpMemberPath(0) \ "paidUnder237b").as[String] mustBe
            (if ((uaMemberPath(0) \ "chargeDetails" \ "isPaymentMandatory").as[Boolean]) "Yes" else "No")
          (etmpMemberPath(0) \ "taxYearEnding").as[String] mustBe (uaMemberPath(0) \ "annualAllowanceYear").as[String]
          (etmpMemberPath(0) \ "memberStatus").as[String] mustBe "New"

          transformedJson \ "chargeDetails" \ "chargeTypeEDetails" \ "totalAmount" mustBe userAnswersJson \ "chargeEDetails" \ "totalChargeAmount"
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

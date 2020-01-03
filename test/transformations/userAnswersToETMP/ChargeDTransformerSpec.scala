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
import transformations.generators.AFTGenerators

class ChargeDTransformerSpec extends FreeSpec with AFTGenerators {

  "A Charge D Transformer" - {
    "must transform ChargeDDetails from UserAnswers to ETMP ChargeDDetails" in {
      forAll(chargeDUserAnswersGenerator) {
        userAnswersJson =>

          val transformer = new ChargeDTransformer
          val transformedJson = userAnswersJson.transform(transformer.transformToETMPData).asOpt.value

          transformedJson \ "chargeDetails" \ "chargeTypeDDetails" \ "memberDetails" \ 0 \ "individualsDetails" \ "firstName" mustBe
            userAnswersJson \ "chargeDDetails" \ "members" \ 0 \ "memberDetails" \ "firstName"
          transformedJson \ "chargeDetails" \ "chargeTypeDDetails" \ "memberDetails" \ 0 \ "individualsDetails" \ "lastName" mustBe
            userAnswersJson \ "chargeDDetails" \ "members" \ 0 \ "memberDetails" \ "lastName"
          transformedJson \ "chargeDetails" \ "chargeTypeDDetails" \ "memberDetails" \ 0 \ "dateOfBeneCrysEvent" mustBe
            userAnswersJson \ "chargeDDetails" \ "members" \ 0 \ "chargeDetails" \ "dateOfEvent"
          transformedJson \ "chargeDetails" \ "chargeTypeDDetails" \ "memberDetails" \ 0 \ "totalAmtOfTaxDueAtLowerRate" mustBe
            userAnswersJson \ "chargeDDetails" \ "members" \ 0 \ "chargeDetails" \ "taxAt25Percent"
          transformedJson \ "chargeDetails" \ "chargeTypeDDetails" \ "memberDetails" \ 0 \ "totalAmtOfTaxDueAtHigherRate" mustBe
            userAnswersJson \ "chargeDDetails" \ "members" \ 0 \ "chargeDetails" \ "taxAt55Percent"
          transformedJson \ "chargeDetails" \ "chargeTypeDDetails" \ "memberDetails" \ 0 \ "memberStatus" mustBe
            JsDefined(JsString("New"))
          transformedJson \ "chargeDetails" \ "chargeTypeDDetails" \ "totalAmount" mustBe
            userAnswersJson \ "chargeDDetails" \ "totalChargeAmount"

          transformedJson \ "chargeDetails" \ "chargeTypeDDetails" \ "memberDetails" \ 1 \ "individualsDetails" \ "firstName" mustBe
            userAnswersJson \ "chargeDDetails" \ "members" \ 1 \ "memberDetails" \ "firstName"

          (transformedJson \ "chargeDetails" \ "chargeTypeDDetails" \ "memberDetails").as[Seq[JsObject]].size mustBe 5

      }
    }

    "must not pass ChargeD to ETMP if total amount is 0" in {
      forAll(chargeDAllDeletedUserAnswersGenerator) {
        userAnswersJson =>
          val transformer = new ChargeDTransformer
          val transformedJson = userAnswersJson.transform(transformer.transformToETMPData).asOpt.value

          transformedJson.as[JsObject] mustBe Json.obj()

      }
    }
  }

}

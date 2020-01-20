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

class ChargeDTransformerSpec extends FreeSpec with AFTUserAnswersGenerators {

  "A Charge D Transformer" - {
    "must transform ChargeDDetails from UserAnswers to ETMP ChargeDDetails" in {
      forAll(chargeDUserAnswersGenerator) {
        userAnswersJson =>

          val transformer = new ChargeDTransformer
          val transformedJson = userAnswersJson.transform(transformer.transformToETMPData).asOpt.value

          def etmpMemberPath(i: Int): JsLookupResult = transformedJson \ "chargeDetails" \ "chargeTypeDDetails" \ "memberDetails" \ i

          def uaMemberPath(i: Int): JsLookupResult = userAnswersJson \ "chargeDDetails" \ "members" \ i

          (etmpMemberPath(0) \ "individualsDetails" \ "firstName").as[String] mustBe (uaMemberPath(0) \ "memberDetails" \ "firstName").as[String]
          (etmpMemberPath(0) \ "individualsDetails" \ "lastName").as[String] mustBe (uaMemberPath(0) \ "memberDetails" \ "lastName").as[String]
          (etmpMemberPath(0) \ "individualsDetails" \ "nino").as[String] mustBe (uaMemberPath(0) \ "memberDetails" \ "nino").as[String]

          (etmpMemberPath(0) \ "dateOfBeneCrysEvent").as[String] mustBe (uaMemberPath(0) \ "chargeDetails" \ "dateOfEvent").as[String]
          (etmpMemberPath(0) \ "totalAmtOfTaxDueAtLowerRate").as[BigDecimal] mustBe (uaMemberPath(0) \ "chargeDetails" \ "taxAt25Percent").as[BigDecimal]
          (etmpMemberPath(0) \ "totalAmtOfTaxDueAtHigherRate").as[BigDecimal] mustBe (uaMemberPath(0) \ "chargeDetails" \ "taxAt55Percent").as[BigDecimal]
          (etmpMemberPath(0) \ "memberStatus").as[String] mustBe "New"

          (etmpMemberPath(1) \ "individualsDetails" \ "firstName").as[String] mustBe (uaMemberPath(1) \ "memberDetails" \ "firstName").as[String]

          (transformedJson \ "chargeDetails" \ "chargeTypeDDetails" \ "totalAmount").as[BigDecimal] mustBe
            (userAnswersJson \ "chargeDDetails" \ "totalChargeAmount").as[BigDecimal]

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

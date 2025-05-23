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

package transformations.userAnswersToETMP

import org.scalacheck.Arbitrary.arbitrary
import org.scalatest.OptionValues
import org.scalatest.freespec.AnyFreeSpec
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks.forAll
import play.api.libs.json._
import transformations.generators.AFTUserAnswersGenerators

class ChargeATransformerSpec extends AnyFreeSpec with AFTUserAnswersGenerators with OptionValues {

  import ChargeATransformerSpec._

  private val transformer = new ChargeATransformer

  "A Charge A Transformer" - {
    "must transform mandatory elements of ChargeADetails from UserAnswers to ETMP" in {
      forAll(chargeAUserAnswersGenerator) {
        userAnswersJson =>
          val transformedJson = userAnswersJson.transform(transformer.transformToETMPData).asOpt.value

          (etmpPath(transformedJson) \ "numberOfMembers").as[Int] `mustBe` (uaPath(userAnswersJson) \ "chargeDetails" \ "numberOfMembers").as[Int]

          (etmpPath(transformedJson) \ "totalAmtOfTaxDueAtLowerRate").as[BigDecimal] `mustBe`
            (uaPath(userAnswersJson) \ "chargeDetails" \ "totalAmtOfTaxDueAtLowerRate").as[BigDecimal]

          (etmpPath(transformedJson) \ "totalAmtOfTaxDueAtHigherRate").as[BigDecimal] `mustBe`
            (uaPath(userAnswersJson) \ "chargeDetails" \ "totalAmtOfTaxDueAtHigherRate").as[BigDecimal]

          (etmpPath(transformedJson) \ "totalAmount").as[BigDecimal] `mustBe` (uaPath(userAnswersJson) \ "chargeDetails" \ "totalAmount").as[BigDecimal]

          (etmpPath(transformedJson) \ "amendedVersion").asOpt[Int] `mustBe` None
      }
    }

    "must transform optional elements - amendedVersion of ChargeADetails from UserAnswers to ETMP" in {
      forAll(chargeAUserAnswersGenerator, arbitrary[Int]) {
        (userAnswersJson, version) =>
          val updatedJson = userAnswersJson.transform(updateJson(__ \ Symbol("chargeADetails"), name = "amendedVersion", version)).asOpt.value
          val transformedJson = updatedJson.transform(transformer.transformToETMPData).asOpt.value

          (etmpPath(transformedJson) \ "amendedVersion").as[Int] `mustBe` (uaPath(updatedJson) \ "amendedVersion").as[Int]
      }
    }
  }
}

object ChargeATransformerSpec {
  private def etmpPath(json: JsObject): JsLookupResult = json \ "chargeDetails" \ "chargeTypeADetails"

  private def uaPath(json: JsObject): JsLookupResult = json \ "chargeADetails"
}



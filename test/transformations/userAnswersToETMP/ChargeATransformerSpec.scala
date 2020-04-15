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

import org.scalacheck.Arbitrary.arbitrary
import org.scalatest.FreeSpec
import transformations.generators.AFTUserAnswersGenerators
import play.api.libs.json.{__, _}

class ChargeATransformerSpec extends FreeSpec with AFTUserAnswersGenerators {

  import ChargeATransformerSpec._

  private val transformer = new ChargeATransformer

  "A Charge A Transformer" - {
    "must transform mandatory elements of ChargeADetails from UserAnswers to ETMP" in {
      forAll(chargeAUserAnswersGenerator) {
        userAnswersJson =>
          val transformedJson = userAnswersJson.transform(transformer.transformToETMPData).asOpt.value

          (etmpPath(transformedJson) \ "numberOfMembers").as[Int] mustBe (uaPath(userAnswersJson) \ "numberOfMembers").as[Int]

          (etmpPath(transformedJson) \ "totalAmtOfTaxDueAtLowerRate").as[BigDecimal] mustBe
            (uaPath(userAnswersJson) \ "totalAmtOfTaxDueAtLowerRate").as[BigDecimal]

          (etmpPath(transformedJson) \ "totalAmtOfTaxDueAtHigherRate").as[BigDecimal] mustBe
            (uaPath(userAnswersJson) \ "totalAmtOfTaxDueAtHigherRate").as[BigDecimal]

          (etmpPath(transformedJson) \ "totalAmount").as[BigDecimal] mustBe (uaPath(userAnswersJson) \ "totalAmount").as[BigDecimal]

          (etmpPath(transformedJson) \ "amendedVersion").asOpt[Int] mustBe None
      }
    }

    "must transform optional elements - amendedVersion of ChargeADetails from UserAnswers to ETMP" in {
      forAll(chargeAUserAnswersGenerator, arbitrary[Int]) {
        (userAnswersJson, version) =>
          val updatedJson = userAnswersJson.transform(updateJson(__ \ 'chargeADetails, name = "amendedVersion", version)).asOpt.value
          val transformedJson = updatedJson.transform(transformer.transformToETMPData).asOpt.value

          (etmpPath(transformedJson) \ "amendedVersion").as[Int] mustBe (uaPath(updatedJson) \ "amendedVersion").as[Int]
      }
    }
  }
}

object ChargeATransformerSpec {
  def etmpPath(json: JsObject): JsLookupResult = json \ "chargeDetails" \ "chargeTypeADetails"

  def uaPath(json: JsObject): JsLookupResult = json \ "chargeADetails"
}



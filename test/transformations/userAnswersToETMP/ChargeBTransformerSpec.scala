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
import play.api.libs.json.__
import transformations.generators.AFTUserAnswersGenerators

class ChargeBTransformerSpec extends FreeSpec with AFTUserAnswersGenerators {

  private val transformer = new ChargeBTransformer

  "A Charge B Transformer" - {
    "must transform mandatory elements of ChargeBDetails from UserAnswers to ETMP" in {
      forAll(chargeBUserAnswersGenerator) {
        userAnswersJson =>
          val transformedJson = userAnswersJson.transform(transformer.transformToETMPData).asOpt.value

          (transformedJson \ "chargeDetails" \ "chargeTypeBDetails" \ "totalAmount").as[BigDecimal] mustBe
            (userAnswersJson \ "chargeBDetails" \ "chargeDetails" \ "totalAmount").as[BigDecimal]

          (transformedJson \ "chargeDetails" \ "chargeTypeBDetails" \ "numberOfMembers").as[Int] mustBe
            (userAnswersJson \ "chargeBDetails" \ "chargeDetails" \ "numberOfDeceased").as[Int]

          (transformedJson \ "chargeDetails" \ "chargeTypeBDetails" \ "amendedVersion").asOpt[Int] mustBe None
      }
    }

    "must transform optional elements - amendedVersion of ChargeBDetails from UserAnswers to ETMP" in {
      forAll(chargeBUserAnswersGenerator, arbitrary[Int]) {
        (userAnswersJson, version) =>
          val updatedJson = userAnswersJson.transform(updateJson(__ \ 'chargeBDetails, name = "amendedVersion", version)).asOpt.value
          val transformedJson = updatedJson.transform(transformer.transformToETMPData).asOpt.value

          (transformedJson \ "chargeDetails" \ "chargeTypeBDetails" \ "amendedVersion").as[Int] mustBe
            (updatedJson \ "chargeBDetails" \ "amendedVersion").as[Int]
      }
    }
  }
}

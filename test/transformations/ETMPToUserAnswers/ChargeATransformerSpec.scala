/*
 * Copyright 2023 HM Revenue & Customs
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

import org.scalatest.OptionValues
import org.scalatest.freespec.AnyFreeSpec
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks.forAll
import transformations.generators.AFTETMPResponseGenerators

class ChargeATransformerSpec extends AnyFreeSpec with AFTETMPResponseGenerators with OptionValues {

  "A Charge A Transformer" - {
    "must transform ChargeADetails from ETMP ChargeTypeADetails to UserAnswers" in {
      forAll(chargeAETMPGenerator) {
        etmpResponseJson =>
          val transformer = new ChargeATransformer
          val transformedJson = etmpResponseJson.transform(transformer.transformToUserAnswers).asOpt.value \ "chargeADetails"
          val chargeAResponse = etmpResponseJson \ "chargeTypeA"

          (transformedJson \ "amendedVersion").as[Int] mustBe (chargeAResponse \ "amendedVersion").as[Int]
          (transformedJson \ "chargeDetails" \ "numberOfMembers").as[Int] mustBe (chargeAResponse \ "numberOfMembers").as[Int]
          (transformedJson \ "chargeDetails" \ "totalAmtOfTaxDueAtLowerRate").as[BigDecimal] mustBe
            (chargeAResponse \ "totalAmtOfTaxDueAtLowerRate").as[BigDecimal]
          (transformedJson \ "chargeDetails" \ "totalAmtOfTaxDueAtHigherRate").as[BigDecimal] mustBe
            (chargeAResponse \ "totalAmtOfTaxDueAtHigherRate").as[BigDecimal]
          (transformedJson \ "chargeDetails" \ "totalAmount").as[BigDecimal] mustBe (chargeAResponse \ "totalAmount").as[BigDecimal]
      }
    }
  }

}

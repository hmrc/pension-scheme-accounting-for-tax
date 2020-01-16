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
import transformations.generators.AFTETMPResponseGenerators

class ChargeBTransformerSpec extends FreeSpec with AFTETMPResponseGenerators {

  "A Charge B Transformer" - {
    "must transform ChargeBDetails from ETMP ChargeTypeBDetails to UserAnswers" in {
      forAll(chargeBETMPGenerator) {
        etmpResponseJson =>
          val transformer = new ChargeBTransformer
          val transformedJson = etmpResponseJson.transform(transformer.transformToUserAnswers).asOpt.value

          transformedJson \ "chargeBDetails" \ "numberOfDeceased" mustBe
            etmpResponseJson \ "chargeTypeBDetails" \ "numberOfMembers"

          transformedJson \ "chargeBDetails" \ "amountTaxDue" mustBe
            etmpResponseJson \ "chargeTypeBDetails" \ "totalAmount"
      }
    }
  }
}
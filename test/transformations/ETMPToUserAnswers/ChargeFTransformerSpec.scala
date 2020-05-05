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

import java.time.LocalDate

import org.scalatest.FreeSpec
import transformations.generators.AFTETMPResponseGenerators

class ChargeFTransformerSpec extends FreeSpec with AFTETMPResponseGenerators {

  "A Charge F Transformer" - {
    "must transform ChargeFDetails from ETMP ChargeTypeFDetails to UserAnswers" in {
      forAll(chargeFETMPGenerator) {
        etmpResponseJson =>
          val transformer = new ChargeFTransformer
          val transformedJson = etmpResponseJson.transform(transformer.transformToUserAnswers).asOpt.value \ "chargeFDetails"
          val chargeFResponse = etmpResponseJson  \ "chargeTypeFDetails"

          (transformedJson \ "amendedVersion").as[Int] mustBe (chargeFResponse \ "amendedVersion").as[Int]
          (transformedJson \ "chargeDetails" \ "amountTaxDue").as[BigDecimal] mustBe (chargeFResponse \ "totalAmount").as[BigDecimal]
          (transformedJson \ "chargeDetails" \ "deRegistrationDate").as[LocalDate] mustBe (chargeFResponse \ "dateRegiWithdrawn").as[LocalDate]

      }
    }
  }

}

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

package transformations.ETMPToUserAnswers

import org.scalatest.OptionValues
import org.scalatest.freespec.AnyFreeSpec
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks.forAll
import transformations.generators.AFTETMPResponseGenerators

import java.time.LocalDate

class ChargeFTransformerSpec extends AnyFreeSpec with AFTETMPResponseGenerators with OptionValues {

  "A Charge F Transformer" - {
    "must transform ChargeFDetails from ETMP ChargeTypeFDetails to UserAnswers" in {
      forAll(chargeFETMPGenerator) {
        etmpResponseJson =>
          val transformer = new ChargeFTransformer
          val transformedJson = etmpResponseJson.transform(transformer.transformToUserAnswers).asOpt.value \ "chargeFDetails"
          val chargeFResponse = etmpResponseJson \ "chargeTypeF"

          (transformedJson \ "amendedVersion").as[Int].mustBe((chargeFResponse \ "amendedVersion").as[String].toInt)
          (transformedJson \ "chargeDetails" \ "totalAmount").as[BigDecimal].mustBe((chargeFResponse \ "totalAmount").as[BigDecimal])
          (transformedJson \ "chargeDetails" \ "deRegistrationDate").as[LocalDate].mustBe((chargeFResponse \ "dateRegiWithdrawn").as[LocalDate])

      }
    }
  }

}

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
import play.api.libs.json.{JsObject, Json, __}
import transformations.generators.AFTUserAnswersGenerators

class ChargeFTransformerSpec extends AnyFreeSpec with AFTUserAnswersGenerators with OptionValues {
  private val transformer = new ChargeFTransformer

  "A Charge F Transformer" - {
    "must transform ChargeFDetails from UserAnswers to ETMP ChargeFDetails" in {
      forAll(chargeFUserAnswersGenerator) {
        userAnswersJson =>

          val transformedJson = userAnswersJson.transform(transformer.transformToETMPData).asOpt.value

          (transformedJson \ "chargeDetails" \ "chargeTypeFDetails" \ "totalAmount").as[BigDecimal] `mustBe`
            (userAnswersJson \ "chargeFDetails" \ "chargeDetails" \ "totalAmount").as[BigDecimal]

          (transformedJson \ "chargeDetails" \ "chargeTypeFDetails" \ "dateRegiWithdrawn").asOpt[String] `mustBe` None

          (transformedJson \ "chargeDetails" \ "chargeTypeFDetails" \ "amendedVersion").asOpt[Int] `mustBe` None
      }
    }

    "must transform optional element - amendedVersion, deRegistrationDate from ChargeFDetails from UserAnswers to ETMP" in {
      forAll(chargeFUserAnswersGenerator, arbitrary[Int], arbitrary[String]) {
        (userAnswersJson, version, date) =>
          val updatedJson = userAnswersJson.transform(
            (__ \ Symbol("chargeFDetails") \ Symbol("chargeDetails")).json.update(__.read[JsObject]
              .map(o => o ++ Json.obj("deRegistrationDate" -> date)))).asOpt.value
            .transform((__ \ Symbol("chargeFDetails")).json.update(__.read[JsObject].map(o => o ++ Json.obj("amendedVersion" -> version)))).asOpt.value

          val transformedJson = updatedJson.transform(transformer.transformToETMPData).asOpt.value

          (transformedJson \ "chargeDetails" \ "chargeTypeFDetails" \ "amendedVersion").as[Int] `mustBe`
            (updatedJson \ "chargeFDetails" \ "amendedVersion").as[Int]

          (transformedJson \ "chargeDetails" \ "chargeTypeFDetails" \ "dateRegiWithdrawn").as[String] `mustBe`
            (updatedJson \ "chargeFDetails" \ "chargeDetails" \ "deRegistrationDate").as[String]
      }
    }
  }
}

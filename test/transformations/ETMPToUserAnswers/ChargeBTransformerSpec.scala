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

class ChargeBTransformerSpec extends AnyFreeSpec with AFTETMPResponseGenerators with OptionValues {

//  "A Charge B Transformer" - {
//    "must transform ChargeBDetails from ETMP ChargeTypeBDetails to UserAnswers" in {
//      forAll(chargeBETMPGenerator) {
//        etmpResponseJson =>
//          val transformer = new ChargeBTransformer
//          val transformedJson = etmpResponseJson.transform(transformer.transformToUserAnswers).asOpt.value
//
//          (transformedJson \ "chargeBDetails" \ "amendedVersion").as[Int] mustBe
//            (etmpResponseJson \ "chargeTypeB" \ "amendedVersion").as[Int]
//
//          (transformedJson \ "chargeBDetails" \ "chargeDetails" \ "numberOfDeceased").as[Int] mustBe
//            (etmpResponseJson \ "chargeTypeB" \ "numberOfMembers").as[Int]
//
//          (transformedJson \ "chargeBDetails" \ "chargeDetails" \ "totalAmount").as[BigDecimal] mustBe
//            (etmpResponseJson \ "chargeTypeB" \ "totalAmount").as[BigDecimal]
//      }
//    }
//  }
}

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
import play.api.libs.json.{JsLookupResult, JsObject}
import transformations.generators.AFTETMPResponseGenerators

class ChargeGTransformerSpec extends AnyFreeSpec with AFTETMPResponseGenerators with OptionValues {

  "A Charge G Transformer" - {
    "must transform ChargeGDetails from ETMP ChargeGDetails to UserAnswers" in {
      forAll(chargeGETMPGenerator) {
        etmpResponseJson =>
          val transformer = new ChargeGTransformer
          val transformedJson = etmpResponseJson.transform(transformer.transformToUserAnswers).asOpt.value

          def membersUAPath(i: Int): JsLookupResult = transformedJson \ "chargeGDetails" \ "members" \ i

          def membersETMPPath(i: Int): JsLookupResult = etmpResponseJson \ "chargeTypeGDetails" \ "memberDetails" \ i

          (membersUAPath(0) \ "memberStatus").as[String] mustBe (membersETMPPath(0) \ "memberStatus").as[String]
          (membersUAPath(0) \ "memberAFTVersion").as[Int] mustBe (membersETMPPath(0) \ "memberAFTVersion").as[Int]
          (membersUAPath(0) \ "memberDetails" \ "firstName").as[String] mustBe (membersETMPPath(0) \ "individualsDetails" \ "firstName").as[String]
          (membersUAPath(0) \ "memberDetails" \ "lastName").as[String] mustBe (membersETMPPath(0) \ "individualsDetails" \ "lastName").as[String]
          (membersUAPath(0) \ "memberDetails" \ "dob").as[String] mustBe (membersETMPPath(0) \ "individualsDetails" \ "dateOfBirth").as[String]

          (membersUAPath(0) \ "chargeDetails" \ "qropsReferenceNumber").as[String] mustBe (membersETMPPath(0) \ "qropsReference").as[String].substring(1)
          (membersUAPath(0) \ "chargeDetails" \ "qropsTransferDate").as[String] mustBe (membersETMPPath(0) \ "dateOfTransfer").as[String]

          (membersUAPath(0) \ "chargeAmounts" \ "amountTransferred").as[BigDecimal] mustBe (membersETMPPath(0) \ "amountTransferred").as[BigDecimal]
          (membersUAPath(0) \ "chargeAmounts" \ "amountTaxDue").as[BigDecimal] mustBe (membersETMPPath(0) \ "amountOfTaxDeducted").as[BigDecimal]

          (transformedJson \ "chargeGDetails" \ "totalChargeAmount").as[BigDecimal] mustBe
            (etmpResponseJson \ "chargeTypeGDetails" \ "totalOTCAmount").as[BigDecimal]

          (transformedJson \ "chargeGDetails" \ "amendedVersion").as[Int] mustBe
            (etmpResponseJson \ "chargeTypeGDetails" \ "amendedVersion").as[Int]

          (membersUAPath(1) \ "memberDetails" \ "firstName").as[String] mustBe (membersETMPPath(1) \ "individualsDetails" \ "firstName").as[String]

          (transformedJson \ "chargeGDetails" \ "members").as[Seq[JsObject]].size mustBe 2

      }
    }
  }

}

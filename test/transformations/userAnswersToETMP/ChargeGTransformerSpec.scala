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
import play.api.libs.json._
import transformations.generators.AFTUserAnswersGenerators

class ChargeGTransformerSpec extends FreeSpec with AFTUserAnswersGenerators {
  private val transformer = new ChargeGTransformer

  "A ChargeG Transformer" - {
    "must transform ChargeGDetails from UserAnswers to ETMP ChargeGDetails" in {
      forAll(chargeGUserAnswersGenerator) {
        userAnswersJson =>
          val transformedJson = userAnswersJson.transform(transformer.transformToETMPData).asOpt.value

          def etmpMemberPath(i: Int): JsLookupResult = transformedJson \ "chargeDetails" \ "chargeTypeGDetails" \ "memberDetails" \ i

          def uaMemberPath(i: Int): JsLookupResult = userAnswersJson \ "chargeGDetails" \ "members" \ i

          (etmpMemberPath(0) \ "individualsDetails" \ "firstName").as[String] mustBe (uaMemberPath(0) \ "memberDetails" \ "firstName").as[String]
          (etmpMemberPath(0) \ "individualsDetails" \ "lastName").as[String] mustBe (uaMemberPath(0) \ "memberDetails" \ "lastName").as[String]
          (etmpMemberPath(0) \ "individualsDetails" \ "dateOfBirth").as[String] mustBe (uaMemberPath(0) \ "memberDetails" \ "dob").as[String]
          (etmpMemberPath(0) \ "individualsDetails" \ "nino").as[String] mustBe (uaMemberPath(0) \ "memberDetails" \ "nino").as[String]
          (etmpMemberPath(0) \ "qropsReference").as[String] mustBe s"Q${(uaMemberPath(0) \ "chargeDetails" \ "qropsReferenceNumber").as[String]}"

          (etmpMemberPath(1) \ "individualsDetails" \ "firstName").as[String] mustBe (uaMemberPath(1) \ "memberDetails" \ "firstName").as[String]

          (etmpMemberPath(0) \ "dateOfTransfer").as[String] mustBe (uaMemberPath(0) \ "chargeDetails" \ "qropsTransferDate").as[String]
          (etmpMemberPath(0) \ "amountTransferred").as[BigDecimal] mustBe (uaMemberPath(0) \ "chargeAmounts" \ "amountTransferred").as[BigDecimal]
          (etmpMemberPath(0) \ "amountOfTaxDeducted").as[BigDecimal] mustBe (uaMemberPath(0) \ "chargeAmounts" \ "amountTaxDue").as[BigDecimal]
          (etmpMemberPath(0) \ "memberStatus").as[String] mustBe "New"

          (transformedJson \ "chargeDetails" \ "chargeTypeGDetails" \ "totalAmount").as[BigDecimal] mustBe
            (userAnswersJson \ "chargeGDetails" \ "totalChargeAmount").as[BigDecimal]

          (transformedJson \ "chargeDetails" \ "chargeTypeGDetails" \ "amendedVersion").asOpt[Int] mustBe None

          (transformedJson \ "chargeDetails" \ "chargeTypeGDetails" \ "memberDetails").as[Seq[JsObject]].size mustBe 5
      }
    }

    "must transform optional element - amendedVersion of ChargeGDetails from UserAnswers to ETMP ChargeGDetails" in {
      forAll(chargeGUserAnswersGenerator, arbitrary[Int]) {
        (userAnswersJson, version) =>
          val updatedJson = userAnswersJson.transform(updateJson(__ \ 'chargeGDetails, name = "amendedVersion", version)).asOpt.value
          val transformedJson = updatedJson.transform(transformer.transformToETMPData).asOpt.value

          (transformedJson \ "chargeDetails" \ "chargeTypeGDetails" \ "amendedVersion").as[Int] mustBe
            (updatedJson \ "chargeGDetails" \ "amendedVersion").as[Int]
      }
    }
  }
}

/*
 * Copyright 2022 HM Revenue & Customs
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

class ChargeCTransformerSpec extends AnyFreeSpec with AFTETMPResponseGenerators with OptionValues {

  "A Charge C Transformer" - {

    "must transform member details and total amount for an Individual from ETMP format to UserAnswers format" in {
      forAll(chargeCETMPGenerator) {
        etmpJson =>

          val transformer = new ChargeCTransformer
          val transformedJson = etmpJson.transform(transformer.transformToUserAnswers).asOpt.value

          val uaPath = transformedJson \ "chargeCDetails" \ "employers" \ 0
          val etmpPath = etmpJson \ "chargeTypeCDetails" \ "memberDetails" \ 0

          (uaPath \ "memberStatus").as[String] mustBe (etmpPath \ "memberStatus").as[String]
          (uaPath \ "memberAFTVersion").as[Int] mustBe (etmpPath \ "memberAFTVersion").as[Int]
          (uaPath \ "chargeDetails" \ "paymentDate").as[String] mustBe (etmpPath \ "dateOfPayment").as[String]
          (uaPath \ "chargeDetails" \ "amountTaxDue").as[BigDecimal] mustBe (etmpPath \ "totalAmountOfTaxDue").as[BigDecimal]

          (transformedJson \ "chargeCDetails" \ "amendedVersion").as[Int] mustBe
            (etmpJson \ "chargeTypeCDetails" \ "amendedVersion").as[Int]

          (transformedJson \ "chargeCDetails" \ "totalChargeAmount").as[BigDecimal] mustBe
            (etmpJson \ "chargeTypeCDetails" \ "totalAmount").as[BigDecimal]
      }
    }

    "must transform individual details for an Individual from ETMP format to UserAnswers format" in {
      forAll(chargeCETMPGenerator) {
        etmpJson =>

          val transformer = new ChargeCTransformer
          val transformedJson = etmpJson.transform(transformer.transformToUserAnswers).asOpt.value

          val uaPath = transformedJson \ "chargeCDetails" \ "employers" \ 0 \ "sponsoringIndividualDetails"
          val etmpPath = etmpJson \ "chargeTypeCDetails" \
            "memberDetails" \ 0 \ "memberTypeDetails" \ "individualDetails"

          (uaPath \ "firstName").as[String] mustBe (etmpPath \ "firstName").as[String]
          (uaPath \ "lastName").as[String] mustBe (etmpPath \ "lastName").as[String]
          (uaPath \ "nino").as[String] mustBe (etmpPath \ "nino").as[String]
      }
    }

    "must transform Organisation details for an Organisation from ETMP format to UserAnswers format" in {
      forAll(chargeCETMPGenerator) {
        etmpJson =>

          val transformer = new ChargeCTransformer
          val transformedJson = etmpJson.transform(transformer.transformToUserAnswers).asOpt.value

          val uaPath = transformedJson \ "chargeCDetails" \ "employers" \ 2 \ "sponsoringOrganisationDetails"
          val etmpPath = etmpJson \ "chargeTypeCDetails" \ "memberDetails" \ 2 \ "memberTypeDetails"

          (uaPath \ "name").as[String] mustBe (etmpPath \ "comOrOrganisationName").as[String]
          (uaPath \ "crn").as[String] mustBe (etmpPath \ "crnNumber").as[String]
      }
    }

    "must transform UK correspondence address from ETMP to UserAnswers format" in {
      forAll(chargeCETMPGenerator) {
        etmpJson =>

          val transformer = new ChargeCTransformer
          val transformedJson = etmpJson.transform(transformer.transformToUserAnswers).asOpt.value

          val uaPath = transformedJson \ "chargeCDetails" \ "employers" \ 0 \ "sponsoringEmployerAddress"
          val etmpPath = etmpJson \ "chargeTypeCDetails" \ "memberDetails" \ 0 \ "correspondenceAddressDetails"

          (uaPath \ "line1").as[String] mustBe (etmpPath \ "addressLine1").as[String]
          (uaPath \ "line2").as[String] mustBe (etmpPath \ "addressLine2").as[String]
          (uaPath \ "line3").asOpt[String] mustBe (etmpPath \ "addressLine3").asOpt[String]
          (uaPath \ "line4").asOpt[String] mustBe (etmpPath \ "addressLine4").asOpt[String]
          (uaPath \ "country").as[String] mustBe (etmpPath \ "countryCode").as[String]
          (uaPath \ "postcode").asOpt[String] mustBe (etmpPath \ "postalCode").asOpt[String]
      }
    }
  }
}

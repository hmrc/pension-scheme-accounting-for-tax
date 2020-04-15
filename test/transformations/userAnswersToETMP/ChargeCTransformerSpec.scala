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

class ChargeCTransformerSpec extends FreeSpec with AFTUserAnswersGenerators {

  private val transformer = new ChargeCTransformer
  "A Charge C Transformer" - {

    "must transform member details and total amount for an Individual from UserAnswers format to ETMP format" in {
      forAll(chargeCUserAnswersGenerator) {
        userAnswersJson =>
          val transformedJson = userAnswersJson.transform(transformer.transformToETMPData).asOpt.value

          val etmpMemberDetailsPath = transformedJson \ "chargeDetails" \ "chargeTypeCDetails" \ "memberDetails" \ 0
          val uaChargeDetailsPath = userAnswersJson \ "chargeCDetails" \ "employers" \ 0

          (etmpMemberDetailsPath \ "memberStatus").as[String] mustBe "New"
          (etmpMemberDetailsPath \ "dateOfPayment").as[String] mustBe (uaChargeDetailsPath \ "chargeDetails" \ "paymentDate").as[String]
          (etmpMemberDetailsPath \ "totalAmountOfTaxDue").as[BigDecimal] mustBe (uaChargeDetailsPath \ "chargeDetails" \ "amountTaxDue").as[BigDecimal]

          (transformedJson \ "chargeDetails" \ "chargeTypeCDetails" \ "totalAmount").as[BigDecimal] mustBe
            (userAnswersJson \ "chargeCDetails" \ "totalChargeAmount").as[BigDecimal]

          (transformedJson \ "chargeDetails" \ "chargeTypeCDetails" \ "amendedVersion").asOpt[Int] mustBe None
      }
    }

    "must transform optionalElement - amendedVersion for ChargeCDetails from UserAnswers format to ETMP format" in {
      forAll(chargeCUserAnswersGenerator, arbitrary[Int]) {
        (userAnswersJson, version) =>
          val updatedJson = userAnswersJson.transform(updateJson(__ \ 'chargeCDetails, name = "amendedVersion", version)).asOpt.value
          val transformedJson = updatedJson.transform(transformer.transformToETMPData).asOpt.value

          (transformedJson \ "chargeDetails" \ "chargeTypeCDetails" \ "amendedVersion").as[Int] mustBe
            (updatedJson \ "chargeCDetails" \ "amendedVersion").as[Int]
      }
    }

    "must transform individual details for an Individual from UserAnswers format to ETMP format" in {
      forAll(chargeCUserAnswersGenerator) {
        userAnswersJson =>
          val transformedJson = userAnswersJson.transform(transformer.transformToETMPData).asOpt.value

          val etmpIndividualDetailsPath = transformedJson \ "chargeDetails" \ "chargeTypeCDetails" \
            "memberDetails" \ 0 \ "memberTypeDetails" \ "individualDetails"
          val uaIndividualDetailsPath = userAnswersJson \ "chargeCDetails" \ "employers" \ 0 \ "sponsoringIndividualDetails"

          (etmpIndividualDetailsPath \ "firstName").as[String] mustBe (uaIndividualDetailsPath \ "firstName").as[String]
          (etmpIndividualDetailsPath \ "lastName").as[String] mustBe (uaIndividualDetailsPath \ "lastName").as[String]
          (etmpIndividualDetailsPath \ "nino").as[String] mustBe (uaIndividualDetailsPath \ "nino").as[String]
      }
    }

    "must transform Organisation details for an Organisation from UserAnswers format to ETMP format" in {
      forAll(chargeCUserAnswersGenerator) {
        userAnswersJson =>
          val transformedJson = userAnswersJson.transform(transformer.transformToETMPData).asOpt.value

          val etmpPathAfterFilteredDeleted = transformedJson \ "chargeDetails" \ "chargeTypeCDetails" \
            "memberDetails" \ 3 \ "memberTypeDetails"
          val uaOrgPathWithoutFilter = userAnswersJson \ "chargeCDetails" \ "employers" \ 4 \ "sponsoringOrganisationDetails"

          (etmpPathAfterFilteredDeleted \ "comOrOrganisationName").as[String] mustBe (uaOrgPathWithoutFilter \ "name").as[String]
          (etmpPathAfterFilteredDeleted \ "crnNumber").as[String] mustBe (uaOrgPathWithoutFilter \ "crn").as[String]
      }
    }

    "must transform UK correspondence address from UserAnswers to ETMP format" in {
      forAll(chargeCUserAnswersGenerator) {
        userAnswersJson =>
          val transformedJson = userAnswersJson.transform(transformer.transformToETMPData).asOpt.value

          val etmpAddressPath = transformedJson \ "chargeDetails" \ "chargeTypeCDetails" \ "memberDetails" \ 0 \ "correspondenceAddressDetails"
          val uaAddressPath = userAnswersJson \ "chargeCDetails" \ "employers" \ 0 \ "sponsoringEmployerAddress"

          (etmpAddressPath \ "nonUKAddress").as[String] mustBe "False"
          (etmpAddressPath \ "addressLine1").as[String] mustBe (uaAddressPath \ "line1").as[String]
          (etmpAddressPath \ "addressLine2").as[String] mustBe (uaAddressPath \ "line2").as[String]
          (etmpAddressPath \ "addressLine3").asOpt[String] mustBe (uaAddressPath \ "line3").asOpt[String]
          (etmpAddressPath \ "addressLine4").asOpt[String] mustBe (uaAddressPath \ "line4").asOpt[String]
          (etmpAddressPath \ "countryCode").as[String] mustBe (uaAddressPath \ "country").as[String]
          (etmpAddressPath \ "postalCode").as[String] mustBe (uaAddressPath \ "postcode").as[String]
      }
    }

    "must transform NON UK correspondence address from UserAnswers to ETMP format" in {
      forAll(chargeCUserAnswersGenerator) {
        userAnswersJson =>
          val transformedJson = userAnswersJson.transform(transformer.transformToETMPData).asOpt.value

          val etmpAddressPath = transformedJson \ "chargeDetails" \ "chargeTypeCDetails" \ "memberDetails" \ 3 \ "correspondenceAddressDetails"
          val uaAddressPath = userAnswersJson \ "chargeCDetails" \ "employers" \ 4 \ "sponsoringEmployerAddress"

          (etmpAddressPath \ "nonUKAddress").as[String] mustBe "True"
          (etmpAddressPath \ "addressLine1").as[String] mustBe (uaAddressPath \ "line1").as[String]
          (etmpAddressPath \ "addressLine2").as[String] mustBe (uaAddressPath \ "line2").as[String]
          (etmpAddressPath \ "addressLine3").asOpt[String] mustBe (uaAddressPath \ "line3").asOpt[String]
          (etmpAddressPath \ "addressLine4").asOpt[String] mustBe (uaAddressPath \ "line4").asOpt[String]
          (etmpAddressPath \ "countryCode").as[String] mustBe (uaAddressPath \ "country").as[String]
          (etmpAddressPath \ "postalCode").asOpt[String] mustBe (uaAddressPath \ "postcode").asOpt[String]
      }
    }

    "must filter out the employers with isDeleted flag as true while transforming from UserAnswers to ETMP format" in {
      forAll(chargeCUserAnswersGenerator) {
        userAnswersJson =>
          val transformedJson = userAnswersJson.transform(transformer.transformToETMPData).asOpt.value

          (transformedJson \ "chargeDetails" \ "chargeTypeCDetails" \ "memberDetails").as[Seq[JsObject]].size mustBe 4
      }
    }
  }
}

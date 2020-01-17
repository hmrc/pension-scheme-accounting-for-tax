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

import org.scalatest.FreeSpec
import play.api.libs.json.{JsDefined, JsObject, JsString, Json}
import transformations.generators.AFTUserAnswersGenerators

class ChargeCTransformerSpec extends FreeSpec with AFTUserAnswersGenerators {

  "A Charge C Transformer" - {

    "must transform member details and total amount for an Individual from UserAnswers format to ETMP format" in {
      forAll(chargeCIndividualUserAnswersGenerator) {
        userAnswersJson =>

          val transformer = new ChargeCTransformer
          val transformedJson = userAnswersJson.transform(transformer.transformToETMPData).asOpt.value

          val etmpMemberDetailsPath = transformedJson \ "chargeDetails" \ "chargeTypeCDetails" \ "memberDetails" \ 0
          val uaChargeDetailsPath = userAnswersJson \ "chargeCDetails"

          (etmpMemberDetailsPath \ "memberStatus").as[String] mustBe "New"
          (etmpMemberDetailsPath \ "dateOfPayment").as[String] mustBe (uaChargeDetailsPath \ "chargeDetails" \ "paymentDate").as[String]
          (etmpMemberDetailsPath \ "totalAmountOfTaxDue").as[BigDecimal] mustBe (uaChargeDetailsPath \ "chargeDetails" \ "amountTaxDue").as[BigDecimal]

          (transformedJson \ "chargeDetails" \ "chargeTypeCDetails" \ "totalAmount").as[BigDecimal] mustBe
            (uaChargeDetailsPath \ "chargeDetails" \ "amountTaxDue").as[BigDecimal]
      }
    }

    "must transform individual details for an Individual from UserAnswers format to ETMP format" in {
      forAll(chargeCIndividualUserAnswersGenerator) {
        userAnswersJson =>

          val transformer = new ChargeCTransformer
          val transformedJson = userAnswersJson.transform(transformer.transformToETMPData).asOpt.value

          val etmpIndividualDetailsPath = transformedJson \ "chargeDetails" \ "chargeTypeCDetails" \
            "memberDetails" \ 0 \ "memberTypeDetails" \ "individualDetails"
          val uaIndividualDetailsPath = userAnswersJson \ "chargeCDetails" \ "sponsoringIndividualDetails"

          (etmpIndividualDetailsPath \ "firstName").as[String] mustBe (uaIndividualDetailsPath \ "firstName").as[String]
          (etmpIndividualDetailsPath \ "lastName").as[String] mustBe (uaIndividualDetailsPath \ "lastName").as[String]
          (etmpIndividualDetailsPath \ "nino").as[String] mustBe (uaIndividualDetailsPath \ "nino").as[String]
      }
    }

    "must transform Organisation details for an Organisation from UserAnswers format to ETMP format" in {
      forAll(chargeCCompanyUserAnswersGenerator) {
        userAnswersJson =>

          val transformer = new ChargeCTransformer
          val transformedJson = userAnswersJson.transform(transformer.transformToETMPData).asOpt.value

          val etmpOrgPath = transformedJson \ "chargeDetails" \ "chargeTypeCDetails" \
            "memberDetails" \ 0 \ "memberTypeDetails"
          val uaOrgPath = userAnswersJson \ "chargeCDetails" \ "sponsoringOrganisationDetails"

          (etmpOrgPath \ "comOrOrganisationName").as[String] mustBe (uaOrgPath \ "name").as[String]
          (etmpOrgPath \ "crnNumber").as[String] mustBe (uaOrgPath \ "crn").as[String]
      }
    }

    "must transform UK correspondence address from UserAnswers to ETMP format" in {
      forAll(chargeCIndividualUserAnswersGenerator) {
        userAnswersJson =>

          val transformer = new ChargeCTransformer
          val transformedJson = userAnswersJson.transform(transformer.transformToETMPData).asOpt.value

          val etmpAddressPath = transformedJson \ "chargeDetails" \ "chargeTypeCDetails" \ "memberDetails" \ 0 \ "correspondenceAddressDetails"
          val uaAddressPath = userAnswersJson \ "chargeCDetails" \ "sponsoringEmployerAddress"

          (etmpAddressPath \ "nonUKAddress").as[String] mustBe "False"
          (etmpAddressPath \ "addressLine1").as[String] mustBe (uaAddressPath \ "line1").as[String]
          (etmpAddressPath \ "addressLine2").as[String] mustBe (uaAddressPath \ "line2").as[String]
          (etmpAddressPath \ "addressLine3").asOpt[String] mustBe (uaAddressPath \ "line3").asOpt[String]
          (etmpAddressPath \ "addressLine4").asOpt[String] mustBe (uaAddressPath \ "line4").asOpt[String]
          (etmpAddressPath \ "countryCode").as[String] mustBe (uaAddressPath \ "country").as[String]
          (etmpAddressPath \ "postCode").as[String] mustBe (uaAddressPath \ "postcode").as[String]
      }
    }

    "must transform NON UK correspondence address from UserAnswers to ETMP format" in {
      forAll(chargeCCompanyUserAnswersGenerator) {
        userAnswersJson =>

          val transformer = new ChargeCTransformer
          val transformedJson = userAnswersJson.transform(transformer.transformToETMPData).asOpt.value

          val etmpAddressPath = transformedJson \ "chargeDetails" \ "chargeTypeCDetails" \ "memberDetails" \ 0 \ "correspondenceAddressDetails"
          val uaAddressPath = userAnswersJson \ "chargeCDetails" \ "sponsoringEmployerAddress"

          (etmpAddressPath \ "nonUKAddress").as[String] mustBe "True"
          (etmpAddressPath \ "addressLine1").as[String] mustBe (uaAddressPath \ "line1").as[String]
          (etmpAddressPath \ "addressLine2").as[String] mustBe (uaAddressPath \ "line2").as[String]
          (etmpAddressPath \ "addressLine3").asOpt[String] mustBe (uaAddressPath \ "line3").asOpt[String]
          (etmpAddressPath \ "addressLine4").asOpt[String] mustBe (uaAddressPath \ "line4").asOpt[String]
          (etmpAddressPath \ "countryCode").as[String] mustBe (uaAddressPath \ "country").as[String]
          (etmpAddressPath \ "postCode").asOpt[String] mustBe (uaAddressPath \ "postcode").asOpt[String]
      }
    }

    "must not pass ChargeC to ETMP if total amount is 0" in {
      val userAnswersJson = Json.obj(
        fields = "chargeCDetails" ->
          Json.obj(
            fields = "chargeDetails" -> Json.obj(
              fields = "amountTaxDue" -> 0.00
            )
          ))
      val transformer = new ChargeCTransformer
      val transformedJson = userAnswersJson.transform(transformer.transformToETMPData).asOpt.value

      transformedJson.as[JsObject] mustBe Json.obj()
    }
  }

}

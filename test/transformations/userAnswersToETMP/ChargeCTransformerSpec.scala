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
import transformations.generators.AFTGenerators

class ChargeCTransformerSpec extends FreeSpec with AFTGenerators {

  "A Charge C Transformer" - {

    "must transform ChargeCDetails for an Individual with UK Address from UserAnswers to ETMP ChargeCDetails" in {
      forAll(chargeCIndividualUserAnswersGenerator) {
        userAnswersJson =>

          val transformer = new ChargeCTransformer
          val transformedJson = userAnswersJson.transform(transformer.transformToETMPData).asOpt.value

          transformedJson \ "chargeDetails" \ "chargeTypeCDetails" \ "memberDetails" \ 0 \ "memberStatus" mustBe
            JsDefined(JsString("New"))
          transformedJson \ "chargeDetails" \ "chargeTypeCDetails" \ "memberDetails" \ 0 \ "dateOfPayment" mustBe
            userAnswersJson \ "chargeCDetails" \ "chargeDetails" \ "paymentDate"
          transformedJson \ "chargeDetails" \ "chargeTypeCDetails" \ "memberDetails" \ 0 \ "totalAmountOfTaxDue" mustBe
            userAnswersJson \ "chargeCDetails" \ "chargeDetails" \ "amountTaxDue"

          transformedJson \ "chargeDetails" \ "chargeTypeCDetails" \ "memberDetails" \ 0 \ "memberTypeDetails" \ "individualDetails" \ "firstName" mustBe
            userAnswersJson \ "chargeCDetails" \ "sponsoringIndividualDetails" \ "firstName"
          transformedJson \ "chargeDetails" \ "chargeTypeCDetails" \ "memberDetails" \ 0 \ "memberTypeDetails" \ "individualDetails" \ "lastName" mustBe
            userAnswersJson \ "chargeCDetails" \ "sponsoringIndividualDetails" \ "lastName"
          transformedJson \ "chargeDetails" \ "chargeTypeCDetails" \ "memberDetails" \ 0 \ "memberTypeDetails" \ "individualDetails" \ "nino" mustBe
            userAnswersJson \ "chargeCDetails" \ "sponsoringIndividualDetails" \ "nino"

          transformedJson \ "chargeDetails" \ "chargeTypeCDetails" \ "memberDetails" \ 0 \ "correspondenceAddressDetails" \ "addressLine1" mustBe
            userAnswersJson \ "chargeCDetails" \ "sponsoringEmployerAddress" \ "line1"
          transformedJson \ "chargeDetails" \ "chargeTypeCDetails" \ "memberDetails" \ 0 \ "correspondenceAddressDetails" \ "addressLine2" mustBe
            userAnswersJson \ "chargeCDetails" \ "sponsoringEmployerAddress" \ "line2"
          transformedJson \ "chargeDetails" \ "chargeTypeCDetails" \ "memberDetails" \ 0 \ "correspondenceAddressDetails" \ "addressLine3" mustBe
            userAnswersJson \ "chargeCDetails" \ "sponsoringEmployerAddress" \ "line3"
          transformedJson \ "chargeDetails" \ "chargeTypeCDetails" \ "memberDetails" \ 0 \ "correspondenceAddressDetails" \ "addressLine4" mustBe
            userAnswersJson \ "chargeCDetails" \ "sponsoringEmployerAddress" \ "line4"
          transformedJson \ "chargeDetails" \ "chargeTypeCDetails" \ "memberDetails" \ 0 \ "correspondenceAddressDetails" \ "countryCode" mustBe
            userAnswersJson \ "chargeCDetails" \ "sponsoringEmployerAddress" \ "country"
          transformedJson \ "chargeDetails" \ "chargeTypeCDetails" \ "memberDetails" \ 0 \ "correspondenceAddressDetails" \ "postCode" mustBe
            userAnswersJson \ "chargeCDetails" \ "sponsoringEmployerAddress" \ "postcode"

          transformedJson \ "chargeDetails" \ "chargeTypeCDetails" \ "totalAmount" mustBe
            userAnswersJson \ "chargeCDetails" \ "chargeDetails" \ "amountTaxDue"
      }
    }

    "must transform ChargeCDetails for an Organisation with NON UK Address from UserAnswers to ETMP ChargeCDetails" in {
      forAll(chargeCCompanyUserAnswersGenerator) {
        userAnswersJson =>

          val transformer = new ChargeCTransformer
          val transformedJson = userAnswersJson.transform(transformer.transformToETMPData).asOpt.value

          transformedJson \ "chargeDetails" \ "chargeTypeCDetails" \ "memberDetails" \ 0 \ "memberStatus" mustBe
            JsDefined(JsString("New"))
          transformedJson \ "chargeDetails" \ "chargeTypeCDetails" \ "memberDetails" \ 0 \ "dateOfPayment" mustBe
            userAnswersJson \ "chargeCDetails" \ "chargeDetails" \ "paymentDate"
          transformedJson \ "chargeDetails" \ "chargeTypeCDetails" \ "memberDetails" \ 0 \ "totalAmountOfTaxDue" mustBe
            userAnswersJson \ "chargeCDetails" \ "chargeDetails" \ "amountTaxDue"

          transformedJson \ "chargeDetails" \ "chargeTypeCDetails" \ "memberDetails" \ 0 \ "memberTypeDetails" \ "comOrOrganisationName" mustBe
            userAnswersJson \ "chargeCDetails" \ "sponsoringOrganisationDetails" \ "name"
          transformedJson \ "chargeDetails" \ "chargeTypeCDetails" \ "memberDetails" \ 0 \ "memberTypeDetails" \ "crnNumber" mustBe
            userAnswersJson \ "chargeCDetails" \ "sponsoringOrganisationDetails" \ "crn"

          transformedJson \ "chargeDetails" \ "chargeTypeCDetails" \ "memberDetails" \ 0 \ "correspondenceAddressDetails" \ "addressLine1" mustBe
            userAnswersJson \ "chargeCDetails" \ "sponsoringEmployerAddress" \ "line1"
          transformedJson \ "chargeDetails" \ "chargeTypeCDetails" \ "memberDetails" \ 0 \ "correspondenceAddressDetails" \ "addressLine2" mustBe
            userAnswersJson \ "chargeCDetails" \ "sponsoringEmployerAddress" \ "line2"
          transformedJson \ "chargeDetails" \ "chargeTypeCDetails" \ "memberDetails" \ 0 \ "correspondenceAddressDetails" \ "addressLine3" mustBe
            userAnswersJson \ "chargeCDetails" \ "sponsoringEmployerAddress" \ "line3"
          transformedJson \ "chargeDetails" \ "chargeTypeCDetails" \ "memberDetails" \ 0 \ "correspondenceAddressDetails" \ "addressLine4" mustBe
            userAnswersJson \ "chargeCDetails" \ "sponsoringEmployerAddress" \ "line4"
          transformedJson \ "chargeDetails" \ "chargeTypeCDetails" \ "memberDetails" \ 0 \ "correspondenceAddressDetails" \ "countryCode" mustBe
            userAnswersJson \ "chargeCDetails" \ "sponsoringEmployerAddress" \ "country"
          transformedJson \ "chargeDetails" \ "chargeTypeCDetails" \ "memberDetails" \ 0 \ "correspondenceAddressDetails" \ "postCode" mustBe
            userAnswersJson \ "chargeCDetails" \ "sponsoringEmployerAddress" \ "postcode"

          transformedJson \ "chargeDetails" \ "chargeTypeCDetails" \ "totalAmount" mustBe
            userAnswersJson \ "chargeCDetails" \ "chargeDetails" \ "amountTaxDue"
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

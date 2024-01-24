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
import play.api.libs.json._
import transformations.generators.AFTUserAnswersGenerators

class ChargeCTransformerSpec extends AnyFreeSpec with AFTUserAnswersGenerators with OptionValues {

  private val transformer = new ChargeCTransformer
  "A Charge C Transformer" - {

    "must transform member details and total amount for an Individual from UserAnswers format to ETMP format" in {
      forAll(chargeCUserAnswersGenerator) {
        userAnswersJson =>
          val transformedJson = userAnswersJson.transform(transformer.transformToETMPData).asOpt.value

          val etmpMemberDetailsPath = transformedJson \ "chargeDetails" \ "chargeTypeCDetails" \ "memberDetails" \ 0
          val uaChargeDetailsPath = userAnswersJson \ "chargeCDetails" \ "employers" \ 0

          (etmpMemberDetailsPath \ "dateOfPayment").as[String] mustBe (uaChargeDetailsPath \ "chargeDetails" \ "paymentDate").as[String]
          (etmpMemberDetailsPath \ "totalAmountOfTaxDue").as[BigDecimal] mustBe (uaChargeDetailsPath \ "chargeDetails" \ "amountTaxDue").as[BigDecimal]

          (transformedJson \ "chargeDetails" \ "chargeTypeCDetails" \ "totalAmount").as[BigDecimal] mustBe
            (userAnswersJson \ "chargeCDetails" \ "totalChargeAmount").as[BigDecimal]

          (transformedJson \ "chargeDetails" \ "chargeTypeCDetails" \ "amendedVersion").asOpt[Int] mustBe None

      }
    }

    "must transform optionalElement - amendedVersion, memberAFTVersion and memberStatus for ChargeCDetails from UserAnswers format to ETMP format" in {
      forAll(chargeCUserAnswersGenerator, arbitrary[Int]) {
        (userAnswersJson, version) =>
          val updatedJson = userAnswersJson.transform(updateJson(__ \ Symbol("chargeCDetails"), name = "amendedVersion", version)).asOpt.value
          val transformedJson = updatedJson.transform(transformer.transformToETMPData).asOpt.value

          val etmpMemberDetailsPath = transformedJson \ "chargeDetails" \ "chargeTypeCDetails" \ "memberDetails" \ 0
          val uaChargeDetailsPath = userAnswersJson \ "chargeCDetails" \ "employers" \ 0

          (etmpMemberDetailsPath \ "memberStatus").as[String] mustBe (uaChargeDetailsPath \ "memberStatus").as[String]
          (etmpMemberDetailsPath \ "memberAFTVersion").as[Int] mustBe (uaChargeDetailsPath \ "memberAFTVersion").as[Int]
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

          val etmpPathAfterTransformation = transformedJson \ "chargeDetails" \ "chargeTypeCDetails" \
            "memberDetails" \ 2 \ "memberTypeDetails"
          val uaOrgPathWithoutFilter = userAnswersJson \ "chargeCDetails" \ "employers" \ 2 \ "sponsoringOrganisationDetails"

          (etmpPathAfterTransformation \ "memberStatus").asOpt[String] mustBe None
          (etmpPathAfterTransformation \ "memberAFTVersion").asOpt[String] mustBe None
          (etmpPathAfterTransformation \ "comOrOrganisationName").as[String] mustBe (uaOrgPathWithoutFilter \ "name").as[String]
          (etmpPathAfterTransformation \ "crnNumber").as[String] mustBe (uaOrgPathWithoutFilter \ "crn").as[String]
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

          val etmpAddressPath = transformedJson \ "chargeDetails" \ "chargeTypeCDetails" \ "memberDetails" \ 2 \ "correspondenceAddressDetails"
          val uaAddressPath = userAnswersJson \ "chargeCDetails" \ "employers" \ 2 \ "sponsoringEmployerAddress"

          (etmpAddressPath \ "nonUKAddress").as[String] mustBe "True"
          (etmpAddressPath \ "addressLine1").as[String] mustBe (uaAddressPath \ "line1").as[String]
          (etmpAddressPath \ "addressLine2").as[String] mustBe (uaAddressPath \ "line2").as[String]
          (etmpAddressPath \ "addressLine3").asOpt[String] mustBe (uaAddressPath \ "line3").asOpt[String]
          (etmpAddressPath \ "addressLine4").asOpt[String] mustBe (uaAddressPath \ "line4").asOpt[String]
          (etmpAddressPath \ "countryCode").as[String] mustBe (uaAddressPath \ "country").as[String]
          (etmpAddressPath \ "postalCode").asOpt[String] mustBe (uaAddressPath \ "postcode").asOpt[String]
      }
    }

    "must filter out the employers with memberStatus is not Deleted while transforming from UserAnswers to ETMP format" in {
      forAll(chargeCUserAnswersGenerator) {
        userAnswersJson =>
          val transformedJson = userAnswersJson.transform(transformer.transformToETMPData).asOpt.value

          (transformedJson \ "chargeDetails" \ "chargeTypeCDetails" \ "memberDetails").as[Seq[JsObject]].size mustBe 4
      }
    }
    "must return an empty JsObject when a mandatory field is missing from the UserAnswers json payload" in {
      val transformer = new ChargeCTransformer
      val json = Json.obj(
        fields = "chargeCDetails" ->
          Json.obj(
            "individualEmployers" -> List(chargeCIndividualEmployer.random)
          ))

      val transformedJson = json.transform(transformer.transformToETMPData)
      transformedJson mustBe JsSuccess(Json.obj())
    }

    "must remove any member nodes missing required fields " in {
      val transformer = new ChargeCTransformer

      val json = Json.obj(
        "chargeCDetails" -> Json.obj(
          "employers" -> Json.arr(
            Json.obj(
              "whichTypeOfSponsoringEmployer" -> "individual",
              "memberStatus" -> "changed",
              "memberAFTVersion" -> 1,
              "chargeDetails" -> Json.obj(
                fields = "paymentDate" -> "06/01/2022",
                "amountTaxDue" -> 1.0
              ),
              "sponsoringIndividualDetails" -> Json.obj(
                fields = "firstName" -> "Henry",
                "lastName" -> "Cavill",
                "nino" -> "AA089000A"
              ),
              "sponsoringEmployerAddress" -> Json.obj(
                "line1" -> "1",
                "line2" -> "2",
                "line3" -> "3",
                "line4" -> "4",
                "postcode" -> "m1111m",
                "country" -> "GB"
              )),
            Json.obj(
              "whichTypeOfSponsoringEmployer" -> "organisation",
              "memberStatus" -> "Deleted",
              "memberAFTVersion" -> 1,
              "sponsoringOrganisationDetails" -> Json.obj(
                fields = "name" -> "someOrg",
                "crn" -> "SomeCRN",
              ),
              "sponsoringEmployerAddress" -> Json.obj(
                "line1" -> "1",
                "line2" -> "2",
                "line3" -> "3",
                "line4" -> "4",
                "postcode" -> "m1111m",
                "country" -> "GB"
              ))
          ),
          "totalChargeAmount" -> 100.00
        ))


      val transformedJson = json.transform(transformer.transformToETMPData)

      val expectedJson = Json.obj(
        "chargeDetails" -> Json.obj(
          "chargeTypeCDetails" -> Json.obj(
            "memberDetails" -> Json.arr(
              Json.obj(
                "correspondenceAddressDetails" -> Json.obj(
                  "addressLine1" -> "1",
                  "addressLine2" -> "2",
                  "addressLine3" -> "3",
                  "addressLine4" -> "4",
                  "countryCode" -> "GB",
                  "nonUKAddress" -> "False",
                  "postalCode" -> "m1111m"
                ),
                "memberTypeDetails" -> Json.obj(
                  "memberType" -> "Individual",
                  "individualDetails" -> Json.obj(
                    "firstName" -> "Henry",
                    "lastName" -> "Cavill",
                    "nino" -> "AA089000A"
                  )
                ),
                "dateOfPayment" -> "06/01/2022",
                "memberAFTVersion" -> 1,
                "memberStatus" -> "changed",
                "totalAmountOfTaxDue" -> 1
              )
            ),
            "totalAmount" -> 100.00
          )
        )
      )

      transformedJson mustBe JsSuccess(expectedJson, __ \ "chargeCDetails")
    }
  }
}

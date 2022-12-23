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

package transformations.userAnswersToETMP

import org.scalacheck.Arbitrary.arbitrary
import org.scalatest.freespec.AnyFreeSpec
import play.api.libs.json._
import transformations.generators.AFTUserAnswersGenerators
import org.scalatest.OptionValues
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks.forAll

class ChargeETransformerSpec extends AnyFreeSpec with AFTUserAnswersGenerators with OptionValues {
  private def etmpMemberPath(json: JsObject, i: Int): JsLookupResult = json \ "chargeDetails" \ "chargeTypeEDetails" \ "memberDetails" \ i

  private def uaMemberPath(json: JsObject, i: Int): JsLookupResult = json \ "chargeEDetails" \ "members" \ i

  private def booleanToString(b:Boolean): String = if (b) "Yes" else "No"

  private val transformer = new ChargeETransformer

  "A Charge E Transformer" - {
    "must filter out the members with memberStatus not Deleted" +
      "also transform all elements of ChargeEDetails from UserAnswers to ETMP" in {
      forAll(chargeEUserAnswersGenerator) {
        userAnswersJson =>
          val transformedJson = userAnswersJson.transform(transformer.transformToETMPData).asOpt.value

          (etmpMemberPath(transformedJson, 0) \ "individualsDetails" \ "firstName").as[String] mustBe
            (uaMemberPath(userAnswersJson, 0) \ "memberDetails" \ "firstName").as[String]
          (etmpMemberPath(transformedJson, 0) \ "individualsDetails" \ "lastName").as[String] mustBe
            (uaMemberPath(userAnswersJson, 0) \ "memberDetails" \ "lastName").as[String]
          (etmpMemberPath(transformedJson, 0) \ "individualsDetails" \ "nino").as[String] mustBe
            (uaMemberPath(userAnswersJson, 0) \ "memberDetails" \ "nino").as[String]


          (etmpMemberPath(transformedJson, 1) \ "individualsDetails" \ "firstName").as[String] mustBe
            (uaMemberPath(userAnswersJson, 1) \ "memberDetails" \ "firstName").as[String]

          (etmpMemberPath(transformedJson, 0) \ "amountOfCharge").as[BigDecimal] mustBe
            (uaMemberPath(userAnswersJson, 0) \ "chargeDetails" \ "chargeAmount").as[BigDecimal]
          (etmpMemberPath(transformedJson, 0) \ "dateOfNotice").as[String] mustBe
            (uaMemberPath(userAnswersJson, 0) \ "chargeDetails" \ "dateNoticeReceived").as[String]
          (etmpMemberPath(transformedJson, 0) \ "paidUnder237b").as[String] mustBe
            booleanToString ((uaMemberPath(userAnswersJson, 0) \ "chargeDetails" \ "isPaymentMandatory").as[Boolean])
          (etmpMemberPath(transformedJson, 0) \ "taxYearEnding").as[String] mustBe (uaMemberPath(userAnswersJson, 0) \ "annualAllowanceYear").as[String]

          (etmpMemberPath(transformedJson, 0) \ "memberStatus").as[String] mustBe (uaMemberPath(userAnswersJson, 0) \ "memberStatus").as[String]
          (etmpMemberPath(transformedJson, 0) \ "memberAFTVersion").as[Int] mustBe (uaMemberPath(userAnswersJson, 0) \ "memberAFTVersion").as[Int]

          (transformedJson \ "chargeDetails" \ "chargeTypeEDetails" \ "totalAmount").as[BigDecimal] mustBe
            (userAnswersJson \ "chargeEDetails" \ "totalChargeAmount").as[BigDecimal]

          (transformedJson \ "chargeDetails" \ "chargeTypeEDetails" \ "amendedVersion").asOpt[Int] mustBe None

          (transformedJson \ "chargeDetails" \ "chargeTypeEDetails" \ "memberDetails").as[Seq[JsObject]].size mustBe 6

          // MCCLOUD
          (etmpMemberPath(transformedJson, 0) \ "anAllowanceChgPblSerRem").as[String] mustBe
            booleanToString ((uaMemberPath(userAnswersJson, 0) \ "mccloudRemedy" \ "isPublicServicePensionsRemedy").as[Boolean])

          (etmpMemberPath(transformedJson, 0) \ "orChgPaidbyAnoPS").as[String] mustBe
            booleanToString((uaMemberPath(userAnswersJson, 0) \ "mccloudRemedy" \ "wasAnotherPensionScheme").as[Boolean])

      }
    }

    "must transform optional elements - amendedVersion, memberStatus and memberAFTVersion of ChargeEDetails from UserAnswers to ETMP" in {
      forAll(chargeEUserAnswersGenerator, arbitrary[Int], arbitrary[String]) {
        (userAnswersJson, version, _) =>

          val jsonTransformer = (__ \ Symbol("chargeEDetails")).json.pickBranch(
            __.json.update(
              __.read[JsObject].map(o => o ++ Json.obj("amendedVersion" -> version))
            ) andThen
              (__ \ Symbol("members")).json.update(
                __.read[JsArray].map {
                  case JsArray(arr) => JsArray(Seq(arr.head.as[JsObject] - "memberAFTVersion" - "memberStatus") ++ arr.tail)
                })
          )
          val updatedJson = userAnswersJson.transform(jsonTransformer).asOpt.value
          val transformedJson = updatedJson.transform(transformer.transformToETMPData).asOpt.value

          (transformedJson \ "chargeDetails" \ "chargeTypeEDetails" \ "amendedVersion").as[Int] mustBe
            (updatedJson \ "chargeEDetails" \ "amendedVersion").as[Int]

          (etmpMemberPath(transformedJson, 0) \ "memberStatus").as[String] mustBe "New"

          (etmpMemberPath(transformedJson, 0) \ "memberAFTVersion").asOpt[Int] mustBe None
      }
    }

    "must return an empty JsObject when a mandatory field is missing from the UserAnswers json payload" in {
      val transformer = new ChargeETransformer
      val json = Json.obj(
        fields = "chargeEDetails" ->
          Json.obj(
            "members" -> chargeEMember("testStatus").random
          ))
      val transformedJson = json.transform(transformer.transformToETMPData)

      transformedJson mustBe JsSuccess(Json.obj())
    }

    "must remove any member nodes missing required fields" in {
      val transformer = new ChargeETransformer

      val json = Json.obj(
        "chargeEDetails" -> Json.obj(
          "members" -> Json.arr(
            Json.obj(
              "memberStatus" -> "Changed",
              "memberAFTVersion" -> 1,
              "annualAllowanceYear" -> "2020",
              "memberDetails" -> Json.obj(
                "firstName" -> "Joy",
                "lastName" -> "Kenneth",
                "nino" -> "AA089000A"
              )
            ),
            Json.obj(
              "memberStatus" -> "Changed",
              "memberAFTVersion" -> 1,
              "annualAllowanceYear" -> "2020",
              "memberDetails" -> Json.obj(
                "firstName" -> "Roy",
                "lastName" -> "Renneth",
                "nino" -> "AA089000A"
              ),
              "chargeDetails" -> Json.obj(
                "dateNoticeReceived" -> "2020-01-11",
                "chargeAmount" -> 200.02,
                "isPaymentMandatory" -> true
              )
            )
          ),
          "totalChargeAmount" -> 2345.02
        )
      )

      val transformedJson = json.transform(transformer.transformToETMPData)

      val expectedJson = Json.obj(
        "chargeDetails" -> Json.obj(
          "chargeTypeEDetails" -> Json.obj(
            "memberDetails" -> Json.arr(
              Json.obj(
                "individualsDetails" -> Json.obj(
                  "firstName" -> "Roy",
                  "lastName" -> "Renneth",
                  "nino" -> "AA089000A"
                ),
                "dateOfNotice" -> "2020-01-11",
                "taxYearEnding" -> "2020",
                "amountOfCharge" -> 200.02,
                "paidUnder237b" -> "Yes",
                "memberStatus" -> "Changed",
                "memberAFTVersion" -> 1,
              )
            ),
            "totalAmount" -> 2345.02
          )
        )
      )

      transformedJson mustBe JsSuccess(expectedJson, __ \ "chargeEDetails")
    }
  }
}

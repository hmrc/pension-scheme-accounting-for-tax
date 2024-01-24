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

import models.Scheme
import org.scalacheck.Arbitrary.arbitrary
import org.scalatest.OptionValues
import org.scalatest.freespec.AnyFreeSpec
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks.forAll
import play.api.libs.json.Reads._
import play.api.libs.json._
import transformations.generators.AFTUserAnswersGenerators

class ChargeDTransformerSpec extends AnyFreeSpec with AFTUserAnswersGenerators with OptionValues {

  private val transformer = new ChargeDTransformer
  private def booleanToString(b: Boolean): String = if (b) "Yes" else "No"

  "A Charge D Transformer" - {
    "must filter out the members with memberStatus not Deleted" +
      "also transform mandatory elements of ChargeDDetails from UserAnswers to ETMP" in {
      forAll(chargeDUserAnswersGenerator) {
        userAnswersJson =>
          val transformedJson = userAnswersJson.transform(transformer.transformToETMPData).asOpt.value

          def etmpMemberPath(i: Int): JsLookupResult = transformedJson \ "chargeDetails" \ "chargeTypeDDetails" \ "memberDetails" \ i

          def uaMemberPath(i: Int): JsLookupResult = userAnswersJson \ "chargeDDetails" \ "members" \ i

          (etmpMemberPath(0) \ "individualsDetails" \ "firstName").as[String] mustBe (uaMemberPath(0) \ "memberDetails" \ "firstName").as[String]
          (etmpMemberPath(0) \ "individualsDetails" \ "lastName").as[String] mustBe (uaMemberPath(0) \ "memberDetails" \ "lastName").as[String]
          (etmpMemberPath(0) \ "individualsDetails" \ "nino").as[String] mustBe (uaMemberPath(0) \ "memberDetails" \ "nino").as[String]

          (etmpMemberPath(0) \ "dateOfBeneCrysEvent").as[String] mustBe (uaMemberPath(0) \ "chargeDetails" \ "dateOfEvent").as[String]
          (etmpMemberPath(0) \ "totalAmtOfTaxDueAtLowerRate").as[BigDecimal] mustBe (uaMemberPath(0) \ "chargeDetails" \ "taxAt25Percent").as[BigDecimal]
          (etmpMemberPath(0) \ "totalAmtOfTaxDueAtHigherRate").as[BigDecimal] mustBe (uaMemberPath(0) \ "chargeDetails" \ "taxAt55Percent").as[BigDecimal]
          (etmpMemberPath(0) \ "memberStatus").as[String] mustBe (uaMemberPath(0) \ "memberStatus").as[String]
          (etmpMemberPath(0) \ "memberAFTVersion").as[Int] mustBe (uaMemberPath(0) \ "memberAFTVersion").as[Int]

          (etmpMemberPath(1) \ "individualsDetails" \ "firstName").as[String] mustBe (uaMemberPath(1) \ "memberDetails" \ "firstName").as[String]

          (transformedJson \ "chargeDetails" \ "chargeTypeDDetails" \ "totalAmount").as[BigDecimal] mustBe
            (userAnswersJson \ "chargeDDetails" \ "totalChargeAmount").as[BigDecimal]

          (transformedJson \ "chargeDetails" \ "chargeTypeDDetails" \ "amendedVersion").asOpt[Int] mustBe None

          (transformedJson \ "chargeDetails" \ "chargeTypeDDetails" \ "memberDetails").as[Seq[JsObject]].size mustBe 6


          val isMcCloudRemedyUA = {
            val isMcCloud = (uaMemberPath(0) \ "mccloudRemedy" \ "isPublicServicePensionsRemedy").as[Boolean]
            val isInAddition = (uaMemberPath(0) \ "mccloudRemedy" \ "isChargeInAdditionReported").as[Boolean]
            isMcCloud && isInAddition
          }


          // MCCLOUD REMEDY
          (etmpMemberPath(0) \ "lfAllowanceChgPblSerRem").as[String] mustBe booleanToString(isMcCloudRemedyUA)
          if (isMcCloudRemedyUA) {
            val isOtherSchemes = (uaMemberPath(0) \ "mccloudRemedy" \ "wasAnotherPensionScheme").as[Boolean]
            (etmpMemberPath(0) \ "orLfChgPaidbyAnoPS").as[String] mustBe booleanToString(isOtherSchemes)

            if (isOtherSchemes) {
              val uaSeqSchemes = (uaMemberPath(0) \ "mccloudRemedy" \ "schemes").as[Seq[Scheme]]
              val transformedSchemes: Seq[JsValue] = (etmpMemberPath(0) \ "pensionSchemeDetails").as[JsArray].value.toSeq
              uaSeqSchemes.zipWithIndex.foreach { case (uaScheme, i) =>
                (transformedSchemes(i) \ "pstr").asOpt[String] mustBe uaScheme.pstr
                (transformedSchemes(i) \ "repPeriodForLtac").asOpt[String] mustBe Some(uaScheme.taxQuarterReportedAndPaid.endDate)
                (transformedSchemes(i) \ "amtOrRepLtaChg").asOpt[BigDecimal] mustBe Some(uaScheme.chargeAmountReported)
              }
            } else {
              val uaScheme = (uaMemberPath(0) \ "mccloudRemedy").as[Scheme]
              val transformedSchemes = (etmpMemberPath(0) \ "pensionSchemeDetails").as[JsArray].value.toSeq
              transformedSchemes.headOption.flatMap(jsValue =>
                (jsValue \ "repPeriodForLtac").asOpt[String]) mustBe Some(uaScheme.taxQuarterReportedAndPaid.endDate)
              transformedSchemes.headOption.flatMap(jsValue =>
                (jsValue \ "amtOrRepLtaChg").asOpt[BigDecimal]) mustBe Some(uaScheme.chargeAmountReported)
            }
          } else {
            assert(true)
          }

      }
    }

    "must transform optional element - amendedVersion, memberStatus and memberAFTVersion of ChargeDDetails from UserAnswers to ETMP" in {
      forAll(chargeDUserAnswersGenerator, arbitrary[Int]) {
        (userAnswersJson, version) =>
          val jsonTransformer = (__ \ Symbol("chargeDDetails")).json.pickBranch(
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

          (transformedJson \ "chargeDetails" \ "chargeTypeDDetails" \ "amendedVersion").as[Int] mustBe
            (updatedJson \ "chargeDDetails" \ "amendedVersion").as[Int]

          (transformedJson \ "chargeDetails" \ "chargeTypeDDetails" \ "memberDetails" \ 0 \ "memberStatus").as[String] mustBe "New"

          (transformedJson \ "chargeDetails" \ "chargeTypeDDetails" \ "memberDetails" \ 0 \ "memberAFTVersion").asOpt[Int] mustBe None
      }
    }

    "must return an empty JsObject when a mandatory field is missing from the UserAnswers json payload" in {
      val transformer = new ChargeDTransformer
      val json = Json.obj(
        fields = "chargeDDetails" ->
          Json.obj(
            "members" -> List(chargeDMember("testStatus").random)
          ))

      val transformedJson = json.transform(transformer.transformToETMPData)
      transformedJson mustBe JsSuccess(Json.obj())
    }

    "must remove any member nodes missing required fields" in {
      val transformer = new ChargeDTransformer

      val json = Json.obj(
        "chargeDDetails" -> Json.obj(
          "members" -> Json.arr(
            Json.obj(
              "memberStatus" -> "Changed",
              "memberAFTVersion" -> 1,
              "memberDetails" -> Json.obj(
                "firstName" -> "Joy",
                "lastName" -> "Kenneth",
                "nino" -> "AA089000A"
              )
            ),
            Json.obj(
              "memberStatus" -> "Changed",
              "memberAFTVersion" -> 1,
              "memberDetails" -> Json.obj(
                "firstName" -> "Roy",
                "lastName" -> "Renneth",
                "nino" -> "AA089000A"
              ),
              "chargeDetails" -> Json.obj(
                "dateOfEvent" -> "2016-02-29",
                "taxAt25Percent" -> 1.02,
                "taxAt55Percent" -> 9.02
              )
            )
          ),
          "totalChargeAmount" -> 2345.02,
          "amendedVersion" -> 1
        )
      )

      val transformedJson = json.transform(transformer.transformToETMPData)

      val expectedJson = Json.obj(
        "chargeDetails" -> Json.obj(
          "chargeTypeDDetails" -> Json.obj(
            "memberDetails" -> Json.arr(
              Json.obj(
                "totalAmtOfTaxDueAtHigherRate" -> 9.02,
                "individualsDetails" -> Json.obj(
                  "firstName" -> "Roy",
                  "lastName" -> "Renneth",
                  "nino" -> "AA089000A"
                ),
                "memberStatus" -> "Changed",
                "totalAmtOfTaxDueAtLowerRate" -> 1.02,
                "memberAFTVersion" -> 1,
                "dateOfBeneCrysEvent" -> "2016-02-29",
                "lfAllowanceChgPblSerRem" -> "No"
              )
            ),
            "totalAmount" -> 2345.02,
            "amendedVersion" -> 1,
          )
        )
      )

      transformedJson mustBe JsSuccess(expectedJson, __ \ "chargeDDetails")
    }
  }
}

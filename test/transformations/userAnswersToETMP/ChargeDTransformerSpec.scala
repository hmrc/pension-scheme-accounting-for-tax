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
import play.api.libs.json.Reads._
import play.api.libs.json.{__, _}
import transformations.generators.AFTUserAnswersGenerators
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks.forAll
import org.scalatest.OptionValues

class ChargeDTransformerSpec extends AnyFreeSpec with AFTUserAnswersGenerators with OptionValues {

  private val transformer = new ChargeDTransformer

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
      }
    }

    "must transform optional element - amendedVersion, memberStatus and memberAFTVersion of ChargeDDetails from UserAnswers to ETMP" in {
      forAll(chargeDUserAnswersGenerator, arbitrary[Int]) {
        (userAnswersJson, version) =>
          val jsonTransformer = (__ \ 'chargeDDetails).json.pickBranch(
            __.json.update(
              __.read[JsObject].map(o => o ++ Json.obj("amendedVersion" -> version))
            ) andThen
              (__ \ 'members).json.update(
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
  }
}

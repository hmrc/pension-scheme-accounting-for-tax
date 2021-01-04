/*
 * Copyright 2021 HM Revenue & Customs
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

class ChargeETransformerSpec extends FreeSpec with AFTUserAnswersGenerators {
  private def etmpMemberPath(json: JsObject, i: Int): JsLookupResult = json \ "chargeDetails" \ "chargeTypeEDetails" \ "memberDetails" \ i

  private def uaMemberPath(json: JsObject, i: Int): JsLookupResult = json \ "chargeEDetails" \ "members" \ i

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
            (if ((uaMemberPath(userAnswersJson, 0) \ "chargeDetails" \ "isPaymentMandatory").as[Boolean]) "Yes" else "No")
          (etmpMemberPath(transformedJson, 0) \ "taxYearEnding").as[String] mustBe (uaMemberPath(userAnswersJson, 0) \ "annualAllowanceYear").as[String]

          (etmpMemberPath(transformedJson, 0) \ "memberStatus").as[String] mustBe (uaMemberPath(userAnswersJson, 0) \ "memberStatus").as[String]
          (etmpMemberPath(transformedJson, 0) \ "memberAFTVersion").as[Int] mustBe (uaMemberPath(userAnswersJson, 0) \ "memberAFTVersion").as[Int]

          (transformedJson \ "chargeDetails" \ "chargeTypeEDetails" \ "totalAmount").as[BigDecimal] mustBe
            (userAnswersJson \ "chargeEDetails" \ "totalChargeAmount").as[BigDecimal]

          (transformedJson \ "chargeDetails" \ "chargeTypeEDetails" \ "amendedVersion").asOpt[Int] mustBe None

          (transformedJson \ "chargeDetails" \ "chargeTypeEDetails" \ "memberDetails").as[Seq[JsObject]].size mustBe 6
      }
    }

    "must transform optional elements - amendedVersion, memberStatus and memberAFTVersion of ChargeEDetails from UserAnswers to ETMP" in {
      forAll(chargeEUserAnswersGenerator, arbitrary[Int], arbitrary[String]) {
        (userAnswersJson, version, status) =>

          val jsonTransformer = (__ \ 'chargeEDetails).json.pickBranch(
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

          (transformedJson \ "chargeDetails" \ "chargeTypeEDetails" \ "amendedVersion").as[Int] mustBe
            (updatedJson \ "chargeEDetails" \ "amendedVersion").as[Int]

          (etmpMemberPath(transformedJson, 0) \ "memberStatus").as[String] mustBe "New"

          (etmpMemberPath(transformedJson, 0) \ "memberAFTVersion").asOpt[Int] mustBe None
      }
    }
  }
}

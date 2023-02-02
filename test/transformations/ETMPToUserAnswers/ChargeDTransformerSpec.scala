/*
 * Copyright 2023 HM Revenue & Customs
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
import play.api.libs.json.{JsLookupResult, JsObject}
import transformations.generators.AFTETMPResponseGenerators

class ChargeDTransformerSpec extends AnyFreeSpec with AFTETMPResponseGenerators with OptionValues {

  private def yesNoToOptBoolean(yesNo: String): Option[Boolean] = yesNo match {
    case "Yes" => Some(true)
    case "No" => Some(false)
    case _ => None
  }

  private def optYesNoToOptBoolean(yesNo: Option[String]): Option[Boolean] = yesNo match {
    case Some("Yes") => Some(true)
    case Some("No") => Some(false)
    case _ => None
  }

  "A Charge D Transformer must" - {

    "must transform ChargeDDetails from ETMP format to UserAnswers format" in {
      forAll(chargeDETMPGenerator) {
        etmpResponseJson =>
          val transformer = new ChargeDTransformer
          val transformedJson = etmpResponseJson.transform(transformer.transformToUserAnswers).asOpt.value

          def membersUAPath(i: Int): JsLookupResult = transformedJson \ "chargeDDetails" \ "members" \ i

          def membersETMPPath(i: Int): JsLookupResult = etmpResponseJson \ "chargeTypeDDetails" \ "memberDetails" \ i

          (membersUAPath(0) \ "memberDetails" \ "firstName").as[String] mustBe (membersETMPPath(0) \ "individualsDetails" \ "firstName").as[String]
          (membersUAPath(0) \ "memberDetails" \ "lastName").as[String] mustBe (membersETMPPath(0) \ "individualsDetails" \ "lastName").as[String]
          (membersUAPath(0) \ "memberDetails" \ "nino").as[String] mustBe (membersETMPPath(0) \ "individualsDetails" \ "nino").as[String]

          (membersUAPath(0) \ "memberStatus").as[String] mustBe (membersETMPPath(0) \ "memberStatus").as[String]
          (membersUAPath(0) \ "memberAFTVersion").as[Int] mustBe (membersETMPPath(0) \ "memberAFTVersion").as[Int]
          (membersUAPath(0) \ "chargeDetails" \ "dateOfEvent").as[String] mustBe (membersETMPPath(0) \ "dateOfBenefitCrystalizationEvent").as[String]
          (membersUAPath(0) \ "chargeDetails" \ "taxAt25Percent").as[BigDecimal] mustBe (membersETMPPath(0) \ "totalAmtOfTaxDueAtLowerRate").as[BigDecimal]
          (membersUAPath(0) \ "chargeDetails" \ "taxAt55Percent").as[BigDecimal] mustBe (membersETMPPath(0) \ "totalAmtOfTaxDueAtHigherRate").as[BigDecimal]

          val isMcCloudRem = (membersUAPath(0) \ "mccloudRemedy" \ "isPublicServicePensionsRemedy").as[Boolean]
          Some(isMcCloudRem) mustBe yesNoToOptBoolean( (membersETMPPath(0) \ "lfAllowanceChgPblSerRem").as[String])
          if (isMcCloudRem) {
            val areMorePensions =  (membersUAPath(0) \ "mccloudRemedy" \ "wasAnotherPensionScheme").asOpt[Boolean]
            areMorePensions mustBe optYesNoToOptBoolean( (membersETMPPath(0) \ "orLfChgPaidbyAnoPS").asOpt[String])
            areMorePensions match {
              case Some(true) =>
                (membersUAPath(0) \ "mccloudRemedy" \ "schemes" \ 0 \ "pstr" ).as[String] mustBe
                  (membersETMPPath(0) \ "pensionSchemeDetails" \ 0 \ "pstr").as[String]
                (membersUAPath(0) \ "mccloudRemedy" \ "schemes" \ 0 \ "chargeAmountReported").as[BigDecimal] mustBe
                  (membersETMPPath(0) \ "pensionSchemeDetails" \ 0 \ "amtOrRepLtaChg").as[BigDecimal]
                (membersUAPath(0) \ "mccloudRemedy" \ "isChargeInAdditionReported").as[Boolean] mustBe true

              case Some(false) =>
                (membersUAPath(0) \ "mccloudRemedy"\ "chargeAmountReported").as[BigDecimal] mustBe
                  (membersETMPPath(0) \ "pensionSchemeDetails" \ 0 \ "amtOrRepLtaChg").as[BigDecimal]
                (membersUAPath(0) \ "mccloudRemedy" \ "isChargeInAdditionReported").as[Boolean] mustBe true

              case None =>
                (membersUAPath(0) \ "mccloudRemedy" \ "isChargeInAdditionReported").as[Boolean] mustBe false
            }
          }

          (transformedJson \ "chargeDDetails" \ "totalChargeAmount").as[BigDecimal] mustBe
            (etmpResponseJson \ "chargeTypeDDetails" \ "totalAmount").as[BigDecimal]

          (transformedJson \ "chargeDDetails" \ "amendedVersion").as[Int] mustBe
            (etmpResponseJson \ "chargeTypeDDetails" \ "amendedVersion").as[Int]

          (transformedJson \ "chargeDDetails" \ "members").as[Seq[JsObject]].size mustBe 2
      }
    }
  }
}

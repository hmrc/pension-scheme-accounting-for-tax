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

import java.time.LocalDate

class ChargeETransformerSpec extends AnyFreeSpec with AFTETMPResponseGenerators with OptionValues {

  private def optYesNoToOptBoolean(yesNo: Option[String]): Option[Boolean] = yesNo match {
    case Some("Yes") => Some(true)
    case Some("No") => Some(false)
    case _ => None
  }
  "A Charge E Transformer" - {
    "must transform ChargeEDetails from ETMP ChargeEDetails to UserAnswers" in {
      forAll(chargeEETMPGenerator) {
        etmpResponseJson =>
          val transformer = new ChargeETransformer
          val transformedJson = etmpResponseJson.transform(transformer.transformToUserAnswers).asOpt.value

          def membersUAPath(i: Int): JsLookupResult = transformedJson \ "chargeEDetails" \ "members" \ i

          def membersETMPPath(i: Int): JsLookupResult = etmpResponseJson \ "chargeTypeEDetails" \ "memberDetails" \ i

          (membersUAPath(0) \ "memberStatus").as[String] mustBe (membersETMPPath(0) \ "memberStatus").as[String]
          (membersUAPath(0) \ "memberAFTVersion").as[Int] mustBe (membersETMPPath(0) \ "memberAFTVersion").as[Int]
          (membersUAPath(0) \ "memberDetails" \ "firstName").as[String] mustBe (membersETMPPath(0) \ "individualsDetails" \ "firstName").as[String]
          (membersUAPath(0) \ "memberDetails" \ "lastName").as[String] mustBe (membersETMPPath(0) \ "individualsDetails" \ "lastName").as[String]
          (membersUAPath(0) \ "memberDetails" \ "nino").as[String] mustBe (membersETMPPath(0) \ "individualsDetails" \ "nino").as[String]

          (membersUAPath(0) \ "chargeDetails" \ "chargeAmount").as[BigDecimal] mustBe (membersETMPPath(0) \ "amountOfCharge").as[BigDecimal]
          (membersUAPath(0) \ "chargeDetails" \ "dateNoticeReceived").as[LocalDate] mustBe (membersETMPPath(0) \ "dateOfNotice").as[LocalDate]
          (membersUAPath(0) \ "chargeDetails" \ "isPaymentMandatory").as[Boolean] mustBe (membersETMPPath(0) \ "paidUnder237b").get.as[String].equals("Yes")


          (membersUAPath(0) \ "annualAllowanceYear").as[Int] mustBe (membersETMPPath(0) \ "taxYearEnding").as[Int]

          /*
          isPSRNodeName = "anAllowanceChgPblSerRem", isOtherSchemesNodeName = "orChgPaidbyAnoPS",
                  amountNodeName = "amtOrRepAaChg", repoPeriodNodeName = "repPeriodForAac"
           */


          val isMcCloudRem = (membersUAPath(0) \ "mccloudRemedy" \ "isPublicServicePensionsRemedy").asOpt[Boolean]
         isMcCloudRem match {
           case Some(true) =>
             val areMorePensions = (membersUAPath(0) \ "mccloudRemedy" \ "wasAnotherPensionScheme").asOpt[Boolean]
             areMorePensions mustBe optYesNoToOptBoolean((membersETMPPath(0) \ "orChgPaidbyAnoPS").asOpt[String])
             areMorePensions match {
               case Some(true) =>
                 (membersUAPath(0) \ "mccloudRemedy" \ "schemes" \ 0 \ "pstr").as[String] mustBe
                   (membersETMPPath(0) \ "pensionSchemeDetails" \ 0 \ "pstr").as[String]
                 (membersUAPath(0) \ "mccloudRemedy" \ "schemes" \ 0 \ "chargeAmountReported").as[BigDecimal] mustBe
                   (membersETMPPath(0) \ "pensionSchemeDetails" \ 0 \ "amtOrRepAaChg").as[BigDecimal]
                 (membersUAPath(0) \ "mccloudRemedy" \ "schemes" \ 0 \"taxYearReportedAndPaid" \ "endDate").as[String] mustBe
                   (membersETMPPath(0) \ "pensionSchemeDetails" \ 0 \ "repPeriodForAac").as[String]
                 (membersUAPath(0) \ "mccloudRemedy" \ "isChargeInAdditionReported").as[Boolean] mustBe true

               case Some(false) =>
                 (membersUAPath(0) \ "mccloudRemedy" \ "chargeAmountReported").as[BigDecimal] mustBe
                   (membersETMPPath(0) \ "pensionSchemeDetails" \ 0 \ "amtOrRepAaChg").as[BigDecimal]
                 (membersUAPath(0) \ "mccloudRemedy" \ "taxYearReportedAndPaid" \ "endDate").as[String] mustBe
                   (membersETMPPath(0) \ "pensionSchemeDetails" \ 0 \ "repPeriodForAac").as[String]
                 (membersUAPath(0) \ "mccloudRemedy" \ "isChargeInAdditionReported").as[Boolean] mustBe true

               case None =>
                 (membersUAPath(0) \ "mccloudRemedy" \ "isChargeInAdditionReported").as[Boolean] mustBe false
             }
           case Some(false) => (membersETMPPath(0) \ "anAllowanceChgPblSerRem").asOpt[String] mustBe Some("No")
           case _ => (membersETMPPath(0) \ "anAllowanceChgPblSerRem").asOpt[String] mustBe None
         }







          (transformedJson \ "chargeEDetails" \ "totalChargeAmount").as[BigDecimal] mustBe
            (etmpResponseJson \ "chargeTypeEDetails" \ "totalAmount").as[BigDecimal]

          (transformedJson \ "chargeEDetails" \ "amendedVersion").as[Int] mustBe
            (etmpResponseJson \ "chargeTypeEDetails" \ "amendedVersion").as[Int]

          (membersUAPath(1) \ "memberDetails" \ "firstName").as[String] mustBe (membersETMPPath(1) \ "individualsDetails" \ "firstName").as[String]

          (transformedJson \ "chargeEDetails" \ "members").as[Seq[JsObject]].size mustBe 2
      }
    }
  }

}

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

package services

import org.scalatest.{MustMatchers, WordSpec}
import play.api.libs.json.{JsObject, Json}

class AFTServiceSpec extends WordSpec with MustMatchers {

  import AFTServiceSpec._

  val aftService = new AFTService

  "isOnlyOneChargeWithNoValue" must {

    memberBasedChargeCreationFunctions
      .zipWithIndex.foreach { case (createChargeSection, chargeSectionIndex) =>
      s"return true where there is a charge of type ${memberBasedChargeNames(chargeSectionIndex)} with a " +
        s"value of zero AND NO OTHER CHARGES" in {
        aftService.isChargeZeroedOut(
          createAFTDetailsResponse(createChargeSection(zeroCurrencyValue))) mustBe true

      }

      s"return false where there is a charge of type ${memberBasedChargeNames(chargeSectionIndex)} with a " +
        s"value of non-zero AND NO OTHER CHARGES" in {
        aftService.isChargeZeroedOut(
          createAFTDetailsResponse(createChargeSection(nonZeroCurrencyValue))) mustBe false

      }
    }

    schemeLevelChargeCreationFunctions
      .zipWithIndex.foreach { case (createChargeSection, chargeSectionIndex) =>
      s"return true where there is a charge of type ${nonMemberBasedChargeNames(chargeSectionIndex)} with a " +
        s"value of zero AND NO OTHER CHARGES" in {
        aftService.isChargeZeroedOut(
          createAFTDetailsResponse(createChargeSection(zeroCurrencyValue))) mustBe true

      }

      s"return false where there is a charge of type ${nonMemberBasedChargeNames(chargeSectionIndex)} with a " +
        s"value of non-zero AND NO OTHER CHARGES" in {
        aftService.isChargeZeroedOut(
          createAFTDetailsResponse(createChargeSection(nonZeroCurrencyValue))) mustBe false

      }
    }

    nonMemberBasedChargeSections
      .zipWithIndex.foreach { case (nonMemberBasedChargeSection, nonMemberBasedChargeSectionIndex) =>
      memberBasedChargeCreationFunctions
        .zipWithIndex.foreach { case (createChargeSection, chargeSectionIndex) =>
        s"return false where there is a charge of type ${memberBasedChargeNames(chargeSectionIndex)} with a " +
          s"value of zero BUT also a value in another non-member-based charge (${nonMemberBasedChargeNames(nonMemberBasedChargeSectionIndex)}})" in {

          val result = aftService.isChargeZeroedOut(
            createAFTDetailsResponse(createChargeSection(zeroCurrencyValue) ++ chargeSectionWithValue(nonMemberBasedChargeSection, nonZeroCurrencyValue)))
          result mustBe false
        }
      }
    }

    memberBasedChargeCreationFunctions
      .zipWithIndex.foreach { case (memberBasedChargeSection, nonMemberBasedChargeSectionIndex) =>
      schemeLevelChargeCreationFunctions
        .zipWithIndex.foreach { case (createChargeSection, chargeSectionIndex) =>
        s"return false where there is a charge of type ${nonMemberBasedChargeNames(chargeSectionIndex)} with a " +
          s"value of zero BUT also a value in another member-based charge (${memberBasedChargeNames(nonMemberBasedChargeSectionIndex)}})" in {

          val result = aftService.isChargeZeroedOut(
            createAFTDetailsResponse(createChargeSection(zeroCurrencyValue) ++ memberBasedChargeSection(nonZeroCurrencyValue)))
          result mustBe false
        }
      }
    }
  }

}

object AFTServiceSpec {

  private val zeroCurrencyValue = BigDecimal(0.00)
  private val nonZeroCurrencyValue = BigDecimal(44.33)

  private val memberBasedChargeCreationFunctions = Seq(
    chargeCSectionWithValue _,
    chargeDSectionWithValue _,
    chargeESectionWithValue _,
    chargeGSectionWithValue _
  )

  private val schemeLevelChargeCreationFunctions = Seq(
    chargeASectionWithValue _,
    chargeBSectionWithValue _,
    chargeFSectionWithValue _
  )
  private val nonMemberBasedChargeSections = Seq("chargeTypeADetails", "chargeTypeBDetails", "chargeTypeFDetails")
  private val memberBasedChargeSections = Seq("chargeTypeCDetails", "chargeTypeDDetails", "chargeTypeEDetails", "chargeTypeGDetails")
  private val nonMemberBasedChargeNames = Seq("A", "B", "F")
  private val memberBasedChargeNames = Seq("C", "D", "E", "G")

  private def chargeSectionWithValue(section: String, currencyValue: BigDecimal): JsObject =
    Json.obj(
      section -> Json.obj(
        "totalAmount" -> currencyValue
      )
    )

  private def createAFTDetailsResponse(chargeSection: JsObject): JsObject = Json.obj(
    "chargeDetails" -> chargeSection
  )

  private def chargeASectionWithValue(currencyValue: BigDecimal): JsObject = Json.obj(
    "chargeTypeADetails" -> Json.obj(
      "amendedVersion" -> 1,
      "numberOfMembers" -> 2,
      "totalAmtOfTaxDueAtLowerRate" -> currencyValue,
      "totalAmtOfTaxDueAtHigherRate" -> currencyValue,
      "totalAmount" -> currencyValue
    )
  )

  private def chargeBSectionWithValue(currencyValue: BigDecimal): JsObject = Json.obj(
    "chargeTypeBDetails" -> Json.obj(
      "amendedVersion" -> 1,
      "numberOfMembers" -> 2,
      "totalAmount" -> currencyValue
    )
  )

  private def chargeFSectionWithValue(currencyValue: BigDecimal): JsObject = Json.obj(
    "chargeTypeBDetails" -> Json.obj(
      "amendedVersion" -> 1,
      "totalAmount" -> currencyValue,
      "dateRegiWithdrawn" -> "1980-02-29"
    )
  )

  private def chargeCSectionWithValue(currencyValue: BigDecimal): JsObject = Json.obj(
    "chargeTypeCDetails" -> Json.obj(
      "totalAmount" -> currencyValue,
      "memberDetails" -> Json.arr(
        Json.obj(
          "memberStatus" -> "New",
          "memberAFTVersion" -> 1,
          "memberTypeDetails" -> Json.obj(
            "memberType" -> "Individual",
            "individualDetails" -> Json.obj(
              "title" -> "Mr",
              "firstName" -> "Ray",
              "lastName" -> "Golding",
              "nino" -> "AA000020A"
            )
          ),
          "correspondenceAddressDetails" -> Json.obj(
            "nonUKAddress" -> "False",
            "addressLine1" -> "Plaza 2 ",
            "addressLine2" -> "Ironmasters Way",
            "addressLine3" -> "Telford",
            "addressLine4" -> "Shropshire",
            "countryCode" -> "GB",
            "postalCode" -> "TF3 4NT"
          ),
          "dateOfPayment" -> "2016-06-29",
          "totalAmountOfTaxDue" -> currencyValue
        )
      )
    )
  )

  private def chargeDSectionWithValue(currencyValue: BigDecimal): JsObject = Json.obj(
    "chargeTypeDDetails" -> Json.obj(
      "totalAmount" -> currencyValue,
      "memberDetails" -> Json.arr(
        Json.obj(
          "memberStatus" -> "New",
          "memberAFTVersion" -> 1,
          "individualsDetails" -> Json.obj(
            "title" -> "Mr",
            "firstName" -> "Ray",
            "lastName" -> "Golding",
            "nino" -> "AA000020A"
          ),
          "dateOfBenefitCrystalizationEvent" -> "2016-06-29",
          "totalAmtOfTaxDueAtLowerRate" -> currencyValue,
          "totalAmtOfTaxDueAtHigherRate" -> currencyValue
        )
      )
    )
  )

  private def chargeESectionWithValue(currencyValue: BigDecimal): JsObject = Json.obj(
    "chargeTypeEDetails" -> Json.obj(
      "totalAmount" -> currencyValue,
      "memberDetails" -> Json.arr(
        Json.obj(
          "memberStatus" -> "New",
          "memberAFTVersion" -> 1,
          "individualsDetails" -> Json.obj(
            "title" -> "Mr",
            "firstName" -> "Ray",
            "lastName" -> "Golding",
            "nino" -> "AA000020A"
          ),
          "dateOfNotice" -> "2016-06-29",
          "amountOfCharge" -> currencyValue,
          "taxYearEnding" -> "2018",
          "paidUnder237b" -> "Yes"
        )
      )
    )
  )

  private def chargeGSectionWithValue(currencyValue: BigDecimal): JsObject = Json.obj(
    "chargeTypeGDetails" -> Json.obj(
      "totalOTCAmount" -> currencyValue,
      "memberDetails" -> Json.arr(
        Json.obj(
          "memberStatus" -> "New",
          "memberAFTVersion" -> 1,
          "individualsDetails" -> Json.obj(
            "title" -> "Mr",
            "firstName" -> "Ray",
            "lastName" -> "Golding",
            "dateOfBirth" -> "1980-02-29",
            "nino" -> "AA000020A"
          ),
          "dateOfTransfer" -> "2016-06-29",
          "amountTransferred" -> currencyValue,
          "amountOfTaxDeducted" -> currencyValue,
          "qropsReference" -> "Q300000"
        )
      )
    )
  )

  private val jsonOneMemberZeroValue = Json.parse(
    """{
      |  "aftStatus": "Compiled",
      |  "quarter": {
      |       "startDate": "2019-01-01",
      |       "endDate": "2019-03-31"
      |  },
      |  "chargeCDetails": {
      |         "employers" : [
      |                {
      |                    "sponsoringIndividualDetails" : {
      |                        "firstName" : "asas",
      |                        "lastName" : "asa",
      |                        "nino" : "CS121212C"
      |                    },
      |                    "whichTypeOfSponsoringEmployer" : "individual",
      |                    "sponsoringEmployerAddress" : {
      |                        "line1" : "asas",
      |                        "line2" : "asas",
      |                        "country" : "FR"
      |                    },
      |                    "chargeDetails" : {
      |                        "paymentDate" : "2000-01-01",
      |                        "amountTaxDue" : 0
      |                    }
      |                }
      |            ],
      |            "totalChargeAmount" : 0
      |  }
      |}""".stripMargin)
}

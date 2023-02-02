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

package transformations.generators

import org.scalacheck.Arbitrary.arbitrary
import org.scalacheck.Gen
import org.scalatest.OptionValues
import org.scalatest.matchers.must.Matchers
import play.api.libs.json.{JsArray, JsObject, JsString, Json}

import java.time.{LocalDate, Year}

trait AFTETMPResponseGenerators extends Matchers with OptionValues { // scalastyle:off magic.number
  val ninoGen: Gen[String] = Gen.oneOf(Seq("AB123456C", "CD123456E"))

  val dateGenerator: Gen[LocalDate] = for {
    day <- Gen.choose(1, 28)
    month <- Gen.choose(1, 12)
    year <- Gen.choose(1990, 2000)
  } yield LocalDate.of(year, month, day)

  val individualGen: Gen[JsObject] = for {
    firstName <- arbitrary[String]
    lastName <- arbitrary[String]
    nino <- ninoGen
  } yield {
    Json.obj(
      fields = "firstName" -> firstName,
      "lastName" -> lastName,
      "nino" -> nino
    )
  }

  val addressGenerator: Gen[JsObject] = for {
    nonUkAddress <- arbitrary[Boolean]
    line1 <- nonEmptyString
    line2 <- nonEmptyString
    line3 <- Gen.option(nonEmptyString)
    line4 <- Gen.option(nonEmptyString)
    postalCode <- Gen.option(nonEmptyString)
    country <- Gen.listOfN(2, nonEmptyString).map(_.mkString)
  } yield {
    Json.obj(
      fields = "nonUKAddress" -> nonUkAddress.toString.capitalize,
      "addressLine1" -> line1,
      "addressLine2" -> line2,
      "addressLine3" -> line3,
      "addressLine4" -> line4,
      "postCode" -> postalCode,
      "countryCode" -> country
    )
  }

  val aftDetailsGenerator: Gen[JsObject] =
    for {
      aftVersion <- Gen.choose(1, 999)
      aftStatus <- Gen.oneOf("Compiled", "Submitted")
      quarterStartDate <- dateGenerator
      quarterEndDate <- dateGenerator
      aftReturnType <- Gen.oneOf(Seq("AFT Charge", "AFT Assessment"))
      receiptDate <- arbitrary[String]
    } yield Json.obj(
      fields =
        "aftVersion" -> aftVersion,
      "aftStatus" -> aftStatus,
      "quarterStartDate" -> quarterStartDate,
      "quarterEndDate" -> quarterEndDate,
      "receiptDate" -> receiptDate,
      "aftReturnType" -> aftReturnType
    )

  val schemeDetailsGenerator: Gen[JsObject] =
    for {
      pstr <- arbitrary[String]
      schemeName <- arbitrary[String]
    } yield Json.obj(
      "pstr" -> pstr,
      "schemeName" -> schemeName
    )

  val chargeAETMPGenerator: Gen[JsObject] =
    for {
      amendedVersion <- arbitrary[Int]
      numberOfMembers <- arbitrary[Int]
      totalAmtOfTaxDueAtLowerRate <- arbitrary[BigDecimal]
      totalAmtOfTaxDueAtHigherRate <- arbitrary[BigDecimal]
      totalAmount <- arbitrary[BigDecimal]
    } yield Json.obj(
      fields = "chargeTypeADetails" ->
        Json.obj(
          fields = "amendedVersion" -> amendedVersion,
          "numberOfMembers" -> numberOfMembers,
          "totalAmtOfTaxDueAtLowerRate" -> totalAmtOfTaxDueAtLowerRate,
          "totalAmtOfTaxDueAtHigherRate" -> totalAmtOfTaxDueAtHigherRate,
          "totalAmount" -> totalAmount
        ))

  val chargeBETMPGenerator: Gen[JsObject] =
    for {
      amendedVersion <- arbitrary[Int]
      numberOfMembers <- arbitrary[Int]
      totalAmount <- arbitrary[BigDecimal]
    } yield Json.obj(
      fields = "chargeTypeBDetails" ->
        Json.obj(
          fields = "amendedVersion" -> amendedVersion,
          "numberOfMembers" -> numberOfMembers,
          "totalAmount" -> totalAmount
        ))

  val chargeCIndividualMember: Gen[JsObject] =
    for {
      memberStatus <- arbitrary[String]
      memberAFTVersion <- arbitrary[Int]
      address <- addressGenerator
      individual <- individualGen
      dateOfPayment <- dateGenerator
      totalAmountTaxDue <- arbitrary[BigDecimal]
    } yield {
      Json.obj(
        "memberStatus" -> memberStatus,
        "memberAFTVersion" -> memberAFTVersion,
        "memberTypeDetails" -> Json.obj(
          "memberType" -> "Individual",
          "individualDetails" -> individual
        ),
        "correspondenceAddressDetails" -> address,
        "dateOfPayment" -> dateOfPayment,
        "totalAmountOfTaxDue" -> totalAmountTaxDue
      )
    }

  val chargeCOrgMember: Gen[JsObject] =
    for {
      memberStatus <- arbitrary[String]
      memberAFTVersion <- arbitrary[Int]
      address <- addressGenerator
      comOrOrganisationName <- arbitrary[String]
      crnNumber <- arbitrary[String]
      dateOfPayment <- dateGenerator
      totalAmountTaxDue <- arbitrary[BigDecimal]
    } yield {
      Json.obj(
        "memberStatus" -> memberStatus,
        "memberAFTVersion" -> memberAFTVersion,
        "memberTypeDetails" -> Json.obj(
          "memberType" -> "Organisation",
          "comOrOrganisationName" -> comOrOrganisationName,
          "crnNumber" -> crnNumber
        ),
        "correspondenceAddressDetails" -> address,
        "dateOfPayment" -> dateOfPayment,
        "totalAmountOfTaxDue" -> totalAmountTaxDue
      )
    }

  val chargeCETMPGenerator: Gen[JsObject] =
    for {
      amendedVersion <- arbitrary[Int]
      indvMembers <- Gen.listOfN(2, chargeCIndividualMember)
      orgMembers <- Gen.listOfN(1, chargeCOrgMember)
      totalAmount <- arbitrary[BigDecimal]
    } yield Json.obj(
      fields = "chargeTypeCDetails" ->
        Json.obj(
          fields = "amendedVersion" -> amendedVersion,
          "memberDetails" -> (indvMembers ++ orgMembers),
          "totalAmount" -> totalAmount
        ))

  private def schemes(howManySchemes: Int, optPstrGen: Gen[Option[String]], amountNodeName: String, repoPeriodNodeName: String ): Gen[JsObject] = {
    val seqInt: Seq[Int] = 1 to howManySchemes
    val seqGenScheme = seqInt.map { _ =>
      for {
        optPstr <- optPstrGen
        date <- dateGenerator
        chargeAmountReported <- arbitrary[BigDecimal]
      } yield {
        val moreThanOneScheme = optPstr match {
          case Some(pstr) => Json.obj("pstr" -> pstr)
          case None => Json.obj()
        }
        Json.obj(
          repoPeriodNodeName -> date,
          amountNodeName -> chargeAmountReported
        ) ++ moreThanOneScheme
      }
    }
    val x = Gen.sequence[Seq[JsObject], JsObject](seqGenScheme)
    x.map { t =>
      Json.obj(
        "pensionSchemeDetails" -> JsArray(t)
      )
    }
  }

  private def genSeqOfSchemes(howManySchemes: Int, wasAnotherPensionScheme: Boolean, isPublicServiceRem: Option[Boolean],
                              amountNodeName: String, repoPeriodNodeName: String ): Gen[JsObject] = {
    (isPublicServiceRem, wasAnotherPensionScheme) match {
      case (Some(true), true) => schemes(howManySchemes, Gen.alphaStr.map(Some(_)),amountNodeName, repoPeriodNodeName)
      case (Some(true), false) => schemes(1, Gen.oneOf(Seq(None)), amountNodeName, repoPeriodNodeName)
      case _ => Gen.oneOf(Seq(Json.obj()))
    }
  }

  private def booleanToYesNo(flag:Boolean) = if (flag) JsString("Yes") else JsString("No")

  //isPSRNodeName = "lfAllowanceChgPblSerRem", isOtherSchemesNodeName = "orLfChgPaidbyAnoPS",
  //        amountNodeName = "amtOrRepLtaChg", repoPeriodNodeName = "repPeriodForLtac"

  private def mccloudRemedy(isPSRNodeName: String, isOtherSchemesNodeName: String,
                            amountNodeName : String, repoPeriodNodeName : String): Gen[JsObject] = {
    for {
      isPublicServicePensionsRemedy <- arbitrary[Option[Boolean]]
      optWasAnotherPensionScheme <- arbitrary[Option[Boolean]]
      howManySchemes <- Gen.chooseNum(minT = 1, maxT = if (optWasAnotherPensionScheme.getOrElse(false)) 5 else 1)
      schemes <- genSeqOfSchemes(howManySchemes, optWasAnotherPensionScheme.getOrElse(false), isPublicServicePensionsRemedy,
        amountNodeName: String, repoPeriodNodeName: String )
    } yield {
//      val additionalSchemes = if (isPublicServicePensionsRemedy) {
//        optWasAnotherPensionScheme match {
//          case Some(x) =>  Json.obj(isOtherSchemesNodeName -> booleanToYesNo(x)) ++ schemes
//          case None => Json.obj()
//        }
//      } else {
//        Json.obj()
//      }
//      Json.obj(
//        isPSRNodeName -> booleanToYesNo(isPublicServicePensionsRemedy)
//      ) ++ additionalSchemes
    (isPublicServicePensionsRemedy, optWasAnotherPensionScheme) match {
            case (Some(true), Some(x)) =>
              Json.obj(isOtherSchemesNodeName -> booleanToYesNo(x)) ++ schemes ++
              Json.obj(isPSRNodeName -> booleanToYesNo(true))
            case (Some(true), None) => Json.obj(isPSRNodeName -> booleanToYesNo(true))
            case (Some(false), _) => Json.obj(isPSRNodeName -> booleanToYesNo(false))
            case _ => Json.obj()
          }
        }
  }

  val chargeDMember: Gen[JsObject] =
    for {
      memberStatus <- arbitrary[String]
      memberAFTVersion <- arbitrary[Int]
      individual <- individualGen
      dateOfBenefitCrystalizationEvent <- dateGenerator
      totalAmtOfTaxDueAtLowerRate <- arbitrary[BigDecimal]
      totalAmtOfTaxDueAtHigherRate <- arbitrary[BigDecimal]
      mccloud <- mccloudRemedy(isPSRNodeName = "lfAllowanceChgPblSerRem", isOtherSchemesNodeName = "orLfChgPaidbyAnoPS",
              amountNodeName = "amtOrRepLtaChg", repoPeriodNodeName = "repPeriodForLtac")
    } yield {
      Json.obj(
        "memberStatus" -> memberStatus,
        "memberAFTVersion" -> memberAFTVersion,
        "individualsDetails" -> individual,
        "dateOfBenefitCrystalizationEvent" -> dateOfBenefitCrystalizationEvent,
        "totalAmtOfTaxDueAtLowerRate" -> totalAmtOfTaxDueAtLowerRate,
        "totalAmtOfTaxDueAtHigherRate" -> totalAmtOfTaxDueAtHigherRate
      ) ++ mccloud
    }

  val chargeDETMPGenerator: Gen[JsObject] =
    for {
      amendedVersion <- arbitrary[Int]
      members <- Gen.listOfN(2, chargeDMember)
      totalAmount <- arbitrary[BigDecimal]
    } yield Json.obj(
      fields = "chargeTypeDDetails" ->
        Json.obj(
          fields = "amendedVersion" -> amendedVersion,
          "memberDetails" -> members,
          "totalAmount" -> totalAmount
        ))

  val chargeEMember: Gen[JsObject] =
    for {
      memberStatus <- arbitrary[String]
      memberAFTVersion <- arbitrary[Int]
      firstName <- arbitrary[String]
      lastName <- arbitrary[String]
      nino <- ninoGen
      chargeAmount <- arbitrary[BigDecimal]
      date <- dateGenerator
      isMandatory <- Gen.oneOf("Yes", "No")
      taxYear <- Gen.choose(1990, Year.now.getValue)
      mccloud <- mccloudRemedy(isPSRNodeName = "anAllowanceChgPblSerRem", isOtherSchemesNodeName = "orChgPaidbyAnoPS",
        amountNodeName = "amtOrRepAaChg", repoPeriodNodeName = "repPeriodForAac")
    } yield Json.obj(
      "memberStatus" -> memberStatus,
      "memberAFTVersion" -> memberAFTVersion,
      "individualsDetails" -> Json.obj(
        fields = "firstName" -> firstName,
        "lastName" -> lastName,
        "nino" -> nino
      ),
      "taxYearEnding" -> taxYear,
      "amountOfCharge" -> chargeAmount,
      "dateOfNotice" -> date,
      "paidUnder237b" -> isMandatory
    ) ++ mccloud

  val chargeEETMPGenerator: Gen[JsObject] =
    for {
      amendedVersion <- arbitrary[Int]
      members <- Gen.listOfN(2, chargeEMember)
      totalAmount <- arbitrary[BigDecimal] retryUntil (_ > 0)
    } yield Json.obj(
      fields = "chargeTypeEDetails" ->
        Json.obj(
          fields = "amendedVersion" -> amendedVersion,
          "memberDetails" -> members,
          "totalAmount" -> totalAmount
        ))

  val chargeFETMPGenerator: Gen[JsObject] =
    for {
      amendedVersion <- arbitrary[Int]
      amountTaxDue <- arbitrary[BigDecimal]
      deRegistrationDate <- dateGenerator
    } yield Json.obj(
      fields = "chargeTypeFDetails" ->
        Json.obj(
          fields = "amendedVersion" -> amendedVersion,
          "totalAmount" -> amountTaxDue,
          "dateRegiWithdrawn" -> deRegistrationDate
        ))

  val qropsGenerator: Gen[String] =
    for {
      first <- Gen.listOf((1, arbitrary[Char]))
      last <- arbitrary[Int]
    } yield {
      s"$first$last"
    }

  val chargeGMember: Gen[JsObject] =
    for {
      memberStatus <- arbitrary[String]
      memberAFTVersion <- arbitrary[Int]
      firstName <- arbitrary[String]
      lastName <- arbitrary[String]
      nino <- ninoGen
      dob <- dateGenerator
      qropsReferenceNumber <- qropsGenerator
      qropsTransferDate <- dateGenerator
      amountTransferred <- arbitrary[BigDecimal]
      amountTaxDue <- arbitrary[BigDecimal]
    } yield Json.obj(
      "memberStatus" -> memberStatus,
      "memberAFTVersion" -> memberAFTVersion,
      "individualsDetails" -> Json.obj(
        fields = "firstName" -> firstName,
        "lastName" -> lastName,
        "dateOfBirth" -> dob,
        "nino" -> nino
      ),
      "qropsReference" -> qropsReferenceNumber,
      "dateOfTransfer" -> qropsTransferDate,
      "amountTransferred" -> amountTransferred,
      "amountOfTaxDeducted" -> amountTaxDue

    )

  val chargeGETMPGenerator: Gen[JsObject] =
    for {
      amendedVersion <- arbitrary[Int]
      members <- Gen.listOfN(2, chargeGMember)
      totalChargeAmount <- arbitrary[BigDecimal] retryUntil (_ > 0)
    } yield Json.obj(
      fields = "chargeTypeGDetails" ->
        Json.obj(
          fields = "amendedVersion" -> amendedVersion,
          "memberDetails" -> members,
          "totalOTCAmount" -> totalChargeAmount
        ))

  def nonEmptyString: Gen[String] = Gen.alphaStr.suchThat(_.nonEmpty)

}

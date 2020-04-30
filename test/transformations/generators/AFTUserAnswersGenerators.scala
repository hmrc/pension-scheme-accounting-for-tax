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

package transformations.generators

import java.time.{LocalDate, Year}

import org.scalacheck.Arbitrary.arbitrary
import org.scalacheck.Gen
import org.scalatest.{MustMatchers, OptionValues}
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks
import play.api.libs.json._

trait AFTUserAnswersGenerators extends MustMatchers with ScalaCheckDrivenPropertyChecks with OptionValues {
  val ninoGen: Gen[String] = Gen.oneOf(Seq("AB123456C", "CD123456E"))

  val dateGenerator: Gen[LocalDate] = for {
    day <- Gen.choose(min = 1, max = 28)
    month <- Gen.choose(min = 1, max = 12)
    year <- Gen.choose(min = 1990, max = 2000)
  } yield LocalDate.of(year, month, day)

  val ukAddressGenerator: Gen[JsObject] = for {
    line1 <- nonEmptyString
    line2 <- nonEmptyString
    line3 <- Gen.option(nonEmptyString)
    line4 <- Gen.option(nonEmptyString)
    postalCode <- nonEmptyString
  } yield {
    Json.obj(
      "line1" -> line1,
      "line2" -> line2,
      "line3" -> line3,
      "line4" -> line4,
      "postcode" -> postalCode,
      "country" -> "GB"
    )
  }

  val nonUkAddressGenerator: Gen[JsObject] = for {
    line1 <- nonEmptyString
    line2 <- nonEmptyString
    line3 <- Gen.option(nonEmptyString)
    line4 <- Gen.option(nonEmptyString)
    postalCode <- Gen.option(nonEmptyString)
    country <- Gen.listOfN(2, nonEmptyString).map(_.mkString)
  } yield {
    Json.obj(
      "line1" -> line1,
      "line2" -> line2,
      "line3" -> line3,
      "line4" -> line4,
      "postcode" -> postalCode,
      "country" -> country
    )
  }

  private def memberDetailsGen(isDeleted: Boolean): Gen[JsObject] = for {
    firstName <- nonEmptyString
    lastName <- nonEmptyString
    nino <- ninoGen
  } yield {
    Json.obj(
      fields = "firstName" -> firstName,
      "lastName" -> lastName,
      "nino" -> nino,
      "isDeleted" -> isDeleted
    )
  }

  val aftDetailsUserAnswersGenerator: Gen[JsObject] =
    for {
      aftStatus <- Gen.oneOf(Seq("Compiled", "Submitted"))
      quarterStartDate <- nonEmptyString
      quarterEndDate <- nonEmptyString
    } yield Json.obj(
      fields = "aftStatus" -> aftStatus,
      "quarterStartDate" -> quarterStartDate,
      "quarterEndDate" -> quarterEndDate
    )

  val chargeAUserAnswersGenerator: Gen[JsObject] =
    for {
      numberOfMembers <- arbitrary[Int]
      totalAmtOfTaxDueAtLowerRate <- arbitrary[BigDecimal]
      totalAmtOfTaxDueAtHigherRate <- arbitrary[BigDecimal]
      totalAmount <- arbitrary[BigDecimal]
    } yield Json.obj(
      fields = "chargeADetails" ->
        Json.obj(
          fields = "chargeDetails" -> Json.obj(
            "numberOfMembers" -> numberOfMembers,
          "totalAmtOfTaxDueAtLowerRate" -> totalAmtOfTaxDueAtLowerRate,
          "totalAmtOfTaxDueAtHigherRate" -> totalAmtOfTaxDueAtHigherRate,
          "totalAmount" -> totalAmount
          )
        ))

  val chargeBUserAnswersGenerator: Gen[JsObject] =
    for {
      totalAmount <- arbitrary[BigDecimal]
      numberOfMembers <- arbitrary[Int]
    } yield Json.obj(
      fields = "chargeBDetails" ->
        Json.obj(
          fields = "chargeDetails" -> Json.obj(
            "amountTaxDue" -> totalAmount,
          "numberOfDeceased" -> numberOfMembers
          )
        )
    )

  def chargeCIndividualEmployer(isDeleted: Boolean = false): Gen[JsObject] =
    for {
      firstName <- Gen.alphaNumStr
      lastName <- Gen.alphaNumStr
      memberStatus <- Gen.alphaNumStr
      memberVersion <- arbitrary[Int]
      nino <- ninoGen
      taxDue <- arbitrary[BigDecimal] retryUntil (_ > 0)
      addressDetails <- ukAddressGenerator
      date <- dateGenerator
    } yield Json.obj(
      fields = "whichTypeOfSponsoringEmployer" -> "individual",
      "memberStatus" -> memberStatus,
      "memberAFTVersion" -> memberVersion,
      "chargeDetails" -> Json.obj(
        fields = "paymentDate" -> date,
        "amountTaxDue" -> taxDue
      ),
      "sponsoringIndividualDetails" -> Json.obj(
        fields = "firstName" -> firstName,
        "lastName" -> lastName,
        "nino" -> nino,
        "isDeleted" -> isDeleted
      ),
      "sponsoringEmployerAddress" -> addressDetails
    )

  def chargeCCompanyEmployer(isDeleted: Boolean = false): Gen[JsObject] =
    for {
      name <- Gen.alphaNumStr
      memberVersion <- arbitrary[Int]
      crn <- Gen.alphaNumStr
      taxDue <- arbitrary[BigDecimal] retryUntil (_ > 0)
      addressDetails <- nonUkAddressGenerator
      date <- dateGenerator
    } yield Json.obj(
      fields = "whichTypeOfSponsoringEmployer" -> "organisation",
      "memberStatus" -> "Deleted",
      "memberAFTVersion" -> memberVersion,
      "chargeDetails" -> Json.obj(
        fields = "paymentDate" -> date,
        "amountTaxDue" -> taxDue
      ),
      "sponsoringOrganisationDetails" -> Json.obj(
        fields = "name" -> name,
        "crn" -> crn,
        "isDeleted" -> isDeleted
      ),
      "sponsoringEmployerAddress" -> addressDetails
    )

  val chargeCUserAnswersGenerator: Gen[JsObject] =
    for {
      individualEmployers <- Gen.listOfN(2, chargeCIndividualEmployer())
      individualDeletedEmployers <- Gen.listOfN(1, chargeCIndividualEmployer(isDeleted = true))
      orgEmployers <- Gen.listOfN(2, chargeCCompanyEmployer())
      orgDeletedEmployers <- Gen.listOfN(1, chargeCCompanyEmployer(isDeleted = true))
      totalChargeAmount <- arbitrary[BigDecimal] retryUntil (_ > 0)
    } yield Json.obj(
      fields = "chargeCDetails" ->
        Json.obj(
          fields = "employers" ->
            (individualEmployers ++ individualDeletedEmployers ++ orgEmployers ++ orgDeletedEmployers),
          "totalChargeAmount" -> totalChargeAmount
        ))

  def chargeDMember(isDeleted: Boolean = false, status: String): Gen[JsObject] =
    for {
      memberDetails <- memberDetailsGen(isDeleted)
      memberVersion <- arbitrary[Int]
      date <- dateGenerator
      taxAt25Percent <- arbitrary[BigDecimal]
      taxAt55Percent <- arbitrary[BigDecimal]
    } yield Json.obj(
      "memberDetails" -> memberDetails,
      "memberAFTVersion" -> memberVersion,
      "memberStatus" -> status,
      "chargeDetails" -> Json.obj(
        "dateOfEvent" -> date,
        "taxAt25Percent" -> taxAt25Percent,
        "taxAt55Percent" -> taxAt55Percent
      )
    )

  val chargeDUserAnswersGenerator: Gen[JsObject] =
    for {
      status <- Gen.alphaNumStr
      members <- Gen.listOfN(5, chargeDMember(isDeleted = false, status))
      deletedMembers <- Gen.listOfN(1, chargeDMember(isDeleted = true, status))
      notDeletedMembers <- Gen.listOfN(1, chargeDMember(isDeleted = true, status = "Deleted"))
      totalChargeAmount <- arbitrary[BigDecimal] suchThat (_ > -1)
    } yield Json.obj(
      fields = "chargeDDetails" ->
        Json.obj(
          fields = "members" -> (members ++ deletedMembers ++ notDeletedMembers),
          "totalChargeAmount" -> totalChargeAmount
        ))

  def chargeEMember(isDeleted: Boolean = false, status: String): Gen[JsObject] =
    for {
      memberDetails <- memberDetailsGen(isDeleted)
      memberVersion <- arbitrary[Int]
      chargeAmount <- arbitrary[BigDecimal]
      date <- dateGenerator
      isMandatory <- arbitrary[Boolean]
      taxYear <- Gen.choose(1990, Year.now.getValue)
    } yield Json.obj(
      "memberDetails" -> memberDetails,
      "memberAFTVersion" -> memberVersion,
      "memberStatus" -> status,
      "annualAllowanceYear" -> taxYear.toString,
      "chargeDetails" -> Json.obj(
        "chargeAmount" -> chargeAmount,
        "dateNoticeReceived" -> date,
        "isPaymentMandatory" -> isMandatory
      )
    )

  val chargeEUserAnswersGenerator: Gen[JsObject] =
    for {
      status <- Gen.alphaNumStr
      members <- Gen.listOfN(5, chargeEMember(isDeleted = false, status))
      deletedMembers <- Gen.listOfN(2, chargeEMember(isDeleted = true, status))
      notDeletedMembers <- Gen.listOfN(1, chargeEMember(isDeleted = true, status = "Deleted"))
      totalChargeAmount <- arbitrary[BigDecimal] retryUntil (_ > -1)
    } yield Json.obj(
      fields = "chargeEDetails" ->
        Json.obj(
          fields = "members" -> (members ++ deletedMembers ++ notDeletedMembers),
          "totalChargeAmount" -> totalChargeAmount
        ))

  val chargeFUserAnswersGenerator: Gen[JsObject] =
    for {
      totalAmount <- arbitrary[BigDecimal]
    } yield Json.obj(
      fields = "chargeFDetails" ->
        Json.obj(
          fields = "chargeDetails" -> Json.obj(
            "amountTaxDue" -> totalAmount
          )
        )
    )

  def chargeGMember(isDeleted: Boolean = false, status: String): Gen[JsObject] =
    for {
      firstName <- nonEmptyString
      lastName <- nonEmptyString
      nino <- ninoGen
      dob <- dateGenerator
      memberVersion <- arbitrary[Int]
      qropsReferenceNumber <- nonEmptyString
      qropsTransferDate <- dateGenerator
      amountTransferred <- arbitrary[BigDecimal]
      amountTaxDue <- arbitrary[BigDecimal]
    } yield Json.obj(
      "memberDetails" -> Json.obj(
        fields = "firstName" -> firstName,
        "lastName" -> lastName,
        "dob" -> dob,
        "nino" -> nino,
        "isDeleted" -> isDeleted
      ),
      "memberAFTVersion" -> memberVersion,
      "memberStatus" -> status,
      "chargeDetails" -> Json.obj(
        "qropsReferenceNumber" -> qropsReferenceNumber,
        "qropsTransferDate" -> qropsTransferDate
      ),
      "chargeAmounts" -> Json.obj(
        "amountTransferred" -> amountTransferred,
        "amountTaxDue" -> amountTaxDue
      )
    )

  val chargeGUserAnswersGenerator: Gen[JsObject] =
    for {
      status <- Gen.alphaNumStr
      members <- Gen.listOfN(5, chargeGMember(isDeleted = false, status))
      deletedMembers <- Gen.listOfN(1, chargeGMember(isDeleted = true, status))
      nonDeletedMembers <- Gen.listOfN(2, chargeGMember(isDeleted = true, status = "Deleted"))
      totalChargeAmount <- arbitrary[BigDecimal] suchThat (_ > -1)
    } yield Json.obj(
      fields = "chargeGDetails" ->
        Json.obj(
          fields = "members" -> (members ++ deletedMembers ++ nonDeletedMembers),
          "totalChargeAmount" -> totalChargeAmount
        ))

  def nonEmptyString: Gen[String] = Gen.alphaStr.suchThat(!_.isEmpty)

  def updateJson(path: JsPath, name: String, value: Int): Reads[JsObject] = {
    path.json.update(__.read[JsObject].map(o => o ++ Json.obj(name -> value)))
  }
}

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
import play.api.libs.json.{JsObject, Json}

trait AFTUserAnswersGenerators extends MustMatchers with ScalaCheckDrivenPropertyChecks with OptionValues {
  val ninoGen: Gen[String] = Gen.oneOf(Seq("AB123456C", "CD123456E"))

  val dateGenerator: Gen[LocalDate] = for {
    day <- Gen.choose(1, 28)
    month <- Gen.choose(1, 12)
    year <- Gen.choose(1990, 2000)
  } yield LocalDate.of(year, month, day)

  val aftDetailsUserAnswersGenerator: Gen[JsObject] =
    for {
      aftStatus <- Gen.oneOf(Seq("Compiled", "Submitted"))
      quarterStartDate <- arbitrary[String]
      quarterEndDate <- arbitrary[String]
    } yield Json.obj(
      fields = "aftStatus" -> aftStatus,
      "quarterStartDate" -> quarterStartDate,
      "quarterEndDate" -> quarterEndDate
    )

  val chargeFUserAnswersGenerator: Gen[JsObject] =
    for {
      totalAmount <- arbitrary[BigDecimal]
      dateRegiWithdrawn <- arbitrary[Option[String]]
    } yield Json.obj(
      fields = "chargeFDetails" ->
        Json.obj(
          fields = "amountTaxDue" -> totalAmount,
          "deRegistrationDate" -> dateRegiWithdrawn
        ))

  val chargeAUserAnswersGenerator: Gen[JsObject] =
    for {
      numberOfMembers <- arbitrary[Int]
      totalAmtOfTaxDueAtLowerRate <- arbitrary[BigDecimal]
      totalAmtOfTaxDueAtHigherRate <- arbitrary[BigDecimal]
      totalAmount <- arbitrary[BigDecimal]
    } yield Json.obj(
      fields = "chargeADetails" ->
        Json.obj(
          fields = "numberOfMembers" -> numberOfMembers,
          "totalAmtOfTaxDueAtLowerRate" -> totalAmtOfTaxDueAtLowerRate,
          "totalAmtOfTaxDueAtHigherRate" -> totalAmtOfTaxDueAtHigherRate,
          "totalAmount" -> totalAmount
        ))

  val chargeBUserAnswersGenerator: Gen[JsObject] =
    for {
      totalAmount <- arbitrary[BigDecimal]
      numberOfMembers <- arbitrary[Int]
    } yield Json.obj(
      fields = "chargeBDetails" ->
        Json.obj(
          fields = "amountTaxDue" -> totalAmount,
          "numberOfDeceased" -> numberOfMembers
        ))

  def chargeEMember(isDeleted: Boolean = false): Gen[JsObject] =
    for {
      firstName <- arbitrary[String]
      lastName <- arbitrary[String]
      nino <- ninoGen
      chargeAmount <- arbitrary[BigDecimal]
      date <- dateGenerator
      isMandatory <- arbitrary[Boolean]
      taxYear <- Gen.choose(1990, Year.now.getValue)
    } yield Json.obj(
      "memberDetails" -> Json.obj(
          fields =  "firstName" -> firstName,
                    "lastName" -> lastName,
                    "nino" -> nino,
                    "isDeleted" -> isDeleted
              ),
              "annualAllowanceYear" -> taxYear,
              "chargeDetails" -> Json.obj(
              "chargeAmount" -> chargeAmount,
                    "dateNoticeReceived" -> date,
                    "isPaymentMandatory" -> isMandatory
              )

    )

  def chargeDMember(isDeleted: Boolean = false): Gen[JsObject] =
    for {
      firstName <- arbitrary[String]
      lastName <- arbitrary[String]
      nino <- ninoGen
      date <- dateGenerator
      taxAt25Percent <- arbitrary[BigDecimal]
      taxAt55Percent <- arbitrary[BigDecimal]
    } yield Json.obj(
      "memberDetails" -> Json.obj(
        fields =  "firstName" -> firstName,
        "lastName" -> lastName,
        "nino" -> nino,
        "isDeleted" -> isDeleted
      ),
      "chargeDetails" -> Json.obj(
        "dateOfEvent" -> date,
        "taxAt25Percent" -> taxAt25Percent,
        "taxAt55Percent" -> taxAt55Percent
      )
    )

  def chargeGMember(isDeleted: Boolean = false): Gen[JsObject] =
    for {
      firstName <- arbitrary[String]
      lastName <- arbitrary[String]
      nino <- ninoGen
      dob <- dateGenerator
      qropsReferenceNumber <- arbitrary[Int]
      qropsTransferDate <- dateGenerator
      amountTransferred <- arbitrary[BigDecimal]
      amountTaxDue <- arbitrary[BigDecimal]
    } yield Json.obj(
      "memberDetails" -> Json.obj(
        fields =  "firstName" -> firstName,
        "lastName" -> lastName,
        "dob" -> dob,
        "nino" -> nino,
        "isDeleted" -> isDeleted
      ),
      "chargeDetails" -> Json.obj(
        "qropsReferenceNumber" -> qropsReferenceNumber,
        "qropsTransferDate" -> qropsTransferDate
      ),
      "chargeAmounts" -> Json.obj(
        "amountTransferred" -> amountTransferred,
        "amountTaxDue" -> amountTaxDue
      )
    )

  val chargeEUserAnswersGenerator: Gen[JsObject] =
    for {
      members <- Gen.listOfN(5, chargeEMember())
      deletedMembers <- Gen.listOfN(2, chargeEMember(isDeleted = true))
      totalChargeAmount <- arbitrary[BigDecimal] retryUntil(_ > 0)
    } yield Json.obj(
      fields = "chargeEDetails" ->
        Json.obj(
          fields = "members" -> (members ++ deletedMembers),
          "totalChargeAmount" -> totalChargeAmount
        ))

  val chargeEAllDeletedUserAnswersGenerator: Gen[JsObject] =
    for {
      members <- Gen.listOfN(5, chargeEMember())
      deletedMembers <- Gen.listOfN(2, chargeEMember(isDeleted = true))
    } yield Json.obj(
      fields = "chargeEDetails" ->
        Json.obj(
          fields = "members" -> (members ++ deletedMembers),
          "totalChargeAmount" -> BigDecimal(0.00)
        ))

  val chargeDUserAnswersGenerator: Gen[JsObject] =
    for {
      members <- Gen.listOfN(5, chargeDMember())
      deletedMembers <- Gen.listOfN(2, chargeDMember(isDeleted = true))
      totalChargeAmount <- arbitrary[BigDecimal] retryUntil(_ > 0)
    } yield Json.obj(
      fields = "chargeDDetails" ->
        Json.obj(
          fields = "members" -> (members ++ deletedMembers),
          "totalChargeAmount" -> totalChargeAmount
        ))

  val chargeDAllDeletedUserAnswersGenerator: Gen[JsObject] =
    for {
      members <- Gen.listOfN(5, chargeDMember())
      deletedMembers <- Gen.listOfN(2, chargeDMember(isDeleted = true))
    } yield Json.obj(
      fields = "chargeDDetails" ->
        Json.obj(
          fields = "members" -> (members ++ deletedMembers),
          "totalChargeAmount" -> BigDecimal(0.00)
        ))

  val chargeGUserAnswersGenerator: Gen[JsObject] =
    for {
      members <- Gen.listOfN(5, chargeGMember())
      deletedMembers <- Gen.listOfN(2, chargeGMember(isDeleted = true))
      totalChargeAmount <- arbitrary[BigDecimal] retryUntil(_ > 0)
    } yield Json.obj(
      fields = "chargeGDetails" ->
        Json.obj(
          fields = "members" -> (members ++ deletedMembers),
          "totalChargeAmount" -> totalChargeAmount
        ))

  val chargeGAllDeletedUserAnswersGenerator: Gen[JsObject] =
    for {
      members <- Gen.listOfN(5, chargeGMember())
      deletedMembers <- Gen.listOfN(2, chargeGMember(isDeleted = true))
    } yield Json.obj(
      fields = "chargeGDetails" ->
        Json.obj(
          fields = "members" -> (members ++ deletedMembers),
          "totalChargeAmount" -> BigDecimal(0.00)
        ))

  def nonEmptyString: Gen[String] = Gen.alphaStr.suchThat(!_.isEmpty)
}

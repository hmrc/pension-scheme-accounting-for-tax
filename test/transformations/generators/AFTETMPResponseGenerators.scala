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
import play.api.libs.json.{JsArray, JsObject, Json}

trait AFTETMPResponseGenerators extends MustMatchers with ScalaCheckDrivenPropertyChecks with OptionValues {
  val ninoGen: Gen[String] = Gen.oneOf(Seq("AB123456C", "CD123456E"))

  val dateGenerator: Gen[LocalDate] = for {
    day <- Gen.choose(1, 28)
    month <- Gen.choose(1, 12)
    year <- Gen.choose(1990, 2000)
  } yield LocalDate.of(year, month, day)

  val aftDetailsGenerator: Gen[(JsObject, JsObject)] =
    for {
      aftVersion <- Gen.choose(1, 999)
      aftStatus <- Gen.oneOf("Compiled", "Submitted")
      quarterStartDate <- dateGenerator
      quarterEndDate <- dateGenerator
      aftReturnType <- Gen.oneOf(Seq("AFT Charge", "AFT Assessment"))
      receiptDate <- arbitrary[String]
    } yield (Json.obj(
      fields =
        "aftVersion" -> aftVersion,
        "aftStatus" -> aftStatus,
      "quarterStartDate" -> quarterStartDate,
      "quarterEndDate" -> quarterEndDate,
      "receiptDate" -> receiptDate,
      "aftReturnType" -> aftReturnType
    ),
      Json.obj(
        fields = "aftStatus" -> aftStatus,
        "quarter" -> Json.obj(
          "startDate" -> quarterStartDate,
          "endDate" -> quarterEndDate
        )
      ))

  val schemeDetailsGenerator: Gen[(JsObject, JsObject)] =
    for {
      pstr <- arbitrary[String]
      schemeName <- arbitrary[String]
    } yield (Json.obj(
      "pstr" -> pstr,
      "schemeName" -> schemeName
    ),
      Json.obj(
        "pstr" -> pstr,
        "schemeName" -> schemeName
      )
    )

  val chargeAUserAnswersGenerator: Gen[(JsObject, JsObject)] =
    for {
      numberOfMembers <- arbitrary[Int]
      totalAmtOfTaxDueAtLowerRate <- arbitrary[BigDecimal]
      totalAmtOfTaxDueAtHigherRate <- arbitrary[BigDecimal]
      totalAmount <- arbitrary[BigDecimal]
    } yield (Json.obj(
      fields = "chargeTypeADetails" ->
        Json.obj(
          fields = "numberOfMembers" -> numberOfMembers,
          "totalAmtOfTaxDueAtLowerRate" -> totalAmtOfTaxDueAtLowerRate,
          "totalAmtOfTaxDueAtHigherRate" -> totalAmtOfTaxDueAtHigherRate,
          "totalAmount" -> totalAmount
        )),
      Json.obj(
        fields = "chargeADetails" ->
          Json.obj(
            fields = "numberOfMembers" -> numberOfMembers,
            "totalAmtOfTaxDueAtLowerRate" -> totalAmtOfTaxDueAtLowerRate,
            "totalAmtOfTaxDueAtHigherRate" -> totalAmtOfTaxDueAtHigherRate,
            "totalAmount" -> totalAmount
          )))

  val chargeEMember: Gen[(JsObject, JsObject)] =
    for {
      firstName <- arbitrary[String]
      lastName <- arbitrary[String]
      nino <- ninoGen
      chargeAmount <- arbitrary[BigDecimal]
      date <- dateGenerator
      isMandatory <- Gen.oneOf("Yes", "No")
      taxYear <- Gen.choose(1990, Year.now.getValue)
    } yield (Json.obj(
      "individualsDetails" -> Json.obj(
        fields =  "firstName" -> firstName,
        "lastName" -> lastName,
        "nino" -> nino
      ),
      "taxYearEnding" -> taxYear,
      "amountOfCharge" -> chargeAmount,
        "dateOfNotice" -> date,
        "paidUnder237b" -> isMandatory

    ),
      Json.obj(
        "memberDetails" -> Json.obj(
          fields =  "firstName" -> firstName,
          "lastName" -> lastName,
          "nino" -> nino
        ),
        "annualAllowanceYear" -> taxYear,
        "chargeDetails" -> Json.obj(
          "chargeAmount" -> chargeAmount,
          "dateNoticeReceived" -> date,
          "isPaymentMandatory" -> isMandatory.equals("Yes")
        )

      ))

  val chargeEUserAnswersGenerator: Gen[(JsObject, JsObject)] =
    for {
      member1 <- chargeEMember
      member2 <- chargeEMember
      totalAmount <- arbitrary[BigDecimal] retryUntil(_ > 0)
    } yield (Json.obj(
      fields = "chargeTypeEDetails" ->
        Json.obj(
          fields = "memberDetails" -> List(member1._1, member2._1),
          "totalAmount" -> totalAmount
        )),
      Json.obj(
        fields = "chargeEDetails" ->
          Json.obj(
            fields = "members" -> List(member1._2, member2._2),
            "totalChargeAmount" -> totalAmount
          )))

  val chargeFUserAnswersGenerator: Gen[(JsObject, JsObject)] =
    for {
      amountTaxDue <- arbitrary[BigDecimal]
      deRegistrationDate <- dateGenerator
    } yield (Json.obj(
      fields = "chargeTypeFDetails" ->
        Json.obj(
          fields = "totalAmount" -> amountTaxDue,
          "dateRegiWithdrawn" -> deRegistrationDate
        )),
      Json.obj(
        fields = "chargeFDetails" ->
          Json.obj(
            fields = "amountTaxDue" -> amountTaxDue,
            "deRegistrationDate" -> deRegistrationDate
          )))

  val chargeGMember: Gen[(JsObject, JsObject)] =
    for {
      firstName <- arbitrary[String]
      lastName <- arbitrary[String]
      nino <- ninoGen
      dob <- dateGenerator
      qropsReferenceNumber <- arbitrary[Int]
      qropsTransferDate <- dateGenerator
      amountTransferred <- arbitrary[BigDecimal]
      amountTaxDue <- arbitrary[BigDecimal]
    } yield (Json.obj(
      "individualsDetails" -> Json.obj(
        fields =  "firstName" -> firstName,
        "lastName" -> lastName,
        "dateOfBirth" -> dob,
        "nino" -> nino
      ),
      "qropsReference" -> qropsReferenceNumber,
      "dateOfTransfer" -> qropsTransferDate,
      "amountTransferred" -> amountTransferred,
      "amountOfTaxDeducted" -> amountTaxDue

    ),
      Json.obj(
        "memberDetails" -> Json.obj(
          fields =  "firstName" -> firstName,
          "lastName" -> lastName,
          "dob" -> dob,
          "nino" -> nino
        ),
        "chargeDetails" -> Json.obj(
          "qropsReferenceNumber" -> qropsReferenceNumber,
          "qropsTransferDate" -> qropsTransferDate
        ),
        "chargeAmounts" -> Json.obj(
          "amountTransferred" -> amountTransferred,
          "amountTaxDue" -> amountTaxDue
        )
      ))

  val chargeGUserAnswersGenerator: Gen[(JsObject, JsObject)] =
    for {
      member1 <- chargeGMember
      member2 <- chargeGMember
      totalChargeAmount <- arbitrary[BigDecimal] retryUntil(_ > 0)
    } yield (Json.obj(
      fields = "chargeTypeGDetails" ->
        Json.obj(
          fields = "memberDetails" -> List(member1._1, member2._1),
          "totalOTCAmount" -> totalChargeAmount
        )),
      Json.obj(
        fields = "chargeGDetails" ->
          Json.obj(
            fields = "members" -> List(member1._2, member2._2),
            "totalChargeAmount" -> totalChargeAmount
          )))

  def nonEmptyString: Gen[String] = Gen.alphaStr.suchThat(!_.isEmpty)

  val etmpResponseGenerator: Gen[(JsObject, JsObject)] =
    for {
      processingDate <- arbitrary[String]
      schemeDetails <- schemeDetailsGenerator
      aftDetails <- aftDetailsGenerator
      chargeADetails <- chargeAUserAnswersGenerator
      chargeEDetails <- chargeEUserAnswersGenerator
      chargeFDetails <- chargeFUserAnswersGenerator
      chargeGDetails <- chargeGUserAnswersGenerator
    } yield (
      Json.obj(
        "processingDate" -> processingDate,
        "aftDetails" -> aftDetails._1,
        "schemeDetails" -> schemeDetails._1,
        "chargeDetails" -> (chargeADetails._1 ++ chargeEDetails._1 ++ chargeFDetails._1 ++ chargeGDetails._1)
      ),
      aftDetails._2 ++ schemeDetails._2 ++
        chargeADetails._2 ++ chargeEDetails._2 ++
        chargeFDetails._2 ++ chargeGDetails._2
      )

}

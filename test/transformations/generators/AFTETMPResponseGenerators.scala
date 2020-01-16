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

trait AFTETMPResponseGenerators extends MustMatchers with ScalaCheckDrivenPropertyChecks with OptionValues {
  val ninoGen: Gen[String] = Gen.oneOf(Seq("AB123456C", "CD123456E"))

  val dateGenerator: Gen[LocalDate] = for {
    day <- Gen.choose(1, 28)
    month <- Gen.choose(1, 12)
    year <- Gen.choose(1990, 2000)
  } yield LocalDate.of(year, month, day)

  val chargeAUserAnswersGenerator: Gen[JsObject] =
    for {
      numberOfMembers <- arbitrary[Int]
      totalAmtOfTaxDueAtLowerRate <- arbitrary[BigDecimal]
      totalAmtOfTaxDueAtHigherRate <- arbitrary[BigDecimal]
      totalAmount <- arbitrary[BigDecimal]
    } yield Json.obj(
      fields = "chargeTypeADetails" ->
        Json.obj(
          fields = "numberOfMembers" -> numberOfMembers,
          "totalAmtOfTaxDueAtLowerRate" -> totalAmtOfTaxDueAtLowerRate,
          "totalAmtOfTaxDueAtHigherRate" -> totalAmtOfTaxDueAtHigherRate,
          "totalAmount" -> totalAmount
        ))

  val chargeBETMPGenerator: Gen[JsObject] =
    for {
      numberOfMembers <- arbitrary[Int]
      totalAmount <- arbitrary[BigDecimal]
    } yield Json.obj(
      fields = "chargeTypeBDetails" ->
        Json.obj(
          fields = "numberOfMembers" -> numberOfMembers,
          "totalAmount" -> totalAmount
        ))

  val chargeEMember: Gen[JsObject] =
    for {
      firstName <- arbitrary[String]
      lastName <- arbitrary[String]
      nino <- ninoGen
      chargeAmount <- arbitrary[BigDecimal]
      date <- dateGenerator
      isMandatory <- Gen.oneOf("Yes", "No")
      taxYear <- Gen.choose(1990, Year.now.getValue)
    } yield Json.obj(
      "individualsDetails" -> Json.obj(
        fields =  "firstName" -> firstName,
        "lastName" -> lastName,
        "nino" -> nino
      ),
      "taxYearEnding" -> taxYear,
      "amountOfCharge" -> chargeAmount,
        "dateOfNotice" -> date,
        "paidUnder237b" -> isMandatory

    )

  val chargeEUserAnswersGenerator: Gen[JsObject] =
    for {
      members <- Gen.listOfN(2, chargeEMember)
      totalAmount <- arbitrary[BigDecimal] retryUntil(_ > 0)
    } yield Json.obj(
      fields = "chargeTypeEDetails" ->
        Json.obj(
          fields = "memberDetails" -> members,
          "totalAmount" -> totalAmount
        ))

  val chargeFUserAnswersGenerator: Gen[JsObject] =
    for {
      amountTaxDue <- arbitrary[BigDecimal]
      deRegistrationDate <- dateGenerator
    } yield Json.obj(
      fields = "chargeTypeFDetails" ->
        Json.obj(
          fields = "totalAmount" -> amountTaxDue,
          "dateRegiWithdrawn" -> deRegistrationDate
        ))

  def nonEmptyString: Gen[String] = Gen.alphaStr.suchThat(!_.isEmpty)
}

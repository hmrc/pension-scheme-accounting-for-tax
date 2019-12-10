/*
 * Copyright 2019 HM Revenue & Customs
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
import  org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks
import org.scalatest.{MustMatchers, OptionValues}
import play.api.libs.json.{JsObject, Json}

trait AFTGenerators extends MustMatchers with ScalaCheckDrivenPropertyChecks with OptionValues {
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
}

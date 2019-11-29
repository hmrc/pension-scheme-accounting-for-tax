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
import org.scalatest.prop.GeneratorDrivenPropertyChecks
import org.scalatest.{MustMatchers, OptionValues}
import play.api.libs.json.{JsObject, Json}

trait AFTGenerators extends MustMatchers with GeneratorDrivenPropertyChecks with OptionValues {
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
      totalAmount <- arbitrary[String]
      dateRegiWithdrawn <- arbitrary[Option[String]]
    } yield Json.obj(
      fields = "chargeFDetails" ->
        Json.obj(
          fields = "totalAmount" -> totalAmount,
          "dateRegiWithdrawn" -> dateRegiWithdrawn
        ))

  val aftReturnUserAnswersGenerator: Gen[JsObject] = {
    for {
      aftDetails <- aftDetailsUserAnswersGenerator
      chargeFDetails <- chargeFUserAnswersGenerator
    } yield {
      aftDetails ++ chargeFDetails
    }
  }
}

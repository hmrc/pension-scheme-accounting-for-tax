/*
 * Copyright 2025 HM Revenue & Customs
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

package models

import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.Json

class MinimalDetailsSpec
  extends AnyWordSpec
    with Matchers {

  "MinimalDetails" must {
    "serialise and de-serialise org correctly" in {
      val minimalDetails: MinimalDetails =
        MinimalDetails(Some("test ltd"), None)

      val json: String =
        """{"organisationName":"test ltd"}""".stripMargin

      val parsed: MinimalDetails =
        Json.parse(json).as[MinimalDetails]

      parsed.organisationName.mustBe(Some("test ltd"))
      parsed.individualDetails.mustBe(None)
      parsed.name.mustBe("test ltd")

      Json.stringify(Json.toJson(minimalDetails)).mustBe(json)
    }

    "serialise and de-serialise ind correctly" in {
      val minimalDetails: MinimalDetails =
        MinimalDetails(None, Some(IndividualDetails("firstName", None, "lastName")))

      val json: String =
        """{"individualDetails":{"firstName":"firstName","lastName":"lastName"}}""".stripMargin

      val parsed: MinimalDetails =
        Json.parse(json).as[MinimalDetails]

      parsed.individualDetails.mustBe(Some(IndividualDetails("firstName", None, "lastName")))
      parsed.organisationName.mustBe(None)
      parsed.name.mustBe("firstName lastName")

      Json.stringify(Json.toJson(minimalDetails)).mustBe(json)
    }
  }
}

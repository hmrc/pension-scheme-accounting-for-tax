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

class IndividualDetailsSpec
  extends AnyWordSpec
    with Matchers {
  
  "IndividualDetails" must {
    "serialise and de-serialise correctly" in {
      val individualDetails: IndividualDetails =
        IndividualDetails("firstName", None, "lastName")

      val json: String =
        """{"firstName":"firstName","lastName":"lastName"}""".stripMargin
          
      val parsed: IndividualDetails =
        Json.parse(json).as[IndividualDetails]
        
      parsed.firstName.mustBe("firstName")
      parsed.middleName.mustBe(None)
      parsed.lastName.mustBe("lastName")
      parsed.fullName.mustBe("firstName lastName")

      Json.stringify(Json.toJson(individualDetails)).mustBe(json)
    }

    "serialise and de-serialise middleName correctly" in {
      val individualDetails: IndividualDetails =
        IndividualDetails("firstName", Some("middleName"), "lastName")

      val json: String =
        """{"firstName":"firstName","lastName":"lastName","middleName":"middleName"}""".stripMargin

      val parsed: IndividualDetails =
        Json.parse(json).as[IndividualDetails]

      parsed.firstName.mustBe("firstName")
      parsed.middleName.mustBe(Some("middleName"))
      parsed.lastName.mustBe("lastName")
      parsed.fullName.mustBe("firstName middleName lastName")

      Json.stringify(Json.toJson(individualDetails)).mustBe(json)
    }
  }
}

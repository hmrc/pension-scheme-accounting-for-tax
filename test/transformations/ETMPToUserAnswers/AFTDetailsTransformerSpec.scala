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

package transformations.ETMPToUserAnswers

import org.scalatest.FreeSpec
import play.api.libs.json.Json
import transformations.generators.AFTUserAnswersGenerators

class AFTDetailsTransformerSpec extends FreeSpec with AFTUserAnswersGenerators {

  private val chargeATransformer = new ChargeATransformer
  private val chargeBTransformer = new ChargeBTransformer
  private val chargeETransformer = new ChargeETransformer
  private val chargeFTransformer = new ChargeFTransformer

  private val userAnswersJson = Json.parse(
    """{
      |  "aftStatus": "Compiled",
      |  "pstr": "1234",
      |  "schemeName": "Test Scheme",
      |  "quarter": {
      |    "startDate": "2019-01-01",
      |    "endDate": "2019-03-31"
      |  },
      |  "chargeADetails": {
      |    "numberOfMembers": 2,
      |    "totalAmtOfTaxDueAtLowerRate": 200.02,
      |    "totalAmtOfTaxDueAtHigherRate": 200.02,
      |    "totalAmount": 200.02
      |  },
      |  "chargeBDetails": {
      |    "numberOfDeceased": 2,
      |    "amountTaxDue": 100.02
      |  },
      |  "chargeFDetails": {
      |    "amountTaxDue": 200.02,
      |    "deRegistrationDate": "1980-02-29"
      |  }
      |}""".stripMargin)

  private val etmpResponseJson = Json.parse(
    """{
      |  "aftDetails": {
      |    "aftStatus": "Compiled",
      |    "quarterStartDate": "2019-01-01",
      |    "quarterEndDate": "2019-03-31"
      |  },
      |  "schemeDetails": {
      |    "schemeName": "Test Scheme",
      |    "pstr": "1234"
      |  },
      |  "chargeDetails": {
      |    "chargeTypeADetails": {
      |      "numberOfMembers": 2,
      |      "totalAmtOfTaxDueAtLowerRate": 200.02,
      |      "totalAmtOfTaxDueAtHigherRate": 200.02,
      |      "totalAmount": 200.02
      |    },
      |    "chargeTypeBDetails": {
      |      "amendedVersion": 1,
      |      "numberOfMembers": 2,
      |      "totalAmount": 100.02
      |    },
      |    "chargeTypeFDetails": {
      |      "totalAmount": 200.02,
      |      "dateRegiWithdrawn": "1980-02-29"
      |    }
      |  }
      |}
      |
      |""".stripMargin)

  "An AFT Details Transformer" - {
    "must transform from ETMP Get Details API Format to UserAnswers format" in {
      val transformer = new AFTDetailsTransformer(chargeATransformer, chargeBTransformer, chargeETransformer, chargeFTransformer)
      val transformedUserAnswersJson = etmpResponseJson.transform(transformer.transformToUserAnswers).asOpt.value
      transformedUserAnswersJson mustBe userAnswersJson
    }
  }
}

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

package transformations.userAnswersToETMP

import org.scalatest.FreeSpec
import play.api.libs.json.Json
import transformations.generators.AFTGenerators

class AFTReturnTransformerSpec extends FreeSpec with AFTGenerators {

  private val chargeFTransformer = new ChargeFTransformer
  private val chargeATransformer = new ChargeATransformer

  private val userAnswersRequestJson = Json.parse(
    """{
      |  "aftStatus": "Compiled",
      |  "quarterStartDate": "2019-01-01",
      |  "quarterEndDate": "2019-03-31",
      |  "chargeADetails": {
      |      "numberOfMembers": 2,
      |      "totalAmtOfTaxDueAtLowerRate": 200.02,
      |      "totalAmtOfTaxDueAtHigherRate": 200.02,
      |      "totalAmount": 200.02
      |    },
      |  "chargeFDetails": {
      |    "totalAmount": 200.02,
      |    "dateRegiWithdrawn": "1980-02-29"
      |  }
      |}""".stripMargin)

  private val etmpResponseJson = Json.parse(
    """{
      |  "aftDetails": {
      |    "aftStatus": "Compiled",
      |    "quarterStartDate": "2019-01-01",
      |    "quarterEndDate": "2019-03-31"
      |  },
      |  "chargeDetails": {
      |     "chargeTypeADetails": {
      |         "numberOfMembers": 2,
      |         "totalAmtOfTaxDueAtLowerRate": 200.02,
      |         "totalAmtOfTaxDueAtHigherRate": 200.02,
      |         "totalAmount": 200.02
      |       },
      |       "chargeTypeFDetails": {
      |         "totalAmount": 200.02,
      |         "dateRegiWithdrawn": "1980-02-29"
      |       }
      |     }
      |}
      |
      |""".stripMargin)

  "An AFTReturn Transformer" - {
    "must transform from UserAnswers to ETMP AFT Return format" in {
      val transformer = new AFTReturnTransformer(chargeATransformer, chargeFTransformer)
      val transformedEtmpJson = userAnswersRequestJson.transform(transformer.transformToETMPFormat).asOpt.value
      transformedEtmpJson mustBe etmpResponseJson
    }
  }
}

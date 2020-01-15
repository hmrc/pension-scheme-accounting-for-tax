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

package transformations.userAnswersToETMP

import org.scalatest.FreeSpec
import play.api.libs.json.Json
import transformations.generators.AFTUserAnswersGenerators

class AFTUserAnswersReturnTransformerSpec extends FreeSpec with AFTUserAnswersGenerators {

  private val chargeFTransformer = new ChargeFTransformer
  private val chargeATransformer = new ChargeATransformer
  private val chargeBTransformer = new ChargeBTransformer
  private val chargeETransformer = new ChargeETransformer
  private val chargeDTransformer = new ChargeDTransformer
  private val chargeGTransformer = new ChargeGTransformer

  private val userAnswersRequestJson = Json.parse(
    """{
      |  "aftStatus": "Compiled",
      |  "quarter": {
      |       "startDate": "2019-01-01",
      |       "endDate": "2019-03-31"
      |  },
      |  "chargeADetails": {
      |      "numberOfMembers": 2,
      |      "totalAmtOfTaxDueAtLowerRate": 200.02,
      |      "totalAmtOfTaxDueAtHigherRate": 200.02,
      |      "totalAmount": 200.02
      |    },
      |    "chargeBDetails": {
      |      "numberOfDeceased": 4,
      |      "amountTaxDue": 55.55
      |    },
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
      |  "chargeDetails": {
      |     "chargeTypeADetails": {
      |         "numberOfMembers": 2,
      |         "totalAmtOfTaxDueAtLowerRate": 200.02,
      |         "totalAmtOfTaxDueAtHigherRate": 200.02,
      |         "totalAmount": 200.02
      |       },
      |       "chargeTypeBDetails": {
          |      "numberOfMembers": 4,
          |      "totalAmount": 55.55
          |    },
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
      val transformer = new AFTReturnTransformer(chargeATransformer, chargeBTransformer, chargeETransformer,
        chargeDTransformer, chargeFTransformer, chargeGTransformer)
      val transformedEtmpJson = userAnswersRequestJson.transform(transformer.transformToETMPFormat).asOpt.value
      transformedEtmpJson mustBe etmpResponseJson
    }
  }
}

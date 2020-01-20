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
import transformations.generators.AFTUserAnswersGenerators

class ChargeATransformerSpec extends FreeSpec with AFTUserAnswersGenerators {

  "A Charge A Transformer" - {
    "must transform ChargeADetails from UserAnswers to ETMP ChargeADetails" in {
      forAll(chargeAUserAnswersGenerator) {
        userAnswersJson =>
          val transformer = new ChargeATransformer
          val transformedJson = userAnswersJson.transform(transformer.transformToETMPData).asOpt.value

          val etmpChargeDetailsPath = transformedJson \ "chargeDetails" \ "chargeTypeADetails"
          val uaChargeDetailsPath = userAnswersJson \ "chargeADetails"

          etmpChargeDetailsPath \ "numberOfMembers" mustBe uaChargeDetailsPath \ "numberOfMembers"
          etmpChargeDetailsPath \ "totalAmtOfTaxDueAtLowerRate" mustBe uaChargeDetailsPath \ "totalAmtOfTaxDueAtLowerRate"
          etmpChargeDetailsPath \ "totalAmtOfTaxDueAtHigherRate" mustBe uaChargeDetailsPath \ "totalAmtOfTaxDueAtHigherRate"
          etmpChargeDetailsPath \ "totalAmount" mustBe uaChargeDetailsPath \ "totalAmount"
      }
    }
  }

}

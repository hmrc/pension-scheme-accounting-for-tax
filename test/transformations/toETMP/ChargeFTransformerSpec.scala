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

package transformations.toETMP

import org.scalatest.FreeSpec
import transformations.ChargeFTransformer

class ChargeFTransformerSpec extends FreeSpec with AFTGenerators {

  "A Chanrge F Transformer" - {
    "must transform ChargeFDetails from UserAnswers to ETMP ChargeFDetails" in {
      forAll(chargeFGenerator) {
        json =>
          val transformer = new ChargeFTransformer
          val transformedJson = json.transform(transformer.transformToETMPData).asOpt.value
          transformedJson \ "chargeTypeFDetails" \ "totalAmount" mustBe json \ "chargeTypeFDetails" \ "totalAmount"
          transformedJson \ "chargeTypeFDetails" \ "dateRegiWithdrawn" mustBe json \ "chargeTypeFDetails" \ "dateRegiWithdrawn"
      }
    }
  }

}

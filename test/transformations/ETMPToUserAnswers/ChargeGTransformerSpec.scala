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
import play.api.libs.json.JsObject
import transformations.generators.AFTETMPResponseGenerators

class ChargeGTransformerSpec extends FreeSpec with AFTETMPResponseGenerators {

  "A Charge G Transformer" - {
    "must transform ChargeGDetails from ETMP ChargeGDetails to UserAnswers" in {
      forAll(chargeGETMPGenerator) {
        generatedValues =>
          val (etmpResponseJson, userAnswersJson) = generatedValues
          val transformer = new ChargeGTransformer
          val transformedJson = etmpResponseJson.transform(transformer.transformToUserAnswers).asOpt.value

          transformedJson mustBe userAnswersJson
      }
    }
  }

}

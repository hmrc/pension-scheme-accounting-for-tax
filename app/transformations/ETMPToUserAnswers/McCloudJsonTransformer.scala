/*
 * Copyright 2023 HM Revenue & Customs
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

import play.api.libs.functional.syntax._
import play.api.libs.json.Reads._
import play.api.libs.json._

trait McCloudJsonTransformer extends JsonTransformer {

//  def readsMcCloudDetails: Reads[JsObject] = {
//    ((__ \ Symbol("mccloudRemedy") \ Symbol("isPublicServicePensionsRemedy")).json.copyFrom((__ \ Symbol("lfAllowanceChgPblSerRem")).json.pick) and
//      (__ \ Symbol("mccloudRemedy") \ Symbol("wasAnotherPensionScheme")).json.copyFrom((__ \ Symbol("orLfChgPaidbyAnoPS")).json.pick)).reduce
//  }

  def readsMcCloudDetails: Reads[JsObject] = {
    (__ \ Symbol("lfAllowanceChgPblSerRem")).read[Boolean].flatMap { isMcCloud =>
      if (isMcCloud) {
        ((__ \ Symbol("mccloudRemedy") \ Symbol("isPublicServicePensionsRemedy")).json.put(JsTrue) and
        (__ \ Symbol("mccloudRemedy") \ Symbol("wasAnotherPensionScheme")).json.copyFrom((__ \ Symbol("orLfChgPaidbyAnoPS")).json.pick)).reduce
      } else {
        (__ \ Symbol("mccloudRemedy") \ Symbol("isPublicServicePensionsRemedy")).json.put(JsFalse)
      }
    }
  }

//    ((__ \ Symbol("memberDetails") \ Symbol("firstName")).json.copyFrom((__ \ Symbol("individualsDetails") \ Symbol("firstName")).json.pick) and
//      (__ \ Symbol("memberDetails") \ Symbol("lastName")).json.copyFrom((__ \ Symbol("individualsDetails") \ Symbol("lastName")).json.pick) and
//      (__ \ Symbol("memberDetails") \ Symbol("nino")).json.copyFrom((__ \ Symbol("individualsDetails") \ Symbol("nino")).json.pick)
//      ).reduce

}

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

import play.api.libs.functional.syntax._
import play.api.libs.json.Reads._
import play.api.libs.json.{__, _}

class ChargeFTransformer extends JsonTransformer {

  def transformToETMPData: Reads[JsObject] =
    (__ \ 'chargeFDetails).readNullable {
      __.read(
        (((__ \ 'chargeDetails \ 'chargeTypeFDetails \ 'amendedVersion).json.copyFrom((__ \ 'amendedVersion).json.pick)
          orElse doNothing) and
          (__ \ 'chargeDetails \ 'chargeTypeFDetails \ 'totalAmount).json.copyFrom((__ \ 'chargeDetails \ 'amountTaxDue).json.pick) and
          ((__ \ 'chargeDetails \ 'chargeTypeFDetails \ 'dateRegiWithdrawn).json.copyFrom((__ \ 'chargeDetails \ 'deRegistrationDate).json.pick)
            orElse doNothing)).reduce
      )
    }.map {
      _.getOrElse(Json.obj())
    }
}

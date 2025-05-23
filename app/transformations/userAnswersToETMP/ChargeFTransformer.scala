/*
 * Copyright 2024 HM Revenue & Customs
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
import play.api.libs.json._

class ChargeFTransformer extends JsonTransformer {

  def transformToETMPData: Reads[JsObject] =
    (__ \ Symbol("chargeFDetails")).readNullable {
      __.read(
        (((__ \ Symbol("chargeDetails") \ Symbol("chargeTypeFDetails") \ Symbol("amendedVersion")).json.copyFrom((__ \ Symbol("amendedVersion")).json.pick)
          orElse doNothing) and
          (__ \ Symbol("chargeDetails") \ Symbol("chargeTypeFDetails") \ Symbol("totalAmount"))
            .json.copyFrom((__ \ Symbol("chargeDetails") \ Symbol("totalAmount")).json.pick) and
          ((__ \ Symbol("chargeDetails") \ Symbol("chargeTypeFDetails") \ Symbol("dateRegiWithdrawn"))
            .json.copyFrom((__ \ Symbol("chargeDetails") \ Symbol("deRegistrationDate")).json.pick)
            orElse doNothing)).reduce: Reads[JsObject]
      )
    }.map {
      _.getOrElse(Json.obj())
    }
}

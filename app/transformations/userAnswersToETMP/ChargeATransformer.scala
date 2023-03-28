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

package transformations.userAnswersToETMP

import play.api.libs.functional.syntax._
import play.api.libs.json.Reads._
import play.api.libs.json._

class ChargeATransformer extends JsonTransformer {

  def transformToETMPData: Reads[JsObject] =
    (__ \ Symbol("chargeADetails")).readNullable {
      __.read(
        (((__ \ Symbol("chargeDetails") \ Symbol("chargeTypeADetails") \ Symbol("amendedVersion")).json
          .copyFrom((__ \ Symbol("amendedVersion")).json.pick)
          orElse doNothing) and
          (__ \ Symbol("chargeDetails") \ Symbol("chargeTypeADetails") \ Symbol("numberOfMembers"))
            .json.copyFrom((__ \ Symbol("chargeDetails") \ Symbol("numberOfMembers")).json.pick) and
          (__ \ Symbol("chargeDetails") \ Symbol("chargeTypeADetails") \ Symbol("totalAmtOfTaxDueAtLowerRate")).json.copyFrom(
            (__ \ Symbol("chargeDetails") \ Symbol("totalAmtOfTaxDueAtLowerRate")).json.pick) and
          (__ \ Symbol("chargeDetails") \ Symbol("chargeTypeADetails") \ Symbol("totalAmtOfTaxDueAtHigherRate")).json.copyFrom(
            (__ \ Symbol("chargeDetails") \ Symbol("totalAmtOfTaxDueAtHigherRate")).json.pick) and
          (__ \ Symbol("chargeDetails") \ Symbol("chargeTypeADetails") \ Symbol("totalAmount"))
            .json.copyFrom((__ \ Symbol("chargeDetails") \ Symbol("totalAmount")).json.pick)).reduce
      )
    }.map {
      _.getOrElse(Json.obj())
    }
}

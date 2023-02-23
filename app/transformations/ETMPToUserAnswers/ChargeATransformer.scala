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
import play.api.libs.json.{JsObject, Json, Reads, __}

class ChargeATransformer {

  def transformToUserAnswers: Reads[JsObject] =
    (__ \ Symbol("chargeTypeA")).readNullable {
      __.read(
        ((__ \ Symbol("chargeADetails") \ Symbol("amendedVersion")).json.copyFrom((__ \ Symbol("amendedVersion")).json.pick) and
          (__ \ Symbol("chargeADetails") \ Symbol("chargeDetails") \ Symbol("numberOfMembers")).json.copyFrom((__ \ Symbol("numberOfMembers")).json.pick) and
          (__ \ Symbol("chargeADetails") \ Symbol("chargeDetails") \ Symbol("totalAmtOfTaxDueAtLowerRate"))
            .json.copyFrom((__ \ Symbol("totalAmtOfTaxDueAtLowerRate")).json.pick) and
          (__ \ Symbol("chargeADetails") \ Symbol("chargeDetails") \ Symbol("totalAmtOfTaxDueAtHigherRate"))
            .json.copyFrom((__ \ Symbol("totalAmtOfTaxDueAtHigherRate")).json.pick) and
          (__ \ Symbol("chargeADetails") \ Symbol("chargeDetails") \ Symbol("totalAmount")).json.copyFrom((__ \ Symbol("totalAmount")).json.pick)).reduce
      )
    }.map {
      _.getOrElse(Json.obj())
    }

}

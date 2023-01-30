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

class ChargeDTransformer extends McCloudJsonTransformer {

  def transformToUserAnswers: Reads[JsObject] =
    (__ \ Symbol("chargeTypeDDetails")).readNullable(__.read(
      ((__ \ Symbol("chargeDDetails") \ Symbol("amendedVersion")).json.copyFrom((__ \ Symbol("amendedVersion")).json.pick) and
        (__ \ Symbol("chargeDDetails") \ Symbol("members")).json.copyFrom((__ \ Symbol("memberDetails")).read(readsMembers)) and
        (__ \ Symbol("chargeDDetails") \ Symbol("totalChargeAmount")).json.copyFrom((__ \ Symbol("totalAmount")).json.pick)).reduce
    )).map(_.getOrElse(Json.obj()))

  def readsMembers: Reads[JsArray] = __.read(Reads.seq(readsMember)).map(JsArray(_))

  def readsMember: Reads[JsObject] =
    (readsMemberDetails and
      (__ \ Symbol("memberStatus")).json.copyFrom((__ \ Symbol("memberStatus")).json.pick) and
      (__ \ Symbol("memberAFTVersion")).json.copyFrom((__ \ Symbol("memberAFTVersion")).json.pick) and
      (__ \ Symbol("chargeDetails") \ Symbol("dateOfEvent")).json.copyFrom((__ \ Symbol("dateOfBenefitCrystalizationEvent")).json.pick) and
      (__ \ Symbol("chargeDetails") \ Symbol("taxAt25Percent")).json.copyFrom((__ \ Symbol("totalAmtOfTaxDueAtLowerRate")).json.pick) and
      (__ \ Symbol("chargeDetails") \ Symbol("taxAt55Percent")).json.copyFrom((__ \ Symbol("totalAmtOfTaxDueAtHigherRate")).json.pick) and
      readsMcCloudDetails).reduce
}

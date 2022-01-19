/*
 * Copyright 2022 HM Revenue & Customs
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
import play.api.libs.json.{__, _}

class ChargeDTransformer extends JsonTransformer {

  def transformToUserAnswers: Reads[JsObject] =
    (__ \ 'chargeTypeDDetails).readNullable(__.read(
      ((__ \ 'chargeDDetails \ 'amendedVersion).json.copyFrom((__ \ 'amendedVersion).json.pick) and
        (__ \ 'chargeDDetails \ 'members).json.copyFrom((__ \ 'memberDetails).read(readsMembers)) and
        (__ \ 'chargeDDetails \ 'totalChargeAmount).json.copyFrom((__ \ 'totalAmount).json.pick)).reduce
    )).map(_.getOrElse(Json.obj()))

  def readsMembers: Reads[JsArray] = __.read(Reads.seq(readsMember)).map(JsArray(_))

  def readsMember: Reads[JsObject] =
    (readsMemberDetails and
      (__ \ 'memberStatus).json.copyFrom((__ \ 'memberStatus).json.pick) and
      (__ \ 'memberAFTVersion).json.copyFrom((__ \ 'memberAFTVersion).json.pick) and
      (__ \ 'chargeDetails \ 'dateOfEvent).json.copyFrom((__ \ 'dateOfBenefitCrystalizationEvent).json.pick) and
      (__ \ 'chargeDetails \ 'taxAt25Percent).json.copyFrom((__ \ 'totalAmtOfTaxDueAtLowerRate).json.pick) and
      (__ \ 'chargeDetails \ 'taxAt55Percent).json.copyFrom((__ \ 'totalAmtOfTaxDueAtHigherRate).json.pick)).reduce
}

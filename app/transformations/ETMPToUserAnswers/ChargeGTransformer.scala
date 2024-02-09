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

package transformations.ETMPToUserAnswers

import play.api.libs.functional.syntax._
import play.api.libs.json.Reads._
import play.api.libs.json._

class ChargeGTransformer extends JsonTransformer {

  def transformToUserAnswers: Reads[JsObject] =
    (__ \ Symbol("chargeTypeG")).readNullable(__.read(
      ((__ \ Symbol("chargeGDetails") \ Symbol("amendedVersion")).json.copyFrom((__ \ Symbol("amendedVersion")).json
        .pick(readsVersionRemovingZeroes)) and
        (__ \ Symbol("chargeGDetails") \ Symbol("members")).json.copyFrom((__ \ Symbol("memberDetails")).read(readsMembers)) and
        (__ \ Symbol("chargeGDetails") \ Symbol("totalChargeAmount")).json.copyFrom((__ \ Symbol("totalOTCAmount")).json.pick)).reduce
    )).map(_.getOrElse(Json.obj()))

  def readsMembers: Reads[JsArray] = __.read(Reads.seq(readsMember)).map(JsArray(_))

  def readsMember: Reads[JsObject] =
    (readsMemberDetails and
      (__ \ Symbol("memberDetails") \ Symbol("dob")).json.copyFrom((__ \ Symbol("individualDetails") \ Symbol("dateOfBirth")).json.pick) and
      readsQrops and
      (__ \ Symbol("memberStatus")).json.copyFrom((__ \ Symbol("memberStatus")).json.pick) and
      (__ \ Symbol("memberAFTVersion")).json.copyFrom((__ \ Symbol("memberAFTVersion")).json
        .pick(readsVersionRemovingZeroes)) and
      (__ \ Symbol("chargeDetails") \ Symbol("qropsTransferDate")).json.copyFrom((__ \ Symbol("dateOfTransfer")).json.pick) and
      (__ \ Symbol("chargeAmounts") \ Symbol("amountTransferred")).json.copyFrom((__ \ Symbol("amountTransferred")).json.pick) and
      (__ \ Symbol("chargeAmounts") \ Symbol("amountTaxDue")).json.copyFrom((__ \ Symbol("amountOfTaxDeducted")).json.pick)).reduce

  def readsQrops: Reads[JsObject] = {
    (__ \ Symbol("qropsReference")).read[String].flatMap { qropsReference =>
      (__ \ Symbol("chargeDetails") \ Symbol("qropsReferenceNumber")).json.put(JsString(qropsReference.substring(1)))
    }
  }

}

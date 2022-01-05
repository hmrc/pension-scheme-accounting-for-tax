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

class ChargeGTransformer extends JsonTransformer {

  def transformToUserAnswers: Reads[JsObject] =
    (__ \ 'chargeTypeGDetails).readNullable(__.read(
      ((__ \ 'chargeGDetails \ 'amendedVersion).json.copyFrom((__ \ 'amendedVersion).json.pick) and
        (__ \ 'chargeGDetails \ 'members).json.copyFrom((__ \ 'memberDetails).read(readsMembers)) and
        (__ \ 'chargeGDetails \ 'totalChargeAmount).json.copyFrom((__ \ 'totalOTCAmount).json.pick)).reduce
    )).map(_.getOrElse(Json.obj()))

  def readsMembers: Reads[JsArray] = __.read(Reads.seq(readsMember)).map(JsArray(_))

  def readsMember: Reads[JsObject] =
    (readsMemberDetails and
      (__ \ 'memberDetails \ 'dob).json.copyFrom((__ \ 'individualsDetails \ 'dateOfBirth).json.pick) and
      readsQrops and
      (__ \ 'memberStatus).json.copyFrom((__ \ 'memberStatus).json.pick) and
      (__ \ 'memberAFTVersion).json.copyFrom((__ \ 'memberAFTVersion).json.pick) and
      (__ \ 'chargeDetails \ 'qropsTransferDate).json.copyFrom((__ \ 'dateOfTransfer).json.pick) and
      (__ \ 'chargeAmounts \ 'amountTransferred).json.copyFrom((__ \ 'amountTransferred).json.pick) and
      (__ \ 'chargeAmounts \ 'amountTaxDue).json.copyFrom((__ \ 'amountOfTaxDeducted).json.pick)).reduce

  def readsQrops: Reads[JsObject] = {
    (__ \ 'qropsReference).read[String].flatMap { qropsReference =>
      (__ \ 'chargeDetails \ 'qropsReferenceNumber).json.put(JsString(qropsReference.substring(1)))
    }
  }

}

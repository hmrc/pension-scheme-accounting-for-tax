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

class ChargeGTransformer extends JsonTransformer {

  def transformToETMPData: Reads[JsObject] =
    (__ \ 'chargeGDetails).readNullable(__.read(readsChargeG)).map(_.getOrElse(Json.obj()))

  def readsChargeG: Reads[JsObject] =
    (__ \ 'totalChargeAmount).read[BigDecimal].flatMap { _ =>
      (((__ \ 'chargeDetails \ 'chargeTypeGDetails \ 'amendedVersion).json.copyFrom((__ \ 'amendedVersion).json.pick)
        orElse doNothing) and
        (__ \ 'chargeDetails \ 'chargeTypeGDetails \ 'memberDetails).json.copyFrom((__ \ 'members).read(readsMembers)) and
        (__ \ 'chargeDetails \ 'chargeTypeGDetails \ 'totalAmount).json.copyFrom((__ \ 'totalChargeAmount).json.pick)).reduce
    }

  def readsMembers: Reads[JsArray] = readsFiltered(_ \ "memberDetails", readsMember).map(JsArray(_))

  def readsMember: Reads[JsObject] =
    (readsMemberDetails and
      (__ \ 'individualsDetails \ 'dateOfBirth).json.copyFrom((__ \ 'memberDetails \ 'dob).json.pick) and
      readsQrops and
      (__ \ 'dateOfTransfer).json.copyFrom((__ \ 'chargeDetails \ 'qropsTransferDate).json.pick) and
      (__ \ 'amountTransferred).json.copyFrom((__ \ 'chargeAmounts \ 'amountTransferred).json.pick) and
      (__ \ 'amountOfTaxDeducted).json.copyFrom((__ \ 'chargeAmounts \ 'amountTaxDue).json.pick) and
      (__ \ 'memberStatus).json.put(JsString("New"))).reduce

  def readsQrops: Reads[JsObject] = {
    (__ \ 'chargeDetails \ 'qropsReferenceNumber).read[String].flatMap { qropsReference =>
      (__ \ 'qropsReference).json.put(JsString(s"Q$qropsReference"))
    }
  }

}

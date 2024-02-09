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

class ChargeGTransformer extends JsonTransformer {

  def transformToETMPData: Reads[JsObject] =
    (__ \ Symbol("chargeGDetails")).readNullable(__.read(readsChargeG)).map(_.getOrElse(Json.obj())).orElseEmptyOnMissingFields

  def readsChargeG: Reads[JsObject] =
    (__ \ Symbol("totalChargeAmount")).read[BigDecimal].flatMap { _ =>
      (((__ \ Symbol("chargeDetails") \ Symbol("chargeTypeGDetails") \ Symbol("amendedVersion")).json.copyFrom((__ \ Symbol("amendedVersion")).json.pick)
        orElse doNothing) and
        (__ \ Symbol("chargeDetails") \ Symbol("chargeTypeGDetails") \ Symbol("memberDetails")).json.copyFrom((__ \ Symbol("members")).read(readsMembers)) and
        (__ \ Symbol("chargeDetails") \ Symbol("chargeTypeGDetails") \ Symbol("totalAmount"))
          .json.copyFrom((__ \ Symbol("totalChargeAmount")).json.pick)).reduce
    }

  def readsMembers: Reads[JsArray] = readsFiltered(_ \ "memberDetails", readsMember).map(JsArray(_)).map(removeEmptyObjects)

  def readsMember: Reads[JsObject] =
    (readsMemberDetails and
      (__ \ Symbol("individualsDetails") \ Symbol("dateOfBirth")).json.copyFrom((__ \ Symbol("memberDetails") \ Symbol("dob")).json.pick) and
      readsQrops and
      (__ \ Symbol("dateOfTransfer")).json.copyFrom((__ \ Symbol("chargeDetails") \ Symbol("qropsTransferDate")).json.pick) and
      (__ \ Symbol("amountTransferred")).json.copyFrom((__ \ Symbol("chargeAmounts") \ Symbol("amountTransferred")).json.pick) and
      (__ \ Symbol("amountOfTaxDeducted")).json.copyFrom((__ \ Symbol("chargeAmounts") \ Symbol("amountTaxDue")).json.pick) and
      ((__ \ Symbol("memberStatus")).json.copyFrom((__ \ Symbol("memberStatus")).json.pick)
        orElse (__ \ Symbol("memberStatus")).json.put(JsString("New"))) and
      ((__ \ Symbol("memberAFTVersion")).json.copyFrom((__ \ Symbol("memberAFTVersion")).json.pick)
        orElse doNothing)).reduce.orElseEmptyOnMissingFields

  def readsQrops: Reads[JsObject] = {
    (__ \ Symbol("chargeDetails") \ Symbol("qropsReferenceNumber")).read[String].flatMap { qropsReference =>
      (__ \ Symbol("qropsReference")).json.put(JsString(s"Q$qropsReference"))
    }
  }

}

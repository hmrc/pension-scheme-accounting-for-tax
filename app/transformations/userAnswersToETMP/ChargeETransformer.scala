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

package transformations.userAnswersToETMP

import play.api.libs.functional.syntax._
import play.api.libs.json.Reads._
import play.api.libs.json.{__, _}

class ChargeETransformer extends JsonTransformer {

  def transformToETMPData: Reads[JsObject] =
    (__ \ 'chargeEDetails).readNullable(__.read(readsChargeE)).map(_.getOrElse(Json.obj()))

  def readsChargeE: Reads[JsObject] =
    (__ \ 'totalChargeAmount).read[BigDecimal].flatMap { _ =>
      (((__ \ 'chargeDetails \ 'chargeTypeEDetails \ 'amendedVersion).json.copyFrom((__ \ 'amendedVersion).json.pick)
        orElse doNothing) and
        (__ \ 'chargeDetails \ 'chargeTypeEDetails \ 'memberDetails).json.copyFrom((__ \ 'members).read(readsMembers)) and
        (__ \ 'chargeDetails \ 'chargeTypeEDetails \ 'totalAmount).json.copyFrom((__ \ 'totalChargeAmount).json.pick)).reduce
    }

  def readsMembers: Reads[JsArray] = readsFiltered(_ \ "memberDetails", readsMember).map(JsArray(_))

  def readsMember: Reads[JsObject] =
    (readsMemberDetails and
      (__ \ 'amountOfCharge).json.copyFrom((__ \ 'chargeDetails \ 'chargeAmount).json.pick) and
      (__ \ 'dateOfNotice).json.copyFrom((__ \ 'chargeDetails \ 'dateNoticeReceived).json.pick) and
      getPaidUnder237b and
      (__ \ 'taxYearEnding).json.copyFrom((__ \ 'annualAllowanceYear).json.pick) and
      ((__ \ 'memberStatus).json.copyFrom((__ \ 'memberStatus).json.pick)
        orElse (__ \ 'memberStatus).json.put(JsString("New"))) and
      ((__ \ 'memberAFTVersion).json.copyFrom((__ \ 'memberAFTVersion).json.pick)
        orElse doNothing)).reduce

  def getPaidUnder237b: Reads[JsObject] =
    (__ \ 'chargeDetails \ 'isPaymentMandatory).read[Boolean].flatMap { flag =>
      (__ \ 'paidUnder237b).json.put(if (flag) JsString("Yes") else JsString("No"))
    } orElse doNothing

}

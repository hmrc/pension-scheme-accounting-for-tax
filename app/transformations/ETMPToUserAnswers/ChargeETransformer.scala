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

package transformations.ETMPToUserAnswers

import play.api.libs.functional.syntax._
import play.api.libs.json.Reads._
import play.api.libs.json.{__, _}

class ChargeETransformer extends JsonTransformer {

  def transformToUserAnswers: Reads[JsObject] =
    (__ \ 'chargeTypeEDetails).readNullable(__.read(
      ((__ \ 'chargeEDetails \ 'members).json.copyFrom((__ \ 'memberDetails).read(readsMembers)) and
        (__ \ 'chargeEDetails \ 'totalChargeAmount).json.copyFrom((__ \ 'totalAmount).json.pick)).reduce
    )).map(_.getOrElse(Json.obj()))

  def readsMembers: Reads[JsArray] = __.read(Reads.seq(readsMember)).map(JsArray(_))

  def readsMember: Reads[JsObject] =
    (readsMemberDetails and
      (__ \ 'chargeDetails \ 'chargeAmount).json.copyFrom((__ \ 'amountOfCharge).json.pick) and
      (__ \ 'chargeDetails \ 'dateNoticeReceived).json.copyFrom((__ \ 'dateOfNotice).json.pick) and
      getPaidUnder237b and
      (__ \ 'annualAllowanceYear).json.copyFrom((__ \ 'taxYearEnding).json.pick)).reduce

  def getPaidUnder237b: Reads[JsObject] =
    (__ \ 'paidUnder237b).read[String].flatMap { paidUnder237b =>
      (__ \ 'chargeDetails \ 'isPaymentMandatory).json.put(JsBoolean(paidUnder237b.equalsIgnoreCase("Yes")))
    } orElse doNothing

}

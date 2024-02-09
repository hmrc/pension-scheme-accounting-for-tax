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

class ChargeETransformer extends McCloudJsonTransformer {

  def transformToUserAnswers: Reads[JsObject] =
    (__ \ Symbol("chargeTypeE")).readNullable(__.read(
      ((__ \ Symbol("chargeEDetails") \ Symbol("amendedVersion")).json.copyFrom((__ \ Symbol("amendedVersion")).json
        .pick(readsVersionRemovingZeroes)) and
        (__ \ Symbol("chargeEDetails") \ Symbol("members")).json.copyFrom((__ \ Symbol("memberDetails")).read(readsMembers)) and
        (__ \ Symbol("chargeEDetails") \ Symbol("totalChargeAmount")).json.copyFrom((__ \ Symbol("totalAmount")).json.pick)).reduce
    )).map(_.getOrElse(Json.obj()))

  def readsMembers: Reads[JsArray] = __.read(Reads.seq(readsMember)).map(JsArray(_))

  def readsMember: Reads[JsObject] =
    (readsMemberDetails and
      (__ \ Symbol("memberStatus")).json.copyFrom((__ \ Symbol("memberStatus")).json.pick) and
      (__ \ Symbol("memberAFTVersion")).json.copyFrom((__ \ Symbol("memberAFTVersion")).json
        .pick(readsVersionRemovingZeroes)) and
      (__ \ Symbol("chargeDetails") \ Symbol("chargeAmount")).json.copyFrom((__ \ Symbol("amountOfCharge")).json.pick) and
      (__ \ Symbol("chargeDetails") \ Symbol("dateNoticeReceived")).json.copyFrom((__ \ Symbol("dateOfNotice")).json.pick) and
      getPaidUnder237b and
      // TODO PODS-8147: Once data (taxYearEnding holding startYear) has been corrected the below line should be uncommented to replace the one below it
//      (__ \ Symbol("annualAllowanceYear")).json.copyFrom((__ \ Symbol("taxYearEnding")).json.pick.map(v => JsString((v.as[String].toInt-1).toString))) and
      (__ \ Symbol("annualAllowanceYear")).json.copyFrom((__ \ Symbol("taxYearEnding")).json.pick) and
      readsMcCloudDetails(isPSRNodeName = "anAllowanceChgPblSerRem", isOtherSchemesNodeName = "orChgPaidbyAnoPS",
        amountNodeName = "amtOrRepAaChg", repoPeriodNodeName = "repPeriodForAac")).reduce

  def getPaidUnder237b: Reads[JsObject] =
    (__ \ Symbol("paidUnder237b")).read[String].flatMap { paidUnder237b =>
      (__ \ Symbol("chargeDetails") \ Symbol("isPaymentMandatory")).json.put(JsBoolean(paidUnder237b.equalsIgnoreCase("Yes")))
    } orElse doNothing

}

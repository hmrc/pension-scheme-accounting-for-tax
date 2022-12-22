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
import play.api.libs.json._

class ChargeETransformer extends JsonTransformer {

  def transformToETMPData: Reads[JsObject] =
    (__ \ Symbol("chargeEDetails")).readNullable(__.read(readsChargeE)).map(_.getOrElse(Json.obj())).orElseEmptyOnMissingFields

  def readsChargeE: Reads[JsObject] =
    (__ \ Symbol("totalChargeAmount")).read[BigDecimal].flatMap { _ =>
      (
        ((__ \ Symbol("chargeDetails") \ Symbol("chargeTypeEDetails") \ Symbol("amendedVersion")).json.copyFrom((__ \ Symbol("amendedVersion")).json.pick)
          orElse doNothing) and
          (__ \ Symbol("chargeDetails") \ Symbol("chargeTypeEDetails") \ Symbol("memberDetails")).json.copyFrom((__ \ Symbol("members")).read(readsMembers)) and
          (__ \ Symbol("chargeDetails") \ Symbol("chargeTypeEDetails") \ Symbol("totalAmount")).json.copyFrom((__ \ Symbol("totalChargeAmount")).json.pick)
        ).reduce
    }

  def readsMembers: Reads[JsArray] = readsFiltered(_ \ "memberDetails", readsMember).map(JsArray(_)).map(removeEmptyObjects)

  def readsMember: Reads[JsObject] =
    (readsMemberDetails and
      (__ \ Symbol("amountOfCharge")).json.copyFrom((__ \ Symbol("chargeDetails") \ Symbol("chargeAmount")).json.pick) and
      (__ \ Symbol("dateOfNotice")).json.copyFrom((__ \ Symbol("chargeDetails") \ Symbol("dateNoticeReceived")).json.pick) and
      getPaidUnder237b and
      (__ \ Symbol("taxYearEnding")).json.copyFrom((__ \ Symbol("annualAllowanceYear")).json.pick) and
      ((__ \ Symbol("memberStatus")).json.copyFrom((__ \ Symbol("memberStatus")).json.pick)
        orElse (__ \ Symbol("memberStatus")).json.put(JsString("New"))) and
      ((__ \ Symbol("memberAFTVersion")).json.copyFrom((__ \ Symbol("memberAFTVersion")).json.pick)
        orElse doNothing) and (readsMccloud orElse doNothing)
      ).reduce.orElseEmptyOnMissingFields

  def getPaidUnder237b: Reads[JsObject] =
    (__ \ Symbol("chargeDetails") \ Symbol("isPaymentMandatory")).read[Boolean].flatMap { flag =>
      (__ \ Symbol("paidUnder237b")).json.put(if (flag) JsString("Yes") else JsString("No"))
    } orElse doNothing


  def readsMccloud: Reads[JsObject] =
    (__ \ Symbol("mccloudRemedy") \ Symbol("isPublicServicePensionsRemedy")).read[Boolean]
      .flatMap( flag => (__ \ Symbol("anAllowanceChgPblSerRem")).json.put(booleanToJsString(flag)))


  private def booleanToJsString(b:Boolean): JsString = if (b) JsString("Yes") else JsString("No")

}

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

package transformations.userAnswersToETMP

import play.api.libs.functional.syntax._
import play.api.libs.json.Reads._
import play.api.libs.json._

class ChargeETransformer extends JsonTransformer {
  private def booleanToJsString(b: Boolean): JsString = if (b) JsString("Yes") else JsString("No")

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
      // TODO PODS-8147: Once data (taxYearEnding holding startYear) has been corrected the below line should be uncommented to replace the one below it
//      (__ \ Symbol("taxYearEnding")).json.copyFrom((__ \ Symbol("annualAllowanceYear")).json.pick.map(v => JsString((v.as[String].toInt+1).toString))) and
      (__ \ Symbol("taxYearEnding")).json.copyFrom((__ \ Symbol("annualAllowanceYear")).json.pick.map(v => v.as[JsString])) and
      ((__ \ Symbol("memberStatus")).json.copyFrom((__ \ Symbol("memberStatus")).json.pick)
        orElse (__ \ Symbol("memberStatus")).json.put(JsString("New"))) and
      ((__ \ Symbol("memberAFTVersion")).json.copyFrom((__ \ Symbol("memberAFTVersion")).json.pick)
        orElse doNothing) and readsMccloud
      ).reduce.orElseEmptyOnMissingFields

  private def getPaidUnder237b: Reads[JsObject] =
    (__ \ Symbol("chargeDetails") \ Symbol("isPaymentMandatory")).read[Boolean].flatMap { flag =>
      (__ \ Symbol("paidUnder237b")).json.put(if (flag) JsString("Yes") else JsString("No"))
    } orElse doNothing

  private val readsScheme: Reads[JsObject] = (
    (__ \ Symbol("pstr")).json.copyFrom((__ \ Symbol("pstr")).json.pick) and
      (__ \ Symbol("repPeriodForAac")).json.copyFrom((__ \ Symbol("taxQuarterReportedAndPaid") \ "endDate").json.pick) and
      (__ \ Symbol("amtOrRepAaChg")).json.copyFrom((__ \ Symbol("chargeAmountReported")).json.pick)
    ).reduce

  private val readsAllSchemes: Reads[JsArray] =
    (__ \ Symbol("mccloudRemedy") \ "schemes").readNullable(Reads.seq(readsScheme)).flatMap {
      case None => Reads.failed("No schemes specified")
      case Some(seqScheme) => Reads.pure(JsArray(seqScheme))
    }

  private val readsSingleScheme: Reads[JsArray] =
    (
      (__ \ Symbol("repPeriodForAac")).json.copyFrom((__ \ Symbol("mccloudRemedy") \ Symbol("taxQuarterReportedAndPaid") \ "endDate").json.pick) and
        (__ \ Symbol("amtOrRepAaChg")).json.copyFrom((__ \ Symbol("mccloudRemedy") \ Symbol("chargeAmountReported")).json.pick)
      ).reduce.map(jsObject => Json.arr(jsObject))

  private def readsPensionSchemeDetails(isAnother: Boolean): Reads[JsObject] = {
    if (isAnother) {
      (__ \ Symbol("pensionSchemeDetails")).json.copyFrom(readsAllSchemes)
    } else {
      (__ \ Symbol("pensionSchemeDetails")).json.copyFrom(readsSingleScheme)
    }
  }

  def readsMccloud: Reads[JsObject] = {
    (for {
      isPensionRemedy <- (__ \ Symbol("mccloudRemedy") \ Symbol("isPublicServicePensionsRemedy")).readNullable[Boolean]
      isInAddition <- (__ \ Symbol("mccloudRemedy") \ Symbol("isChargeInAdditionReported")).readNullable[Boolean]
      optIsAnother <- (__ \ Symbol("mccloudRemedy") \ Symbol("wasAnotherPensionScheme")).readNullable[Boolean]
    } yield {
      (isPensionRemedy, isInAddition, optIsAnother) match {
        case (Some(true), Some(true), Some(isAnother)) =>
          (
            (__ \ Symbol("anAllowanceChgPblSerRem")).json.put(booleanToJsString(true)) and
              (__ \ Symbol("orChgPaidbyAnoPS")).json.put(booleanToJsString(isAnother)) and
              readsPensionSchemeDetails(isAnother)
            ).reduce
        case (Some(true), Some(false), _) => (__ \ Symbol("anAllowanceChgPblSerRem")).json.put(booleanToJsString(false))
        case (Some(false) | None, _, _) => (__ \ Symbol("anAllowanceChgPblSerRem")).json.put(booleanToJsString(false))
        case (a, b, c) =>
          fail[JsObject](s"Invalid values entered:- isPublicServicePensionsRemedy: $a isChargeInAdditionReported: $b wasAnotherPensionScheme: $c")
      }

    }).flatMap(identity)
  }


}

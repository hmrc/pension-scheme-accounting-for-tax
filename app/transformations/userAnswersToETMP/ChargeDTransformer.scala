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

class ChargeDTransformer extends JsonTransformer {
  private def booleanToJsString(b: Boolean): JsString = if (b) JsString("Yes") else JsString("No")

  def transformToETMPData: Reads[JsObject] =
    (__ \ Symbol("chargeDDetails")).readNullable(__.read(readsChargeD)).map(_.getOrElse(Json.obj())).orElseEmptyOnMissingFields

  private def readsChargeD: Reads[JsObject] =
    (__ \ Symbol("totalChargeAmount")).read[BigDecimal].flatMap { _ =>
      (((__ \ Symbol("chargeDetails") \ Symbol("chargeTypeDDetails") \ Symbol("amendedVersion")).json.copyFrom((__ \ Symbol("amendedVersion")).json.pick)
        orElse doNothing) and
        (__ \ Symbol("chargeDetails") \ Symbol("chargeTypeDDetails") \ Symbol("memberDetails")).json.copyFrom((__ \ Symbol("members")).read(readsMembers)) and
        (__ \ Symbol("chargeDetails") \ Symbol("chargeTypeDDetails") \ Symbol("totalAmount"))
          .json.copyFrom((__ \ Symbol("totalChargeAmount")).json.pick)).reduce
    }

  private def readsMembers: Reads[JsArray] = readsFiltered(_ \ "memberDetails", readsMember).map(JsArray(_)).map(removeEmptyObjects)

  private def readsMember: Reads[JsObject] = {
    (readsMemberDetails and
      (__ \ Symbol("dateOfBeneCrysEvent")).json.copyFrom((__ \ Symbol("chargeDetails") \ Symbol("dateOfEvent")).json.pick) and
      (__ \ Symbol("totalAmtOfTaxDueAtLowerRate")).json.copyFrom((__ \ Symbol("chargeDetails") \ Symbol("taxAt25Percent")).json.pick) and
      (__ \ Symbol("totalAmtOfTaxDueAtHigherRate")).json.copyFrom((__ \ Symbol("chargeDetails") \ Symbol("taxAt55Percent")).json.pick) and
      ((__ \ Symbol("memberStatus")).json.copyFrom((__ \ Symbol("memberStatus")).json.pick)
        orElse (__ \ Symbol("memberStatus")).json.put(JsString("New"))) and
      ((__ \ Symbol("memberAFTVersion")).json.copyFrom((__ \ Symbol("memberAFTVersion")).json.pick)
        orElse doNothing) and readsMccloud).reduce.orElseEmptyOnMissingFields
  }

  private val readsScheme: Reads[JsObject] = (
    (__ \ Symbol("pstr")).json.copyFrom((__ \ Symbol("pstr")).json.pick) and
      (__ \ Symbol("repPeriodForLtac")).json.copyFrom((__ \ Symbol("taxQuarterReportedAndPaid") \ "endDate").json.pick) and
      (__ \ Symbol("amtOrRepLtaChg")).json.copyFrom((__ \ Symbol("chargeAmountReported")).json.pick)
    ).reduce

  private val readsAllSchemes: Reads[JsArray] =
    (__ \ Symbol("mccloudRemedy") \ "schemes").readNullable(Reads.seq(readsScheme)).flatMap {
      case None => Reads.failed("No schemes specified")
      case Some(seqScheme) => Reads.pure(JsArray(seqScheme))
    }

  private val readsSingleScheme: Reads[JsArray] =
    (
      (__ \ Symbol("repPeriodForLtac")).json.copyFrom((__ \ Symbol("mccloudRemedy") \ Symbol("taxQuarterReportedAndPaid") \ "endDate").json.pick) and
        (__ \ Symbol("amtOrRepLtaChg")).json.copyFrom((__ \ Symbol("mccloudRemedy") \ Symbol("chargeAmountReported")).json.pick)
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
            (__ \ Symbol("lfAllowanceChgPblSerRem")).json.put(booleanToJsString(true)) and
            (__ \ Symbol("orLfChgPaidbyAnoPS")).json.put(booleanToJsString(isAnother)) and
              readsPensionSchemeDetails(isAnother)
            ).reduce
        case (Some(true), Some(false), _) => (__ \ Symbol("lfAllowanceChgPblSerRem")).json.put(booleanToJsString(false))
        case (Some(false) | None, _, _) => (__ \ Symbol("lfAllowanceChgPblSerRem")).json.put(booleanToJsString(false))
        case (a, b, c) =>
          fail[JsObject](s"Invalid values entered:- isPublicServicePensionsRemedy: $a isChargeInAdditionReported: $b wasAnotherPensionScheme: $c")
      }

    }).flatMap(identity)
  }
}

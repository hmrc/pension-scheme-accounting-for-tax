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

package services

import play.api.libs.json.{JsArray, JsValue}

class AFTService {

  def isChargeZeroedOut(jsValue: JsValue): Boolean = {
    val memberLevelCharges = Seq("chargeTypeCDetails", "chargeTypeDDetails", "chargeTypeEDetails", "chargeTypeGDetails")
    val schemeLevelCharges = Seq("chargeTypeADetails", "chargeTypeBDetails", "chargeTypeFDetails")

    val allNonEmptyCharges: Seq[(Boolean, String)] =
      (memberLevelCharges.map(chargeDetails => ((jsValue \ "chargeDetails" \ chargeDetails).isDefined, chargeDetails)) ++
        schemeLevelCharges.map(chargeDetails => ((jsValue \ "chargeDetails" \ chargeDetails).isDefined, chargeDetails)))
        .filter(_._1)

    if (allNonEmptyCharges.size == 1) {
      allNonEmptyCharges.headOption match {
        case Some((_, "chargeTypeGDetails")) => onlyLastZeroAmountMemberG(chargeType = "chargeTypeGDetails", jsValue)
        case Some((_, chargeType)) if memberLevelCharges.contains(chargeType) => onlyLastZeroAmountMember(chargeType, jsValue)
        case Some((_, chargeType)) => (jsValue \ "chargeDetails" \ chargeType \ "totalAmount").asOpt[BigDecimal].contains(zeroCurrencyValue)
        case _ => false
      }
    } else {
      false
    }
  }

  private val zeroCurrencyValue = BigDecimal(0.00)

  private def onlyLastZeroAmountMemberG(chargeType: String, jsValue: JsValue): Boolean = {
    (jsValue \ "chargeDetails" \ chargeType \ "memberDetails").validate[JsArray].asOpt.exists(_.value.size == 1) &&
      (jsValue \ "chargeDetails" \ chargeType \ "totalOTCAmount").asOpt[BigDecimal].contains(zeroCurrencyValue)
  }

  private def onlyLastZeroAmountMember(chargeType: String, jsValue: JsValue): Boolean = {
    (jsValue \ "chargeDetails" \ chargeType \ "memberDetails").validate[JsArray].asOpt.exists(_.value.size == 1) &&
      (jsValue \ "chargeDetails" \ chargeType \ "totalAmount").asOpt[BigDecimal].contains(zeroCurrencyValue)
  }
}

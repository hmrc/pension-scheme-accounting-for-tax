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

package services

import play.api.libs.json.{JsArray, JsValue}

class AFTService {

  def isOnlyOneChargeWithOneMemberAndNoValue(jsValue: JsValue): Boolean = {

    val areNoChargesWithValues: Boolean =
      (jsValue \ "chargeDetails" \ "chargeTypeADetails" \ "totalAmount").toOption.flatMap(_.validate[BigDecimal].asOpt).forall(_ == zeroCurrencyValue) &&
        (jsValue \ "chargeDetails" \ "chargeTypeBDetails" \ "totalAmount").toOption.flatMap(_.validate[BigDecimal].asOpt).forall(_ == zeroCurrencyValue) &&
        (jsValue \ "chargeDetails" \ "chargeTypeCDetails" \ "totalAmount").toOption.flatMap(_.validate[BigDecimal].asOpt).forall(_ == zeroCurrencyValue) &&
        (jsValue \ "chargeDetails" \ "chargeTypeDDetails" \ "totalAmount").toOption.flatMap(_.validate[BigDecimal].asOpt).forall(_ == zeroCurrencyValue) &&
        (jsValue \ "chargeDetails" \ "chargeTypeEDetails" \ "totalAmount").toOption.flatMap(_.validate[BigDecimal].asOpt).forall(_ == zeroCurrencyValue) &&
        (jsValue \ "chargeDetails" \ "chargeTypeFDetails" \ "totalAmount").toOption.flatMap(_.validate[BigDecimal].asOpt).forall(_ == zeroCurrencyValue) &&
        (jsValue \ "chargeDetails" \ "chargeTypeGDetails" \ "totalOTCAmount").toOption.flatMap(_.validate[BigDecimal].asOpt).forall(_ == zeroCurrencyValue)

    val isOnlyOneChargeWithOneMember: Boolean = Seq(
        (jsValue \ "chargeDetails" \ "chargeTypeCDetails" \ "memberDetails").validate[JsArray].asOpt.exists(_.value.size == 1),
        (jsValue \ "chargeDetails" \ "chargeTypeDDetails" \ "memberDetails").validate[JsArray].asOpt.exists(_.value.size == 1),
        (jsValue \ "chargeDetails" \ "chargeTypeEDetails" \ "memberDetails").validate[JsArray].asOpt.exists(_.value.size == 1),
        (jsValue \ "chargeDetails" \ "chargeTypeGDetails" \ "memberDetails").validate[JsArray].asOpt.exists(_.value.size == 1)
      ).count(_ == true) == 1

    areNoChargesWithValues && isOnlyOneChargeWithOneMember
  }

  private val zeroCurrencyValue = BigDecimal(0.00)

}

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

package models

import java.time.LocalDate

import play.api.libs.functional.syntax._
import play.api.libs.json.{Format, JsPath, Json, Reads}

case class PsaFS(chargeReference: String, chargeType: String, dueDate: Option[LocalDate],
                 amountDue: BigDecimal, outstandingAmount: BigDecimal, stoodOverAmount: BigDecimal,
                 periodStartDate: LocalDate, periodEndDate: LocalDate, pstr: String)

object PsaFS {

  implicit val rds: Reads[PsaFS] = (
    (JsPath \ "chargeReference").read[String] and
      (JsPath \ "chargeType").read[String] and
      (JsPath \ "dueDate").readNullable[String] and
      (JsPath \ "amountDue").read[BigDecimal] and
      (JsPath \ "outstandingAmount").read[BigDecimal] and
      (JsPath \ "stoodOverAmount").read[BigDecimal] and
      (JsPath \ "periodStartDate").read[String] and
      (JsPath \ "periodEndDate").read[String] and
      (JsPath \ "pstr").read[String]
    ) (
    (chargeReference, chargeType, dueDateOpt,
     amountDue, outstandingAmount, stoodOverAmount,
     periodStartDate, periodEndDate, pstr) =>
      PsaFS(
        chargeReference,
        PsaChargeType.valueWithName(chargeType),
        dueDateOpt.map(LocalDate.parse),
        amountDue,
        outstandingAmount,
        stoodOverAmount,
        LocalDate.parse(periodStartDate),
        LocalDate.parse(periodEndDate),
        pstr
      )
  )

  implicit val formats: Format[PsaFS] = Json.format[PsaFS]
}

object PsaChargeType extends Enumeration {

  sealed case class TypeValue(name: String, value: String) extends Val(name)

  val aftInitialLFP = TypeValue("57001080", "Accounting for Tax late filing penalty")
  val aftDailyLFP = TypeValue("57001091", "Accounting for Tax further late filing penalty")
  val aft30DayLPP = TypeValue("57301080", "Accounting for Tax late payment penalty (30 days)")
  val aft6MonthLPP = TypeValue("57301091", "Accounting for Tax late payment penalty (6 months)")
  val aft12MonthLPP = TypeValue("57301092", "Accounting for Tax late payment penalty (12 months)")
  val otc30DayLPP = TypeValue("57401080", "Overseas transfer charge late payment penalty (30 days)")
  val otc6MonthLPP = TypeValue("57401091", "Overseas transfer charge late payment penalty (6 months)")
  val otc12MonthLPP = TypeValue("57401092", "Overseas transfer charge late payment penalty (12 months)")
  val paymentOnAccount = TypeValue("00600100", "Payment on account")

  def valueWithName(name: String): String = {
    withName(name).asInstanceOf[TypeValue].value
  }
}
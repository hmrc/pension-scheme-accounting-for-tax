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

case class SchemeFS(chargeReference: String, chargeType: String, dueDate: Option[LocalDate], amountDue: BigDecimal,
                    outstandingAmount: BigDecimal, accruedInterestTotal: BigDecimal, stoodOverAmount: BigDecimal,
                    periodStartDate: Option[LocalDate], periodEndDate: Option[LocalDate])

object SchemeFS {

  implicit val rds: Reads[SchemeFS] = (
    (JsPath \ "chargeReference").read[String] and
      (JsPath \ "chargeType").read[String] and
      (JsPath \ "dueDate").readNullable[String] and
      (JsPath \ "amountDue").read[BigDecimal] and
      (JsPath \ "outstandingAmount").read[BigDecimal] and
      (JsPath \ "accruedInterestTotal").read[BigDecimal] and
      (JsPath \ "stoodOverAmount").read[BigDecimal] and
      (JsPath \ "periodStartDate").readNullable[String] and
      (JsPath \ "periodEndDate").readNullable[String]
    ) (
    (chargeReference, chargeType, dueDateOpt, amountDue, outstandingAmount, accruedInterestTotal, stoodOverAmount, periodStartDateOpt, periodEndDateOpt) =>
      SchemeFS(
        chargeReference,
        ChargeType.valueWithName(chargeType),
        dueDateOpt.map(LocalDate.parse),
        amountDue,
        outstandingAmount,
        accruedInterestTotal,
        stoodOverAmount,
        periodStartDateOpt.map(LocalDate.parse),
        periodEndDateOpt.map(LocalDate.parse)
      )
  )

  implicit val formats: Format[SchemeFS] = Json.format[SchemeFS]
}

object ChargeType extends Enumeration {

  sealed case class TypeValue(name: String, value: String) extends Val(name)

  val aftReturn = TypeValue("56001000", "PSS AFT Return")
  val aftReturnInterest = TypeValue("56052000", "PSS AFT Return Interest")
  val otcAftReturn = TypeValue("56101000", "PSS OTC AFT Return")
  val otcAftReturnInterest = TypeValue("56152000", "PSS OTC AFT Return Interest")
  val paymentOnAccount = TypeValue("00600100", "Payment on Account")

  def valueWithName(name: String): String = {
    withName(name).asInstanceOf[TypeValue].value
  }
}


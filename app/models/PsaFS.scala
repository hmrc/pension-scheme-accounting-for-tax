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
        chargeTypeMap.getOrElse(chargeType, "Unknown charge type"),
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
  
  val chargeTypeMap: Map[String, String] = Map(
    "57001080" -> "AFT Initial LFP",
    "57001091" -> "AFT Daily LFP",
    "57301080" -> "AFT 30 Day LPP",
    "57301091" -> "AFT 6 Month LPP",
    "57301092" -> "AFT 12 Month LPP",
    "57401080" -> "OTC 30 Day LPP",
    "57401091" -> "OTC 6 Month LPP",
    "57401092" -> "OTC 12 Month LPP",
    "00600100" -> "Payment on Account"
  )
}
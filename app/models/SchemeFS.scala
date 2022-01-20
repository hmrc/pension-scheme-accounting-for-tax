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

package models

import java.time.LocalDate

import play.api.libs.functional.syntax._
import play.api.libs.json.{Format, JsPath, Json, Reads}

case class SchemeFS(chargeReference: String, chargeType: String, dueDate: Option[LocalDate], totalAmount: BigDecimal, amountDue: BigDecimal,
                    outstandingAmount: BigDecimal, accruedInterestTotal: BigDecimal, stoodOverAmount: BigDecimal,
                    periodStartDate: Option[LocalDate], periodEndDate: Option[LocalDate],
                    formBundleNumber: Option[String] = None,
                    clearedAmountItem: Option[BigDecimal] = None,
                    sourceChargeRefForInterest: Option[String] = None,
                    clearingDate: Option[LocalDate] = None,
                    clearingReason:Option[String] = None
                   )

object SchemeFS {

  implicit val rds: Reads[SchemeFS] = (
    (JsPath \ "chargeReference").read[String] and
      (JsPath \ "chargeType").read[String] and
      (JsPath \ "dueDate").readNullable[String] and
      (JsPath \ "totalAmount").read[BigDecimal] and
      (JsPath \ "amountDue").read[BigDecimal] and
      (JsPath \ "outstandingAmount").read[BigDecimal] and
      (JsPath \ "accruedInterestTotal").read[BigDecimal] and
      (JsPath \ "stoodOverAmount").read[BigDecimal] and
      (JsPath \ "periodStartDate").readNullable[String] and
      (JsPath \ "periodEndDate").readNullable[String]
    ) (
    (chargeReference, chargeType, dueDateOpt, totalAmount, amountDue, outstandingAmount,
     accruedInterestTotal, stoodOverAmount, periodStartDateOpt, periodEndDateOpt) =>
      SchemeFS(
        chargeReference,
        SchemeChargeType.valueWithName(chargeType),
        dueDateOpt.map(LocalDate.parse),
        totalAmount,
        amountDue,
        outstandingAmount,
        accruedInterestTotal,
        stoodOverAmount,
        periodStartDateOpt.map(LocalDate.parse),
        periodEndDateOpt.map(LocalDate.parse)
      )
  )



  /*
      "sapDocumentNumber"-> "123456789192",
    "postingDate"-> "<StartOfQ1LastYear>",
    "clearedAmountTotal"-> 7035.10,
    "formbundleNumber"-> "123456789192",
    "chargeClassification"-> "Charge",
    "sourceChargeRefForInterest"-> "XY002610150181",
    "documentLineItemDetails"-> Json.obj(
      "sapDocumentItemKey"-> "0000001000",
      "documentLineItemAmount"-> 0.00,
      "accruedInterestItem"-> 0.00,
      "clearingStatus"-> "Open",
      "clearedAmountItem"-> 0.00,
      "stoodOverLock"-> false,
      "clearingLock"-> false,
      "clearingDate"-> "2020-06-30",
      "clearingReason"-> "C1",
      "paymDateOrCredDueDate"-> "<StartOfQ1LastYear>"
   */


  implicit val rdsMax: Reads[SchemeFS] = (
    (JsPath \ "chargeReference").read[String] and
      (JsPath \ "chargeType").read[String] and
      (JsPath \ "dueDate").readNullable[String] and
      (JsPath \ "totalAmount").read[BigDecimal] and
      (JsPath \ "amountDue").read[BigDecimal] and
      (JsPath \ "outstandingAmount").read[BigDecimal] and
      (JsPath \ "accruedInterestTotal").read[BigDecimal] and
      (JsPath \ "stoodOverAmount").read[BigDecimal] and
      (JsPath \ "periodStartDate").readNullable[String] and
      (JsPath \ "periodEndDate").readNullable[String] and
      (JsPath \ "formbundleNumber").readNullable[String]  and
      (JsPath \ "documentLineItemDetails" \ "clearedAmountItem").readNullable[BigDecimal] and
      (JsPath \ "sourceChargeRefForInterest").readNullable[String] and
      (JsPath \ "documentLineItemDetails" \"clearingDate").readNullable[LocalDate] and
      (JsPath \ "documentLineItemDetails" \"clearingReason").readNullable[String]
    ) (
    (chargeReference, chargeType, dueDateOpt, totalAmount, amountDue, outstandingAmount,
     accruedInterestTotal, stoodOverAmount, periodStartDateOpt, periodEndDateOpt,
     formBundleNumber, clearedAmountItem, sourceChargeRefForInterest, clearingDate, clearingReason) =>
      SchemeFS(
        chargeReference,
        SchemeChargeType.valueWithName(chargeType),
        dueDateOpt.map(LocalDate.parse),
        totalAmount,
        amountDue,
        outstandingAmount,
        accruedInterestTotal,
        stoodOverAmount,
        periodStartDateOpt.map(LocalDate.parse),
        periodEndDateOpt.map(LocalDate.parse),
        formBundleNumber ,
        clearedAmountItem,
        sourceChargeRefForInterest,
        clearingDate,
        clearingReason
      )
  )




  implicit val formats: Format[SchemeFS] = Json.format[SchemeFS]
}

object SchemeChargeType extends Enumeration {

  sealed case class TypeValue(name: String, value: String) extends Val(name)

  val aftReturn: TypeValue = TypeValue("56001000", "Accounting for Tax return")
  val aftReturnInterest: TypeValue = TypeValue("56052000", "Interest on Accounting for Tax return")
  val otcAftReturn: TypeValue = TypeValue("56101000", "Overseas transfer charge")
  val otcAftReturnInterest: TypeValue = TypeValue("56152000", "Interest on overseas transfer charge")
  val paymentOnAccount: TypeValue = TypeValue("00600100", "Payment on account")
  val aftManualAssessment: TypeValue = TypeValue("56201000", "AFT manual assessment")
  val aftManualAssessmentInterest: TypeValue = TypeValue("56252000", "Interest on AFT manual assessment")
  val otcManualAssessment: TypeValue = TypeValue("56301000", "OTC manual assessment")
  val otcManualAssessmentInterest: TypeValue = TypeValue("56352000", "Interest on OTC manual assessment")
  val pssCharge: TypeValue = TypeValue("56701000", "PSS charge")
  val pssChargeInterest: TypeValue = TypeValue("56752000", "PSS charge interest")
  val contractSettlement: TypeValue = TypeValue("56901000", "Contract settlement")
  val contractSettlementInterest: TypeValue = TypeValue("56952000", "Contract settlement interest")
  val repaymentInterest: TypeValue = TypeValue("56962925", "Repayment interest")
  val excessReliefPaidCharge: TypeValue = TypeValue("57701000", "Excess relief paid")
  val excessReliefIntCharge: TypeValue = TypeValue("57751000", "Interest on excess relief")

  def valueWithName(name: String): String = {
    withName(name).asInstanceOf[TypeValue].value
  }
}


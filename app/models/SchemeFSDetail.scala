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

import play.api.libs.functional.syntax._
import play.api.libs.json._

import java.time.LocalDate

case class DocumentLineItemDetail(clearedAmountItem: BigDecimal, clearingDate: Option[LocalDate], clearingReason: Option[String])

object DocumentLineItemDetail {
  implicit val formats: Format[DocumentLineItemDetail] = Json.format[DocumentLineItemDetail]
}

case class AccountHeaderDetails(inhibitRefundSignal: Boolean)

object AccountHeaderDetails {
  implicit val formats: Format[AccountHeaderDetails] = Json.format[AccountHeaderDetails]
}

case class SchemeFSDetail(
                     chargeReference: String,
                     chargeType: String,
                     dueDate: Option[LocalDate],
                     totalAmount: BigDecimal,
                     amountDue: BigDecimal,
                     outstandingAmount: BigDecimal,
                     accruedInterestTotal: BigDecimal,
                     stoodOverAmount: BigDecimal,
                     periodStartDate: Option[LocalDate],
                     periodEndDate: Option[LocalDate],
                     formBundleNumber: Option[String] = None,
                     aftVersion: Option[Int] = None,
                     sourceChargeRefForInterest: Option[String] = None,
                     documentLineItemDetails: Seq[DocumentLineItemDetail] = Nil
                   )

object SchemeFSDetail {
  implicit val formats: Format[SchemeFSDetail] = Json.format[SchemeFSDetail]
}


case class SchemeFS(
                     accountHeaderDetails: Option[AccountHeaderDetails] = None,
                     seqSchemeFSDetail: Seq[SchemeFSDetail]
                          )

object SchemeFS {

  implicit val rds: Reads[SchemeFSDetail] = (
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
      SchemeFSDetail(
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

  implicit val rdsDocumentLineItemDetail: Reads[DocumentLineItemDetail] = (
    (JsPath \ "clearedAmountItem").read[BigDecimal] and
      (JsPath \ "clearingDate").readNullable[LocalDate] and
      (JsPath \ "clearingReason").readNullable[String]
    ) (
    (clearedAmountItem, clearingDate, clearingReason) =>
      DocumentLineItemDetail(
        clearedAmountItem,
        clearingDate,
        clearingReason
      )
  )

  implicit val rdsMax: Reads[SchemeFSDetail] = (
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
      (JsPath \ "formbundleNumber").readNullable[String] and
      (JsPath \ "aftVersion").readNullable[Int] and
      (JsPath \ "sourceChargeRefForInterest").readNullable[String] and
      (JsPath \ "documentLineItemDetails").read(Reads.seq(rdsDocumentLineItemDetail))
    ) (
    (chargeReference, chargeType, dueDateOpt, totalAmount, amountDue, outstandingAmount,
     accruedInterestTotal, stoodOverAmount, periodStartDateOpt, periodEndDateOpt,
     formBundleNumber, aftVersionOpt, sourceChargeRefForInterest, documentLineItemDetails) =>
      SchemeFSDetail(
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
        formBundleNumber,
        aftVersionOpt,
        sourceChargeRefForInterest,
        documentLineItemDetails
      )
  )
  implicit val rdsSeq: Reads[SchemeFS] =
    (
      (JsPath \ "accountHeaderDetails").read((JsPath \ "inhibitRefundSignal").read[Boolean]) and
        (JsPath \ "documentHeaderDetails").read(Reads.seq(rds))
      ) (
      (_, documentHeaderDetails) => SchemeFS(None, documentHeaderDetails)
    )

  implicit val rdsMaxSeq: Reads[SchemeFS] =
    (
      (JsPath \ "accountHeaderDetails").read((JsPath \ "inhibitRefundSignal").read[Boolean]) and
        (JsPath \ "documentHeaderDetails").read(Reads.seq(rdsMax))
      ) (
      (inhibitRefundSignal, seqSchemeFSDetail) => SchemeFS(Some(AccountHeaderDetails(inhibitRefundSignal)), seqSchemeFSDetail)
    )

 // implicit val formats: Format[SchemeFSWrapper] = Json.format[SchemeFSWrapper]
}

object SchemeChargeType extends Enumeration {

  sealed case class TypeValue(name: String, value: String) extends Val(name)

  val aftReturn: TypeValue = TypeValue("56001000", "Accounting for Tax return")
  val aftReturnInterest: TypeValue = TypeValue("56052000", "Interest on Accounting for Tax return")
  val otcAftReturn: TypeValue = TypeValue("56101000", "Overseas transfer charge")
  val otcAftReturnInterest: TypeValue = TypeValue("56152000", "Interest on overseas transfer charge")
  val paymentOnAccount: TypeValue = TypeValue("00600100", "Payment on account")
  val aftManualAssessment: TypeValue = TypeValue("56201000", "Accounting for Tax return manual assessment")
  val aftManualAssessmentInterest: TypeValue = TypeValue("56252000", "Interest on Accounting for Tax return manual assessment")
  val otcManualAssessment: TypeValue = TypeValue("56301000", "Overseas transfer charge manual assessment")
  val otcManualAssessmentInterest: TypeValue = TypeValue("56352000", "Interest on overseas transfer charge manual assessment")
  val pssCharge: TypeValue = TypeValue("56701000", "Pensions charge")
  val pssChargeInterest: TypeValue = TypeValue("56752000", "Interest on pensions charge")
  val contractSettlement: TypeValue = TypeValue("56901000", "Contract settlement charge")
  val contractSettlementInterest: TypeValue = TypeValue("56952000", "Contract settlement interest charge")
  val repaymentInterest: TypeValue = TypeValue("56962925", "Repayment interest")
  val excessReliefPaidCharge: TypeValue = TypeValue("57701000", "Excess relief paid charge")
  val excessReliefIntCharge: TypeValue = TypeValue("57751000", "Interest on excess relief charge")

  def valueWithName(name: String): String = {
    withName(name).asInstanceOf[TypeValue].value
  }
}


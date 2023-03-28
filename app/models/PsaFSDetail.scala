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

package models

import play.api.libs.functional.syntax._
import play.api.libs.json._

import java.time.LocalDate

case class PsaFSDetail(index: Int,
                       chargeReference: String,
                       chargeType: String,
                       dueDate: Option[LocalDate],
                       totalAmount: BigDecimal,
                       amountDue: BigDecimal,
                       outstandingAmount: BigDecimal,
                       stoodOverAmount: BigDecimal,
                       accruedInterestTotal: BigDecimal,
                       periodStartDate: LocalDate,
                       periodEndDate: LocalDate,
                       pstr: String,
                       sourceChargeRefForInterest: Option[String] = None,
                       psaSourceChargeInfo: Option[PsaSourceChargeInfo] = None,
                       documentLineItemDetails: Seq[DocumentLineItemDetail] = Nil)

object PsaFSDetail {
  implicit val formats: Format[PsaFSDetail] = Json.format[PsaFSDetail]
}

case class PsaSourceChargeInfo(
                                index: Int,
                                chargeType: String,
                                periodStartDate: LocalDate,
                                periodEndDate: LocalDate
                              )

object PsaSourceChargeInfo {
  implicit val formats: Format[PsaSourceChargeInfo] = Json.format[PsaSourceChargeInfo]
}

case class PsaFS(
                  inhibitRefundSignal: Boolean,
                  seqPsaFSDetail: Seq[PsaFSDetail]
                )

object PsaFS {

  implicit val writesPsaFS: Writes[PsaFS] = (
    (JsPath \ "inhibitRefundSignal").write[Boolean] and
      (JsPath \ "seqPsaFSDetail").write[Seq[PsaFSDetail]]) (x => (x.inhibitRefundSignal, x.seqPsaFSDetail))

  implicit val rdsDocumentLineItemDetail: Reads[DocumentLineItemDetail] = (
    (JsPath \ "clearedAmountItem").read[BigDecimal] and
      (JsPath \ "clearingDate").readNullable[LocalDate] and
      (JsPath \ "paymDateOrCredDueDate").readNullable[LocalDate] and
      (JsPath \ "clearingReason").readNullable[String]
    ) (
    (clearedAmountItem, clearingDate, paymDateOrCredDueDate, clearingReason) =>
      DocumentLineItemDetail(
        clearedAmountItem,
        clearingDate,
        paymDateOrCredDueDate,
        clearingReason
      )
  )

  implicit val rdsPsaFSDetailMax: Reads[PsaFSDetail] = (
    (JsPath \ "chargeReference").read[String] and
      (JsPath \ "chargeType").read[String] and
      (JsPath \ "dueDate").readNullable[String] and
      (JsPath \ "totalAmount").read[BigDecimal] and
      (JsPath \ "amountDue").read[BigDecimal] and
      (JsPath \ "outstandingAmount").read[BigDecimal] and
      (JsPath \ "stoodOverAmount").read[BigDecimal] and
      (JsPath \ "accruedInterestTotal").read[BigDecimal] and
      //The following fields are optional in API but mandatory here based on comment added on PODS-5109
      (JsPath \ "periodStartDate").readNullable[String] and
      (JsPath \ "periodEndDate").readNullable[String] and
      (JsPath \ "pstr").readNullable[String] and
      (JsPath \ "sourceChargeRefForInterest").readNullable[String] and
      (JsPath \ "documentLineItemDetails").read(Reads.seq(rdsDocumentLineItemDetail))
    ) (
    (chargeReference, chargeType, dueDateOpt,
     totalAmount, amountDue, outstandingAmount, stoodOverAmount, accruedInterestTotal,
     periodStartDate, periodEndDate, pstr, sourceChargeRefForInterest, documentLineItemDetails) =>
      PsaFSDetail(
        index = 0,
        chargeReference,
        PsaChargeType.valueWithName(chargeType),
        dueDateOpt.map(LocalDate.parse),
        totalAmount,
        amountDue,
        outstandingAmount,
        stoodOverAmount,
        accruedInterestTotal,
        periodStartDate.map(LocalDate.parse(_)).getOrElse(LocalDate.of(1900,1,1)),
        periodEndDate.map(LocalDate.parse(_)).getOrElse(LocalDate.of(2900,12,31)),
        pstr.getOrElse(""),
        sourceChargeRefForInterest,
        None,
        documentLineItemDetails
      )
  )

  implicit val rdsPsaFSMax: Reads[PsaFS] = {
    def transformExtraFields(seqPsaFSDetail: Seq[PsaFSDetail]): Seq[PsaFSDetail] = {
      val seqPsaFSDetailWithIndexes = seqPsaFSDetail.zipWithIndex.map { case (psaFSDetail, i) =>
        psaFSDetail copy (index = i + 1)
      }
      seqPsaFSDetailWithIndexes.map { psaFSDetail =>
        psaFSDetail.sourceChargeRefForInterest match {
          case Some(ref) => seqPsaFSDetailWithIndexes.find(_.chargeReference == ref) match {
            case Some(foundOriginalCharge) =>
              psaFSDetail copy (
                psaSourceChargeInfo = Some(PsaSourceChargeInfo(
                  index = foundOriginalCharge.index,
                  chargeType = foundOriginalCharge.chargeType,
                  periodStartDate = foundOriginalCharge.periodStartDate,
                  periodEndDate = foundOriginalCharge.periodEndDate
                ))
                )
            case _ => psaFSDetail
          }
          case _ => psaFSDetail
        }
      }
    }

    (
      (JsPath \ "accountHeaderDetails").read((JsPath \ "inhibitRefundSignal").read[Boolean]) and
        (JsPath \ "documentHeaderDetails").read(Reads.seq(rdsPsaFSDetailMax))
      ) (
      (inhibitRefundSignal, seqPsaFSDetail) => PsaFS(inhibitRefundSignal, transformExtraFields(seqPsaFSDetail))
    )
  }
}

object PsaChargeType extends Enumeration {

  sealed case class TypeValue(name: String, value: String) extends Val(name)

  val aftInitialLFP: TypeValue = TypeValue("57001080", "Accounting for Tax Late Filing Penalty")
  val aftDailyLFP: TypeValue = TypeValue("57001091", "Accounting for Tax Further Late Filing Penalty")
  val aft30DayLPP: TypeValue = TypeValue("57301080", "Accounting for Tax 30 Days Late Payment Penalty")
  val aft6MonthLPP: TypeValue = TypeValue("57301091", "Accounting for Tax 6 Months Late Payment Penalty")
  val aft12MonthLPP: TypeValue = TypeValue("57301092", "Accounting for Tax 12 Months Late Payment Penalty")
  val otc30DayLPP: TypeValue = TypeValue("57401080", "Overseas Transfer Charge 30 Days Late Payment Penalty")
  val otc6MonthLPP: TypeValue = TypeValue("57401091", "Overseas Transfer Charge 6 Months Late Payment Penalty")
  val otc12MonthLPP: TypeValue = TypeValue("57401092", "Overseas Transfer Charge 12 Months Late Payment Penalty")
  val pssPenalty: TypeValue = TypeValue("56801090", "Pensions Penalty")
  val pssInfoNotice: TypeValue = TypeValue("57601090", "Information Notice Penalty")
  val contractSettlement: TypeValue = TypeValue("58001000", "Contract Settlement")
  val contractSettlementInterest: TypeValue = TypeValue("58052000", "Contract Settlement Interest")
  val repaymentInterest: TypeValue = TypeValue("57962925", "Repayment Interest")
  val paymentOnAccount: TypeValue = TypeValue("00600100", "Payment on Account")
  val ssc30DayLpp: TypeValue = TypeValue("57501080", "Scheme Sanction Charge 30 Days Late Payment Penalty")
  val ssc6MonthLpp: TypeValue = TypeValue("57501091", "Scheme Sanction Charge 6 Months Late Payment Penalty")
  val ssc12MonthLpp: TypeValue = TypeValue("57501092", "Scheme Sanction Charge 12 Months Late Payment Penalty")
  val ltaDischargeAssessment30DayLpp: TypeValue = TypeValue("56991080", "Lifetime Allowance Discharge Assessment 30 Days Late Payment Penalty")
  val ltaDischargeAssessment6MonthLpp: TypeValue = TypeValue("56991091", "Lifetime Allowance Discharge Assessment 6 Months Late Payment Penalty")
  val ltaDischargeAssessment12MonthLpp: TypeValue = TypeValue("56991092", "Lifetime Allowance Discharge Assessment 12 Months Late Payment Penalty")

  def valueWithName(name: String): String = {
    withName(name).asInstanceOf[TypeValue].value
  }
}

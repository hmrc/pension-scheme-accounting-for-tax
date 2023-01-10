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

  implicit val rdsPsaFSDetailsMedium: Reads[PsaFSDetail] = (
    (JsPath \ "chargeReference").read[String] and
      (JsPath \ "chargeType").read[String] and
      (JsPath \ "dueDate").readNullable[String] and
      (JsPath \ "totalAmount").read[BigDecimal] and
      (JsPath \ "amountDue").read[BigDecimal] and
      (JsPath \ "outstandingAmount").read[BigDecimal] and
      (JsPath \ "stoodOverAmount").read[BigDecimal] and
      (JsPath \ "accruedInterestTotal").read[BigDecimal] and
      //The following fields are optional in API but mandatory here based on comment added on PODS-5109
      (JsPath \ "periodStartDate").read[String] and
      (JsPath \ "periodEndDate").read[String] and
      (JsPath \ "pstr").read[String]
    ) (
    (chargeReference, chargeType, dueDateOpt,
     totalAmount, amountDue, outstandingAmount, stoodOverAmount, accruedInterestTotal,
     periodStartDate, periodEndDate, pstr) =>
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
        LocalDate.parse(periodStartDate),
        LocalDate.parse(periodEndDate),
        pstr
      )
  )

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
      (JsPath \ "periodStartDate").read[String] and
      (JsPath \ "periodEndDate").read[String] and
      (JsPath \ "pstr").read[String] and
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
        LocalDate.parse(periodStartDate),
        LocalDate.parse(periodEndDate),
        pstr,
        sourceChargeRefForInterest,
        None,
        documentLineItemDetails
      )
  )

  implicit val rdsPsaFSMedium: Reads[PsaFS] = {
    Reads.seq(rdsPsaFSDetailsMedium).map {
      seqPsaFSDetail => PsaFS(inhibitRefundSignal = false, seqPsaFSDetail = seqPsaFSDetail)
    }
  }

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

  val aftInitialLFP: TypeValue = TypeValue("57001080", "Accounting for Tax late filing penalty")
  val aftDailyLFP: TypeValue = TypeValue("57001091", "Accounting for Tax further late filing penalty")
  val aft30DayLPP: TypeValue = TypeValue("57301080", "Accounting for Tax late payment penalty (30 days)")
  val aft6MonthLPP: TypeValue = TypeValue("57301091", "Accounting for Tax late payment penalty (6 months)")
  val aft12MonthLPP: TypeValue = TypeValue("57301092", "Accounting for Tax late payment penalty (12 months)")
  val otc30DayLPP: TypeValue = TypeValue("57401080", "Overseas transfer charge late payment penalty (30 days)")
  val otc6MonthLPP: TypeValue = TypeValue("57401091", "Overseas transfer charge late payment penalty (6 months)")
  val otc12MonthLPP: TypeValue = TypeValue("57401092", "Overseas transfer charge late payment penalty (12 months)")
  val pssPenalty: TypeValue = TypeValue("56801090", "Pensions Penalty")
  val pssInfoNotice: TypeValue = TypeValue("57601090", "Information Notice Penalty")
  val contractSettlement: TypeValue = TypeValue("58001000", "Contract settlement charge")
  val contractSettlementInterest: TypeValue = TypeValue("58052000", "Contract settlement interest")
  val repaymentInterest: TypeValue = TypeValue("57962925", "Repayment Interest")
  val paymentOnAccount: TypeValue = TypeValue("00600100", "Payment on account")

  def valueWithName(name: String): String = {
    withName(name).asInstanceOf[TypeValue].value
  }
}

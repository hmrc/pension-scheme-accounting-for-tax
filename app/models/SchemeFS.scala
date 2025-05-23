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

package models

import play.api.libs.functional.syntax._
import play.api.libs.json._

import java.time.LocalDate

case class DocumentLineItemDetail(clearedAmountItem: BigDecimal, clearingDate: Option[LocalDate], paymDateOrCredDueDate: Option[LocalDate], clearingReason: Option[String])

object DocumentLineItemDetail {
  implicit val formats: Format[DocumentLineItemDetail] = Json.format[DocumentLineItemDetail]
}

case class SchemeSourceChargeInfo(
                                   index: Int,
                                   version: Option[Int] = None,
                                   receiptDate: Option[LocalDate] = None,
                                   periodStartDate: Option[LocalDate] = None,
                                   periodEndDate: Option[LocalDate] = None
                                 )

object SchemeSourceChargeInfo {
  implicit val formats: Format[SchemeSourceChargeInfo] = Json.format[SchemeSourceChargeInfo]
}

case class SchemeFSDetail(
                           index: Int,
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
                           version: Option[Int] = None,
                           receiptDate: Option[LocalDate] = None,
                           aftVersion: Option[Int] = None,
                           sourceChargeRefForInterest: Option[String] = None,
                           sourceChargeInfo: Option[SchemeSourceChargeInfo] = None,
                           documentLineItemDetails: Seq[DocumentLineItemDetail] = Nil
                         )

object SchemeFSDetail {
  implicit val formats: Format[SchemeFSDetail] = Json.format[SchemeFSDetail]
}


case class SchemeFS(
                     inhibitRefundSignal: Boolean,
                     seqSchemeFSDetail: Seq[SchemeFSDetail]
                   )

object SchemeFS {

  implicit val writesSchemeFS: Writes[SchemeFS] = (
    (JsPath \ "inhibitRefundSignal").write[Boolean] and
      (JsPath \ "seqSchemeFSDetail").write[Seq[SchemeFSDetail]]) (x => (x.inhibitRefundSignal, x.seqSchemeFSDetail))

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

  implicit val rdsSchemeFSDetailMax: Reads[SchemeFSDetail] = (
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
      (JsPath \ "documentLineItemDetails").read(Reads.seq(rdsDocumentLineItemDetail)) and
      (JsPath \ "reportVersion").readNullable[String] and
      (JsPath \ "submissionDateTime").readNullable[String]
    ) (
    (chargeReference, chargeType, dueDateOpt, totalAmount, amountDue, outstandingAmount,
     accruedInterestTotal, stoodOverAmount, periodStartDateOpt, periodEndDateOpt,
     formBundleNumber, aftVersionOpt, sourceChargeRefForInterest, documentLineItemDetails, reportVersion, submittionDateTime) =>
      SchemeFSDetail(
        index = 0,
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
        reportVersion.map(_.toInt),
        submittionDateTime.map(x => LocalDate.parse(x.split("T").head)),
        aftVersionOpt,
        sourceChargeRefForInterest,
        None,
        documentLineItemDetails
      )
  )

  implicit val rdsSchemeFSMax: Reads[SchemeFS] = {
    def transformExtraFields(seqSchemeFSDetail: Seq[SchemeFSDetail]): Seq[SchemeFSDetail] = {
      val seqSchemeFSDetailWithIndexes = seqSchemeFSDetail.zipWithIndex.map { case (schemeFSDetail, i) =>
        schemeFSDetail.copy (index = i + 1)
      }
      seqSchemeFSDetailWithIndexes.map { schemeFSDetail =>
        schemeFSDetail.sourceChargeRefForInterest match {
          case Some(ref) => seqSchemeFSDetailWithIndexes.find(_.chargeReference == ref) match {
            case Some(foundOriginalCharge) =>
              schemeFSDetail.copy (
                sourceChargeInfo = Some(SchemeSourceChargeInfo(
                  index = foundOriginalCharge.index,
                  periodStartDate = foundOriginalCharge.periodStartDate,
                  periodEndDate = foundOriginalCharge.periodEndDate
                ))
                )
            case _ => schemeFSDetail
          }
          case _ => schemeFSDetail
        }
      }
    }

    (
      (JsPath \ "accountHeaderDetails").read((JsPath \ "inhibitRefundSignal").read[Boolean]) and
        (JsPath \ "documentHeaderDetails").read(Reads.seq(rdsSchemeFSDetailMax))
      ) (
      (inhibitRefundSignal, seqSchemeFSDetail) => SchemeFS(inhibitRefundSignal, transformExtraFields(seqSchemeFSDetail))
    )
  }


  implicit val rdsSchemeFS: Reads[SchemeFS] = {
    def transformExtraFields(seqSchemeFSDetail: Seq[SchemeFSDetail]): Seq[SchemeFSDetail] = {
      val seqSchemeFSDetailWithIndexes = seqSchemeFSDetail.zipWithIndex.map { case (schemeFSDetail, i) =>
        schemeFSDetail.copy (index = i + 1)
      }
      seqSchemeFSDetailWithIndexes.map { schemeFSDetail =>
        schemeFSDetail.sourceChargeRefForInterest match {
          case Some(ref) => seqSchemeFSDetailWithIndexes.find(_.chargeReference == ref) match {
            case Some(foundOriginalCharge) =>
              schemeFSDetail.copy (
                sourceChargeInfo = Some(SchemeSourceChargeInfo(
                  index = foundOriginalCharge.index,
                  periodStartDate = foundOriginalCharge.periodStartDate,
                  periodEndDate = foundOriginalCharge.periodEndDate
                ))
                )
            case _ => schemeFSDetail
          }
          case _ => schemeFSDetail
        }
      }
    }

    (
      (JsPath \ "inhibitRefundSignal").read[Boolean] and
        (JsPath \ "seqSchemeFSDetail").read(Reads.seq(Json.reads[SchemeFSDetail]))
      )(
      (inhibitRefundSignal, seqSchemeFSDetail) => SchemeFS(inhibitRefundSignal, transformExtraFields(seqSchemeFSDetail))
    )
  }
}

object SchemeChargeType extends Enumeration {

  sealed case class TypeValue(name: String, value: String) extends Val(name)

  val aftReturn: TypeValue = TypeValue("56001000", "Accounting for Tax Return")
  val aftReturnCredit: TypeValue = TypeValue("56002930", "Accounting for Tax Return credit")
  val aftReturnInterest: TypeValue = TypeValue("56052000", "Accounting for Tax Return Interest")
  val otcAftReturn: TypeValue = TypeValue("56101000", "Overseas Transfer Charge")
  val otcAftReturnCredit: TypeValue = TypeValue("56102930", "Overseas Transfer Charge credit")
  val otcAftReturnInterest: TypeValue = TypeValue("56152000", "Overseas Transfer Charge Interest")
  val paymentOnAccount: TypeValue = TypeValue("00600100", "Payment on Account")
  val aftManualAssessment: TypeValue = TypeValue("56201000", "Accounting for Tax assessment")
  val aftManualAssessmentCredit: TypeValue = TypeValue("56202930", "Accounting for Tax assessment credit")
  val aftManualAssessmentInterest: TypeValue = TypeValue("56252000", "Interest on Accounting for Tax assessment")
  val otcManualAssessment: TypeValue = TypeValue("56301000", "Overseas Transfer Charge assessment")
  val otcManualAssessmentCredit: TypeValue = TypeValue("56302930", "Overseas Transfer Charge assessment credit")
  val otcManualAssessmentInterest: TypeValue = TypeValue("56352000", "Interest on Overseas Transfer Charge assessment")
  val pssCharge: TypeValue = TypeValue("56701000", "Pensions Charge")
  val pssChargeInterest: TypeValue = TypeValue("56752000", "Pensions Charge Interest")
  val contractSettlement: TypeValue = TypeValue("56901000", "Contract Settlement")
  val contractSettlementInterest: TypeValue = TypeValue("56952000", "Contract Settlement Interest")
  val repaymentInterest: TypeValue = TypeValue("56962925", "Repayment Interest")
  val pssLtaDischargeAssessment: TypeValue = TypeValue("56971000", "Lifetime Allowance Discharge Assessment")
  val pssLtaDischargeAssessmentInterest: TypeValue = TypeValue("56982000", "Lifetime Allowance Assessment Interest")
  val excessReliefPaidCharge: TypeValue = TypeValue("57701000", "Relief at Source Excess Relief Repaid")
  val excessReliefIntCharge: TypeValue = TypeValue("57751000", "Relief at Source Excess Relief Interest Charge")
  val pssSchemeSanctionCharge: TypeValue = TypeValue("56401000", "Scheme Sanction Charge")
  val pssSchemeSanctionCreditCharge: TypeValue = TypeValue("56402930", "Scheme Sanction Charge credit")
  val pssSchemeSanctionChargeInterest: TypeValue = TypeValue("56452000", "Scheme Sanction Charge Interest")
  val pssManualSsc: TypeValue = TypeValue("56601000", "Manual Scheme Sanction Charge")
  val pssManualCreditSsc: TypeValue = TypeValue("56602930", "Manual Scheme Sanction Charge credit")
  val pssManualSchemeSanctionChargeInterest: TypeValue = TypeValue("56652000", "Manual Scheme Sanction Charge Interest")
  val pssFixedChargeMembersTax: TypeValue = TypeValue("56501000", "Member Unauthorised Payments")
  val pssFixedCreditChargeMembersTax: TypeValue = TypeValue("56502930", "Member Unauthorised Payments credit")
  val pssManualFixedChargeMembersTax: TypeValue = TypeValue("57801000", "Manual Member Unauthorised Payments")

  def valueWithName(name: String): String = {
    withName(name).asInstanceOf[TypeValue].value
  }
}


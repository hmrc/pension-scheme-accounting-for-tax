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
import java.util.NoSuchElementException
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.OptionValues
import play.api.libs.json.{JsValue, Json}

class PsaFSReadsSpec extends AnyWordSpec with OptionValues with Matchers {

  import PsaFSReadsSpec._

  "rdsPsaFSDetailsMedium" must {
    "format " when {
      "reading from json" in {
        val result = Json.fromJson[PsaFSDetail](psaFSResponseJson(chargeType = "57401091"))(PsaFS.rdsPsaFSDetailsMedium).asOpt.value
        result mustBe psaFSModel
      }

      "throw NoSuchElementException for invalid charge type" in {
        intercept[NoSuchElementException] {
          Json.fromJson[PsaFSDetail](psaFSResponseJson(chargeType = "56000000"))(PsaFS.rdsPsaFSDetailsMedium).asOpt.value
        }
      }
    }
  }

  "rdsPsaFSDetailMax" must {
    "format " when {
      "reading from json" in {
        val result = Json.fromJson[PsaFSDetail](psaFSMaxResponseJson(chargeType = "57401091"))(PsaFS.rdsPsaFSDetailMax).asOpt.value
        result mustBe psaFSMaxModel
      }

      "throw NoSuchElementException for invalid charge type" in {
        intercept[NoSuchElementException] {
          Json.fromJson[PsaFSDetail](psaFSMaxResponseJson(chargeType = "56000000"))(PsaFS.rdsPsaFSDetailMax).asOpt.value
        }
      }
    }
  }

  "rdsPsaFSMax" must {
    "format " when {
      "reading from json and inhibitRefundSignal is true" in {
        val result = Json.fromJson[PsaFS](psaFSMaxSeqResponse("58001000", "58052000", true))(PsaFS.rdsPsaFSMax).asOpt.value
        result mustBe psaFSMaxTrue
      }

      "reading from json and inhibitRefundSignal is false" in {
        val result = Json.fromJson[PsaFS](psaFSMaxSeqResponse("58001000", "58052000", false))(PsaFS.rdsPsaFSMax).asOpt.value
        result mustBe psaFSMaxFalse
      }

      "throw NoSuchElementException for invalid charge type" in {
        intercept[NoSuchElementException] {
          Json.fromJson[PsaFS](psaFSMaxSeqResponse("56000000", "58001000",false))(PsaFS.rdsPsaFSMax).asOpt.value
        }
      }
    }
  }
}
object PsaFSReadsSpec {

  private def psaFSResponseJson(chargeType: String): JsValue = Json.obj(
    "index" -> 0,
    "chargeReference" -> "XY002610150184",
    "chargeType" -> s"$chargeType",
    "dueDate" -> "2020-02-15",
    "totalAmount" -> 80000.00,
    "outstandingAmount" -> 56049.08,
    "stoodOverAmount" -> 25089.08,
    "accruedInterestTotal" -> 123.32,
    "amountDue" -> 1029.05,
    "periodStartDate" -> "2020-04-01",
    "periodEndDate" -> "2020-06-30",
    "pstr" -> "24000040IN"
  )

  private def psaFSMaxResponseJson(chargeType: String): JsValue = Json.obj(
    "index" -> 0,
    "chargeReference" -> "XY002610150184",
    "chargeType" -> s"$chargeType",
    "dueDate" -> "2020-02-15",
    "totalAmount" -> 80000.00,
    "outstandingAmount" -> 56049.08,
    "stoodOverAmount" -> 25089.08,
    "accruedInterestTotal" -> 123.32,
    "amountDue" -> 1029.05,
    "periodStartDate" -> "2020-04-01",
    "periodEndDate" -> "2020-06-30",
    "pstr" -> "24000040IN",
    "sourceChargeRefForInterest"-> "XY002610150181",
    "sourceChargeIndex" -> None,
    "documentLineItemDetails"-> Json.arr(
      Json.obj(
        "clearingDate"-> "2020-06-30",
        "clearingReason"-> "C1",
        "clearedAmountItem"-> 0.00
      )
  ))

  private def psaFSMaxSeqResponse(chargeType1: String, chargeType2: String, inhibitRefundSignal: Boolean): JsValue = Json.obj(
    "accountHeaderDetails" -> Json.obj("inhibitRefundSignal" -> inhibitRefundSignal),
    "documentHeaderDetails" -> Json.arr(
      Json.obj(
        "index" -> 1,
        "chargeReference" -> "XY002610150184",
        "chargeType" -> s"$chargeType1",
        "totalAmount" -> -15000.00,
        "dueDate" -> "2020-06-25",
        "amountDue" -> -15000.00,
        "outstandingAmount" -> -15000.00,
        "stoodOverAmount" -> 0.00,
        "accruedInterestTotal" -> 0.00,
        "periodStartDate" -> "2020-04-01",
        "periodEndDate" -> "2020-06-30",
        "pstr" -> "24000040IN",
        "sourceChargeRefForInterest"-> "XY002610150181",
        "sourceChargeIndex" -> None,
        "documentLineItemDetails"-> Json.arr(
          Json.obj(
            "clearingDate"-> "2020-06-30",
            "clearingReason"-> "C1",
            "clearedAmountItem"-> 0.00
          )
        )
      ),
      Json.obj(
        "index" -> 2,
        "chargeReference" -> "Not Applicable",
        "chargeType" -> s"$chargeType2",
        "dueDate" -> "2020-02-15",
        "totalAmount" -> 80000.00,
        "outstandingAmount" -> 56049.08,
        "stoodOverAmount" -> 25089.08,
        "accruedInterestTotal" -> 123.00,
        "amountDue" -> 1029.05,
        "periodStartDate" -> "2020-04-01",
        "periodEndDate" -> "2020-06-30",
        "pstr" -> "24000040IN",
        "sourceChargeRefForInterest"-> "XY002610150184",
        "sourceChargeIndex" -> Some(1),
        "documentLineItemDetails"-> Json.arr(
          Json.obj(
            "clearingDate"-> "2020-06-30",
            "clearingReason"-> "C1",
            "clearedAmountItem"-> 0.00
          )
        )
      )
    )
  )

  private def psaFSModel = PsaFSDetail(
    index = 0,
    chargeReference = "XY002610150184",
    chargeType = "Overseas transfer charge late payment penalty (6 months)",
    dueDate = Some(LocalDate.parse("2020-02-15")),
    totalAmount = 80000.00,
    outstandingAmount = 56049.08,
    stoodOverAmount = 25089.08,
    accruedInterestTotal = 123.32,
    amountDue = 1029.05,
    periodStartDate = LocalDate.parse("2020-04-01"),
    periodEndDate = LocalDate.parse("2020-06-30"),
    pstr = "24000040IN"
  )

  private def psaFSMaxModel = PsaFSDetail(
    index = 0,
    chargeReference = "XY002610150184",
    chargeType = "Overseas transfer charge late payment penalty (6 months)",
    dueDate = Some(LocalDate.parse("2020-02-15")),
    totalAmount = 80000.00,
    outstandingAmount = 56049.08,
    stoodOverAmount = 25089.08,
    accruedInterestTotal = 123.32,
    amountDue = 1029.05,
    periodStartDate = LocalDate.parse("2020-04-01"),
    periodEndDate = LocalDate.parse("2020-06-30"),
    pstr = "24000040IN",
    sourceChargeRefForInterest = Some("XY002610150181"),
    sourceChargeInfo = None,
    documentLineItemDetails = Seq(DocumentLineItemDetail(
      clearingReason= Some("C1"),
      clearingDate = Some(LocalDate.parse("2020-06-30")),
      clearedAmountItem = BigDecimal(0.00))
    )
  )

  private val sourceChargeInfo : SourceChargeInfo = SourceChargeInfo(
    index = 1,
    chargeType = "Contract settlement charge",
    periodStartDate = LocalDate.parse("2020-04-01"),
    periodEndDate = LocalDate.parse("2020-06-30"),
    pstr = "24000040IN"
  )

  private val psaFSMaxSeqModel: Seq[PsaFSDetail] = Seq(
    PsaFSDetail(
      index = 1,
      chargeReference = "XY002610150184",
      chargeType = "Contract settlement charge",
      dueDate = Some(LocalDate.parse("2020-06-25")),
      totalAmount = -15000.00,
      amountDue = -15000.00,
      outstandingAmount = -15000.00,
      stoodOverAmount = 0.00,
      accruedInterestTotal = 0.00,
      periodStartDate = LocalDate.parse("2020-04-01"),
      periodEndDate = LocalDate.parse("2020-06-30"),
      pstr = "24000040IN",
      sourceChargeRefForInterest = Some("XY002610150181"),
      sourceChargeInfo = None,
      Seq(DocumentLineItemDetail(
        clearingReason = Some("C1"),
        clearingDate = Some(LocalDate.parse("2020-06-30")),
        clearedAmountItem = BigDecimal(0.00))
      )
    ),
    PsaFSDetail(
      index = 2,
      chargeReference = "Not Applicable",
      chargeType = "Contract settlement interest",
      dueDate = Some(LocalDate.parse("2020-02-15")),
      totalAmount = 80000.00,
      amountDue = 1029.05,
      outstandingAmount = 56049.08,
      stoodOverAmount = 25089.08,
      accruedInterestTotal = 123.00,
      periodStartDate = LocalDate.parse("2020-04-01"),
      periodEndDate = LocalDate.parse("2020-06-30"),
      pstr = "24000040IN",
      sourceChargeRefForInterest = Some("XY002610150184"),
      sourceChargeInfo = Some(sourceChargeInfo),
      Seq(DocumentLineItemDetail(
        clearingReason = Some("C1"),
        clearingDate = Some(LocalDate.parse("2020-06-30")),
        clearedAmountItem = BigDecimal(0.00))
      )
    )
  )

  private def psaFSMaxTrue = PsaFS(
    inhibitRefundSignal = true,
    seqPsaFSDetail = psaFSMaxSeqModel
  )

  private def psaFSMaxFalse = PsaFS(
    inhibitRefundSignal = false,
    seqPsaFSDetail = psaFSMaxSeqModel
  )
}
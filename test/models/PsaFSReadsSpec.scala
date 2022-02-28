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

  "Psa FS" must {
    "format " when {
      "reading from json" in {
        val result = Json.fromJson[PsaFS](psaFSResponseJson(chargeType = "57401091"))(PsaFS.rds).asOpt.value
        result mustBe psaFSModel
      }

      "throw NoSuchElementException for invalid charge type" in {
        intercept[NoSuchElementException] {
          Json.fromJson[PsaFS](psaFSResponseJson(chargeType = "56000000"))(PsaFS.rds).asOpt.value
        }
      }
    }
  }

  "Psa Max FS" must {
    "format " when {
      "reading from json" in {
        val result = Json.fromJson[PsaFS](psaFSMaxResponseJson(chargeType = "57401091"))(PsaFS.rdsMax).asOpt.value
        result mustBe psaFSMaxModel
      }

      "throw NoSuchElementException for invalid charge type" in {
        intercept[NoSuchElementException] {
          Json.fromJson[PsaFS](psaFSMaxResponseJson(chargeType = "56000000"))(PsaFS.rdsMax).asOpt.value
        }
      }
    }
  }

  "Psa Max FS Seq" must {
    "format " when {
      "reading from json" in {
        val result = Json.fromJson[Seq[PsaFS]](psaFSMaxSeqResponse(chargeType = "57401091"))(PsaFS.rdsMaxSeq).asOpt.value
        result mustBe psaFSMaxSeqModel
      }

      "throw NoSuchElementException for invalid charge type" in {
        intercept[NoSuchElementException] {
          Json.fromJson[Seq[PsaFS]](psaFSMaxSeqResponse(chargeType = "56000000"))(PsaFS.rdsMaxSeq).asOpt.value
        }
      }
    }
  }
}
object PsaFSReadsSpec {

  private def psaFSResponseJson(chargeType: String): JsValue = Json.obj(
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
    "documentLineItemDetails"-> Json.arr(
      Json.obj(
        "clearingDate"-> "2020-06-30",
        "clearingReason"-> "C1",
        "clearedAmountItem"-> 0.00
      )
  ))

  private def psaFSMaxSeqResponse(chargeType: String): JsValue = Json.obj(
    "documentHeaderDetails" -> Json.arr(
      Json.obj(
        "chargeReference" -> "Not Applicable",
        "chargeType" -> s"$chargeType",
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
        "documentLineItemDetails"-> Json.arr(
          Json.obj(
            "clearingDate"-> "2020-06-30",
            "clearingReason"-> "C1",
            "clearedAmountItem"-> 0.00
          )
        )
      ),
      Json.obj(
        "chargeReference" -> "Not Applicable",
        "chargeType" -> s"$chargeType",
        "dueDate" -> "2020-02-15",
        "totalAmount" -> 80000.00,
        "outstandingAmount" -> 56049.08,
        "stoodOverAmount" -> 25089.08,
        "accruedInterestTotal" -> 123.00,
        "amountDue" -> 1029.05,
        "periodStartDate" -> "2020-04-01",
        "periodEndDate" -> "2020-06-30",
        "pstr" -> "24000040IN",
        "sourceChargeRefForInterest"-> "XY002610150181",
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

  private def psaFSModel = PsaFS(
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

  private def psaFSMaxModel = PsaFS(
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
    documentLineItemDetails = Seq(DocumentLineItemDetail(
      clearingReason= Some("C1"),
      clearingDate = Some(LocalDate.parse("2020-06-30")),
      clearedAmountItem = BigDecimal(0.00))
    )
  )

  private val psaFSMaxSeqModel: Seq[PsaFS] = Seq(
    PsaFS(
      chargeReference = "Not Applicable",
      chargeType = "Overseas transfer charge late payment penalty (6 months)",
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
      Seq(DocumentLineItemDetail(
        clearingReason = Some("C1"),
        clearingDate = Some(LocalDate.parse("2020-06-30")),
        clearedAmountItem = BigDecimal(0.00))
      )
    ),
    PsaFS(
      chargeReference = "Not Applicable",
      chargeType = "Overseas transfer charge late payment penalty (6 months)",
      dueDate = Some(LocalDate.parse("2020-02-15")),
      totalAmount = 80000.00,
      amountDue = 1029.05,
      outstandingAmount = 56049.08,
      stoodOverAmount = 25089.08,
      accruedInterestTotal = 123.00,
      periodStartDate = LocalDate.parse("2020-04-01"),
      periodEndDate = LocalDate.parse("2020-06-30"),
      pstr = "24000040IN",
      sourceChargeRefForInterest = Some("XY002610150181"),
      Seq(DocumentLineItemDetail(
        clearingReason = Some("C1"),
        clearingDate = Some(LocalDate.parse("2020-06-30")),
        clearedAmountItem = BigDecimal(0.00))
      )
    )
  )
}
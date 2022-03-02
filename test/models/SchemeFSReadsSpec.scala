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

import org.scalatest.OptionValues
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.{JsValue, Json}

import java.time.LocalDate
import java.util.NoSuchElementException

class SchemeFSReadsSpec extends AnyWordSpec with OptionValues with Matchers {

  import SchemeFSReadsSpec._

  "Scheme FS" must {
    "format " when {
      "reading from json" in {
        val result = Json.fromJson[SchemeFS](schemeFSResponseJson(chargeType = "56001000"))(SchemeFS.rds).asOpt.value
        result mustBe schemeFSModel
      }

      "throw NoSuchElementException for invalid charge type" in {
        intercept[NoSuchElementException] {
          Json.fromJson[SchemeFS](schemeFSResponseJson(chargeType = "56000000"))(SchemeFS.rds).asOpt.value
        }
      }
    }
  }
  "Scheme FSMax" must {
    "format " when {
      "reading from json" in {
        val result = Json.fromJson[SchemeFS](schemeFSResponseJsonMax(chargeType = "56001000"))(SchemeFS.rdsMax).asOpt.value
        result mustBe schemeFSModelMax
      }

      "throw NoSuchElementException for invalid charge type" in {
        intercept[NoSuchElementException] {
          Json.fromJson[SchemeFS](schemeFSResponseJsonMax(chargeType = "56000000"))(SchemeFS.rdsMax).asOpt.value
        }
      }
    }
  }

  "Scheme FSMax Seq" must {
    "format " when {
      "reading from json" in {
        val result = Json.fromJson[Seq[SchemeFS]](schemeFSMaxSeqResponseJson(chargeType = "56001000"))(SchemeFS.rdsMaxSeq).asOpt.value
        result mustBe schemeFSMaxSeqModel
      }

      "throw NoSuchElementException for invalid charge type" in {
        intercept[NoSuchElementException] {
          Json.fromJson[Seq[SchemeFS]](schemeFSMaxSeqResponseJson(chargeType = "56000000"))(SchemeFS.rdsMaxSeq).asOpt.value
        }
      }
    }
  }
}


object SchemeFSReadsSpec {
  private def schemeFSResponseJson(chargeType: String): JsValue = Json.obj(
    "chargeReference" -> "XY002610150184",
    "chargeType" -> s"$chargeType",
    "dueDate" -> "2020-02-15",
    "totalAmount" -> 80000.00,
    "amountDue" -> 1029.05,
    "outstandingAmount" -> 56049.08,
    "accruedInterestTotal" -> 100.05,
    "stoodOverAmount" -> 25089.08,
    "periodStartDate" -> "2020-04-01",
    "periodEndDate" -> "2020-06-30"
  )
  private def schemeFSResponseJsonMax(chargeType: String): JsValue = Json.obj(
    "chargeReference" -> "XY002610150184",
    "chargeType" -> s"$chargeType",
    "dueDate" -> "2020-02-15",
    "totalAmount" -> 80000.00,
    "amountDue" -> 1029.05,
    "outstandingAmount" -> 56049.08,
    "accruedInterestTotal" -> 100.05,
    "stoodOverAmount" -> 25089.08,
    "periodStartDate" -> "2020-04-01",
    "periodEndDate" -> "2020-06-30",
    "formbundleNumber"-> "123456789193",
    "sourceChargeRefForInterest"-> "XY002610150181",
    "documentLineItemDetails"-> Json.arr(
      Json.obj(
        "clearingDate"-> "2020-06-30",
        "clearingReason"-> "C1",
        "clearedAmountItem"-> 0.00
      )
    )
  )

  private def schemeFSMaxSeqResponseJson(chargeType: String): JsValue = Json.obj(
    "documentHeaderDetails" -> Json.arr(
      Json.obj(
        "chargeReference" -> "XY002610150184",
        "chargeType" -> s"$chargeType",
        "dueDate" -> "2020-02-15",
        "totalAmount" -> 80000.00,
        "amountDue" -> 1029.05,
        "outstandingAmount" -> 56049.08,
        "accruedInterestTotal" -> 100.05,
        "stoodOverAmount" -> 25089.08,
        "periodStartDate" -> "2020-04-01",
        "periodEndDate" -> "2020-06-30",
        "formbundleNumber"-> "123456789193",
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
        "chargeReference" -> "XY002610150184",
        "chargeType" -> s"$chargeType",
        "dueDate" -> "2020-02-15",
        "totalAmount" -> 800.00,
        "amountDue" -> 1029.05,
        "outstandingAmount" -> 1500.00,
        "accruedInterestTotal" -> 100.05,
        "stoodOverAmount" -> 508.18,
        "periodStartDate" -> "2020-04-01",
        "periodEndDate" -> "2020-06-30",
        "formbundleNumber"-> "123456789193",
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

  private def schemeFSModel = SchemeFS(
    chargeReference = "XY002610150184",
    chargeType = "Accounting for Tax return",
    dueDate = Some(LocalDate.parse("2020-02-15")),
    totalAmount = 80000.00,
    amountDue = 1029.05,
    outstandingAmount = 56049.08,
    accruedInterestTotal = 100.05,
    stoodOverAmount = 25089.08,
    periodStartDate = Some(LocalDate.parse("2020-04-01")),
    periodEndDate = Some(LocalDate.parse("2020-06-30"))
  )
  private def schemeFSModelMax = SchemeFS(
    chargeReference = "XY002610150184",
    chargeType = "Accounting for Tax return",
    dueDate = Some(LocalDate.parse("2020-02-15")),
    totalAmount = 80000.00,
    amountDue = 1029.05,
    outstandingAmount = 56049.08,
    accruedInterestTotal = 100.05,
    stoodOverAmount = 25089.08,
    periodStartDate = Some(LocalDate.parse("2020-04-01")),
    periodEndDate = Some(LocalDate.parse("2020-06-30")),
    formBundleNumber=Some("123456789193"),
    sourceChargeRefForInterest = Some("XY002610150181"),
    Seq(DocumentLineItemDetail(
      clearingReason= Some("C1"),
      clearingDate = Some(LocalDate.parse("2020-06-30")),
      clearedAmountItem = BigDecimal(0.00))
    )
  )

  private def schemeFSMaxSeqModel = Seq(
    SchemeFS(
      chargeReference = "XY002610150184",
      chargeType = "Accounting for Tax return",
      dueDate = Some(LocalDate.parse("2020-02-15")),
      totalAmount = 80000.00,
      amountDue = 1029.05,
      outstandingAmount = 56049.08,
      accruedInterestTotal = 100.05,
      stoodOverAmount = 25089.08,
      periodStartDate = Some(LocalDate.parse("2020-04-01")),
      periodEndDate = Some(LocalDate.parse("2020-06-30")),
      formBundleNumber=Some("123456789193"),
      sourceChargeRefForInterest = Some("XY002610150181"),
      Seq(DocumentLineItemDetail(
        clearingReason= Some("C1"),
        clearingDate = Some(LocalDate.parse("2020-06-30")),
        clearedAmountItem = BigDecimal(0.00))
      )
    ),
    SchemeFS(
      chargeReference = "XY002610150184",
      chargeType = "Accounting for Tax return",
      dueDate = Some(LocalDate.parse("2020-02-15")),
      totalAmount = 800.00,
      amountDue = 1029.05,
      outstandingAmount = 1500.0,
      accruedInterestTotal = 100.05,
      stoodOverAmount = 508.18,
      periodStartDate = Some(LocalDate.parse("2020-04-01")),
      periodEndDate = Some(LocalDate.parse("2020-06-30")),
      formBundleNumber=Some("123456789193"),
      sourceChargeRefForInterest = Some("XY002610150181"),
      Seq(DocumentLineItemDetail(
        clearingReason= Some("C1"),
        clearingDate = Some(LocalDate.parse("2020-06-30")),
        clearedAmountItem = BigDecimal(0.00))
      )
    )
  )
}



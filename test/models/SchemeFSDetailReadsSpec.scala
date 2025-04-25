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

import org.scalatest.OptionValues
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.{JsValue, Json}

import java.time.LocalDate

class SchemeFSDetailReadsSpec extends AnyWordSpec with OptionValues with Matchers {

  import SchemeFSDetailReadsSpec._

  "rdsSchemeFSDetailMax" must {
    "format " when {
      "reading from json" in {
        val result = Json.fromJson[SchemeFSDetail](schemeFSResponseJsonMax(chargeType = "56001000"))(SchemeFS.rdsSchemeFSDetailMax).asOpt.value
        result.mustBe(schemeFSModelMax)
      }

      "throw NoSuchElementException for invalid charge type" in {
        intercept[NoSuchElementException] {
          Json.fromJson[SchemeFSDetail](schemeFSResponseJsonMax(chargeType = "56000000"))(SchemeFS.rdsSchemeFSDetailMax).asOpt.value
        }
      }
    }
  }

  "rdsSchemeFSMax" must {
    "format " when {
      "reading from json and inhibitRefundSignal is true" in {
        val result = Json.fromJson[SchemeFS](schemeFSMaxSeqResponseJson(chargeType = "56001000", inhibitRefundSignal = true))(SchemeFS.rdsSchemeFSMax)
          .asOpt.value
        result.mustBe(schemeFSMaxWrapperTrue)
      }

      "reading from json and inhibitRefundSignal is false" in {
        val result = Json.fromJson[SchemeFS](schemeFSMaxSeqResponseJson(chargeType = "56001000", inhibitRefundSignal = false))(SchemeFS.rdsSchemeFSMax)
          .asOpt.value
        result.mustBe(schemeFSMaxWrapperFalse)
      }

      "throw NoSuchElementException for invalid charge type" in {
        intercept[NoSuchElementException] {
          Json.fromJson[SchemeFS](schemeFSMaxSeqResponseJson(chargeType = "56000000", inhibitRefundSignal = false))(SchemeFS.rdsSchemeFSMax).asOpt.value
        }
      }
    }
  }
}


object SchemeFSDetailReadsSpec {
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
    "formbundleNumber" -> "123456789193",
    "aftVersion" -> 0,
    "sourceChargeRefForInterest" -> "XY002610150181",
    "documentLineItemDetails" -> Json.arr(
      Json.obj(
        "clearingDate" -> "2020-06-30",
        "paymDateOrCredDueDate" -> "2020-04-24",
        "clearingReason" -> "C1",
        "clearedAmountItem" -> 0.00
      )
    )
  )

  private def schemeFSMaxSeqResponseJson(chargeType: String, inhibitRefundSignal: Boolean): JsValue = Json.obj(
    "accountHeaderDetails" -> Json.obj("inhibitRefundSignal" -> inhibitRefundSignal),
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
        "formbundleNumber" -> "123456789193",
        "aftVersion" -> 0,
        "sourceChargeRefForInterest" -> "XY002610150181",
        "documentLineItemDetails" -> Json.arr(
          Json.obj(
            "clearingDate" -> "2020-06-30",
            "paymDateOrCredDueDate" -> "2020-04-24",
            "clearingReason" -> "C1",
            "clearedAmountItem" -> 0.00
          )
        )
      ),
      Json.obj(
        "chargeReference" -> "XY002610150185",
        "chargeType" -> s"$chargeType",
        "dueDate" -> "2020-02-15",
        "totalAmount" -> 800.00,
        "amountDue" -> 1029.05,
        "outstandingAmount" -> 1500.00,
        "accruedInterestTotal" -> 100.05,
        "stoodOverAmount" -> 508.18,
        "periodStartDate" -> "2020-04-01",
        "periodEndDate" -> "2020-06-30",
        "formbundleNumber" -> "123456789194",
        "aftVersion" -> 0,
        "sourceChargeRefForInterest" -> "XY002610150184",
        "documentLineItemDetails" -> Json.arr(
          Json.obj(
            "clearingDate" -> "2020-06-30",
            "paymDateOrCredDueDate" -> "2020-04-24",
            "clearingReason" -> "C1",
            "clearedAmountItem" -> 0.00
          )
        )
      )
    )
  )

  private def schemeFSModelMax = SchemeFSDetail(
    index = 0,
    chargeReference = "XY002610150184",
    chargeType = "Accounting for Tax Return",
    dueDate = Some(LocalDate.parse("2020-02-15")),
    totalAmount = 80000.00,
    amountDue = 1029.05,
    outstandingAmount = 56049.08,
    accruedInterestTotal = 100.05,
    stoodOverAmount = 25089.08,
    periodStartDate = Some(LocalDate.parse("2020-04-01")),
    periodEndDate = Some(LocalDate.parse("2020-06-30")),
    formBundleNumber = Some("123456789193"),
    version = None,
    receiptDate = None,
    aftVersion = Some(0),
    sourceChargeRefForInterest = Some("XY002610150181"),
    sourceChargeInfo = None,
    Seq(DocumentLineItemDetail(
      clearingReason = Some("C1"),
      clearingDate = Some(LocalDate.parse("2020-06-30")),
      paymDateOrCredDueDate = Some(LocalDate.parse("2020-04-24")),
      clearedAmountItem = BigDecimal(0.00))
    )
  )


  //scalastyle:off method.length
  private def schemeFSMaxSeqModel = Seq(
    SchemeFSDetail(
      index = 1,
      chargeReference = "XY002610150184",
      chargeType = "Accounting for Tax Return",
      dueDate = Some(LocalDate.parse("2020-02-15")),
      totalAmount = 80000.00,
      amountDue = 1029.05,
      outstandingAmount = 56049.08,
      accruedInterestTotal = 100.05,
      stoodOverAmount = 25089.08,
      periodStartDate = Some(LocalDate.parse("2020-04-01")),
      periodEndDate = Some(LocalDate.parse("2020-06-30")),
      formBundleNumber = Some("123456789193"),
      version = None,
      receiptDate = None,
      aftVersion = Some(0),
      sourceChargeRefForInterest = Some("XY002610150181"),
      sourceChargeInfo = None,
      Seq(DocumentLineItemDetail(
        clearingReason = Some("C1"),
        clearingDate = Some(LocalDate.parse("2020-06-30")),
        paymDateOrCredDueDate = Some(LocalDate.parse("2020-04-24")),
        clearedAmountItem = BigDecimal(0.00))
      )
    ),
    SchemeFSDetail(
      index = 2,
      chargeReference = "XY002610150185",
      chargeType = "Accounting for Tax Return",
      dueDate = Some(LocalDate.parse("2020-02-15")),
      totalAmount = 800.00,
      amountDue = 1029.05,
      outstandingAmount = 1500.0,
      accruedInterestTotal = 100.05,
      stoodOverAmount = 508.18,
      periodStartDate = Some(LocalDate.parse("2020-04-01")),
      periodEndDate = Some(LocalDate.parse("2020-06-30")),
      formBundleNumber = Some("123456789194"),
      version = None,
      receiptDate = None,
      aftVersion = Some(0),
      sourceChargeRefForInterest = Some("XY002610150184"),
      sourceChargeInfo = Some(
        SchemeSourceChargeInfo(
          index = 1,
          periodStartDate = Some(LocalDate.parse("2020-04-01")),
          periodEndDate = Some(LocalDate.parse("2020-06-30"))
        )
      ),
      Seq(DocumentLineItemDetail(
        clearingReason = Some("C1"),
        clearingDate = Some(LocalDate.parse("2020-06-30")),
        paymDateOrCredDueDate = Some(LocalDate.parse("2020-04-24")),
        clearedAmountItem = BigDecimal(0.00))
      )
    )
  )

  private def schemeFSMaxWrapperTrue = SchemeFS(
    inhibitRefundSignal = true,
    seqSchemeFSDetail = schemeFSMaxSeqModel
  )

  private def schemeFSMaxWrapperFalse = SchemeFS(
    inhibitRefundSignal = false,
    seqSchemeFSDetail = schemeFSMaxSeqModel
  )
}



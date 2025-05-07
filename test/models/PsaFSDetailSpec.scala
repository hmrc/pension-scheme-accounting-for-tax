/*
 * Copyright 2025 HM Revenue & Customs
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
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json._

import java.time.LocalDate

class PsaFSDetailSpec extends AnyWordSpec with Matchers {

  "PsaFSDetail format" should {

    "serialize and deserialize correctly with all fields" in {
      val detail = PsaFSDetail(
        index = 1,
        chargeReference = "XYZ123",
        chargeType = "Some charge",
        dueDate = Some(LocalDate.of(2025, 1, 1)),
        totalAmount = BigDecimal(1000.50),
        amountDue = BigDecimal(500.25),
        outstandingAmount = BigDecimal(300.00),
        stoodOverAmount = BigDecimal(100.00),
        accruedInterestTotal = BigDecimal(50.00),
        periodStartDate = LocalDate.of(2024, 4, 1),
        periodEndDate = LocalDate.of(2024, 6, 30),
        pstr = "123XYZ",
        sourceChargeRefForInterest = Some("REF456"),
        psaSourceChargeInfo = Some(PsaSourceChargeInfo(
          index = 2,
          chargeType = "Another charge",
          periodStartDate = LocalDate.of(2023, 1, 1),
          periodEndDate = LocalDate.of(2023, 3, 31)
        )),
        documentLineItemDetails = Seq(
          DocumentLineItemDetail(
            clearedAmountItem = BigDecimal(200),
            clearingDate = Some(LocalDate.of(2024, 2, 1)),
            paymDateOrCredDueDate = Some(LocalDate.of(2024, 3, 1)),
            clearingReason = Some("Adjustment")
          )
        )
      )

      val json = Json.toJson(detail)
      val parsed = json.validate[PsaFSDetail]

      parsed.isSuccess mustBe true
      parsed.get mustBe detail
    }

    "handle optional fields when missing" in {
      val json = Json.parse(
        """
          {
            "index": 0,
            "chargeReference": "XYZ123",
            "chargeType": "Some charge",
            "dueDate": null,
            "totalAmount": 1000.50,
            "amountDue": 500.25,
            "outstandingAmount": 300.00,
            "stoodOverAmount": 100.00,
            "accruedInterestTotal": 50.00,
            "periodStartDate": "2024-04-01",
            "periodEndDate": "2024-06-30",
            "pstr": "123XYZ",
            "documentLineItemDetails": []
          }
        """
      )

      val result = json.validate[PsaFSDetail]
      result.isSuccess mustBe true
      result.get.sourceChargeRefForInterest mustBe None
      result.get.psaSourceChargeInfo mustBe None
    }
  }

  "PsaSourceChargeInfo format" should {

    "serialize and deserialize correctly" in {
      val info = PsaSourceChargeInfo(
        index = 5,
        chargeType = "InfoCharge",
        periodStartDate = LocalDate.of(2024, 1, 1),
        periodEndDate = LocalDate.of(2024, 12, 31)
      )

      val json = Json.toJson(info)
      val parsed = json.validate[PsaSourceChargeInfo]

      parsed.isSuccess mustBe true
      parsed.get mustBe info
    }
  }
}

/*
 * Copyright 2020 HM Revenue & Customs
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

import org.scalatest.{MustMatchers, OptionValues, WordSpec}
import play.api.libs.json.{JsValue, Json}

class SchemeFSReadsSpec extends WordSpec with OptionValues with MustMatchers {

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
}

object SchemeFSReadsSpec {
  private def schemeFSResponseJson(chargeType: String): JsValue = Json.obj(
    "chargeReference" -> "XY002610150184",
    "chargeType" -> s"$chargeType",
    "dueDate" -> "2020-02-15",
    "amountDue" -> 1029.05,
    "outstandingAmount" -> 56049.08,
    "accruedInterestTotal" -> 100.05,
    "stoodOverAmount" -> 25089.08,
    "periodStartDate" -> "2020-04-01",
    "periodEndDate" -> "2020-06-30"
  )

  private def schemeFSModel = SchemeFS(
    chargeReference = "XY002610150184",
    chargeType = "PSS AFT Return",
    dueDate = Some(LocalDate.parse("2020-02-15")),
    amountDue = 1029.05,
    outstandingAmount = 56049.08,
    accruedInterestTotal = 100.05,
    stoodOverAmount = 25089.08,
    periodStartDate = Some(LocalDate.parse("2020-04-01")),
    periodEndDate = Some(LocalDate.parse("2020-06-30"))
  )
}



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

class PsaFSReadsSpec extends WordSpec with OptionValues with MustMatchers {

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
}
object PsaFSReadsSpec {

  private def psaFSResponseJson(chargeType: String): JsValue = Json.obj(
    "chargeReference" -> "XY002610150184",
    "chargeType" -> s"$chargeType",
    "dueDate" -> "2020-02-15",
    "outstandingAmount" -> 56049.08,
    "stoodOverAmount" -> 25089.08,
    "amountDue" -> 1029.05,
    "periodStartDate" -> "2020-04-01",
    "periodEndDate" -> "2020-06-30",
    "pstr" -> "24000040IN"
  )

  private def psaFSModel = PsaFS(
    chargeReference = "XY002610150184",
    chargeType = "Overseas transfer charge late payment penalty (6 months)",
    dueDate = Some(LocalDate.parse("2020-02-15")),
    outstandingAmount = 56049.08,
    stoodOverAmount = 25089.08,
    amountDue = 1029.05,
    periodStartDate = LocalDate.parse("2020-04-01"),
    periodEndDate = LocalDate.parse("2020-06-30"),
    pstr = "24000040IN"
  )
}





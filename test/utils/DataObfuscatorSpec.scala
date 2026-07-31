/*
 * Copyright 2026 HM Revenue & Customs
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

package utils

import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.{JsString, Json}

class DataObfuscatorSpec extends AnyWordSpec with Matchers {

  "DataObfuscator must obfuscate nested member details and leave other values unchanged" in {

    val input = Json.parse(
      """
        |{
        |  "schemeStatus": "Open",
        |  "loggedInPersonEmail": "nigel@test.com",
        |  "loggedInPersonName": "Nigel Robert Smith",
        |  "minimalFlags": {
        |    "deceasedFlag": false,
        |    "rlsFlag": false
        |  },
        |  "quarter": {
        |    "startDate": "2025-07-01",
        |    "endDate": "2025-09-30"
        |  },
        |  "aftStatus": "Submitted",
        |  "schemeName": "Open Scheme Variations Test - Scheme D1.3",
        |  "pstr": "24000017IN",
        |  "inputSelection": "manualInput",
        |  "chargeEDetails": {
        |    "totalChargeAmount": 3000,
        |    "addMembers": false,
        |    "members": [
        |      {
        |        "mccloudRemedy": {
        |          "isPublicServicePensionsRemedy": true,
        |          "isChargeInAdditionReported": true,
        |          "wasAnotherPensionScheme": true,
        |          "schemes": [
        |            {
        |              "pstr": "20123456RQ",
        |              "taxYearReportedAndPaidPage": "2021",
        |              "taxQuarterReportedAndPaid": {
        |                "startDate": "2021-10-01",
        |                "endDate": "2021-12-31"
        |              },
        |              "chargeAmountReported": 2000
        |            },
        |            {
        |              "pstr": "20123456RR",
        |              "taxYearReportedAndPaidPage": "2021",
        |              "taxQuarterReportedAndPaid": {
        |                "startDate": "2021-10-01",
        |                "endDate": "2021-12-31"
        |              },
        |              "chargeAmountReported": 1000
        |            }
        |          ]
        |        },
        |        "memberFormCompleted": true,
        |        "memberDetails": {
        |          "firstName": "S",
        |          "lastName": "J",
        |          "nino": "SJ123456A"
        |        },
        |        "annualAllowanceYear": "2021",
        |        "chargeDetails": {
        |          "chargeAmount": 3000,
        |          "dateNoticeReceived": "2022-05-10",
        |          "isPaymentMandatory": true
        |        }
        |      }
        |    ]
        |  },
        |  "aFTSummary": false,
        |  "confirmSubmitAFTReturn": true,
        |  "declaration": {
        |    "submittedBy": "PSA",
        |    "submittedID": "A2100005",
        |    "hasAgreed": true
        |  }
        |}
        |""".stripMargin
    )

    val result = DataObfuscator.obfuscate(input)

    (result \ "chargeEDetails" \ "members" \ 0 \ "memberDetails" \ "firstName")
      .get mustBe JsString("[REDACTED]")

    (result \ "chargeEDetails" \ "members" \ 0 \ "memberDetails" \ "lastName")
      .get mustBe JsString("[REDACTED]")

    (result \ "chargeEDetails" \ "members" \ 0 \ "memberDetails" \ "nino")
      .get mustBe JsString("[REDACTED]")

    (result \ "schemeStatus").as[String] mustBe "Open"
    (result \ "pstr").as[String] mustBe "24000017IN"

    (result \ "chargeEDetails" \ "members" \ 0 \ "chargeDetails" \ "chargeAmount")
      .as[Int] mustBe 3000

    (result \ "declaration" \ "submittedID")
      .as[String] mustBe "A2100005"

  }
}

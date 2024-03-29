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

package audit

import models.enumeration.JourneyType
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import play.api.libs.json.{JsObject, Json}

class FileAftReturnSpec extends AnyFlatSpec with Matchers {

  "FileAftReturn.details" should "output the correct map of data" in {

    val pstr = "test-pstr"
    val quarterStartDate = "2020-01-01"
    val journeyType = JourneyType.AFT_SUBMIT_RETURN.toString
    val aftStatus = "Compiled"
    val status = 1
    val request = Json.obj(
      "name" -> "request",
      "aftDetails" -> Json.obj(
        "aftStatus" -> aftStatus,
        "quarterStartDate" -> quarterStartDate
      ))
    val response = Json.obj("name" -> "response")

    val event = FileAftReturn(
      pstr = pstr,
      journeyType = journeyType,
      status = status,
      request = request,
      response = Some(response)
    )

    val expected: JsObject = Json.obj(
      "pstr" -> pstr,
      "quarterStartDate" -> quarterStartDate,
      "aftStatus" -> journeyType,
      "status" -> status.toString,
      "request" -> request,
      "response" -> response
    )

    event.details shouldBe expected

  }

  "FileAftReturnSchemaValidator.details" should "output the correct map of data" in {

    val psaId = "psa"
    val pstr = "test-pstr"
    val noOfFailures = 1
    val chargeType = "List(chargeTypeA, chargeTypeB, chargeTypeC, chargeTypeD)"
    val request = Json.obj(
      "name" -> "request",
      "aftDeclarationDetails" -> Json.obj(
        "submittedBy" -> "PSA",
        "submittedID" -> psaId
      ))
    val response = "response"

    val event = FileAftReturnSchemaValidator(
      psaOrPspId = psaId,
      pstr = pstr,
      chargeType = chargeType,
      numberOfFailures = noOfFailures,
      request = request,
      failureResponse = response
    )

    val expected: JsObject = Json.obj(
      "psaOrPspId" -> psaId,
      "pstr" -> pstr,
      "chargeType" -> chargeType,
      "request" -> request,
      "failureResponse" -> response,
      "numberOfFailures" -> noOfFailures.toString
    )

    event.details shouldBe expected

  }

}

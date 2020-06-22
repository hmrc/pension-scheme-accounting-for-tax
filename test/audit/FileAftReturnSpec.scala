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

package audit

import models.enumeration.JourneyType
import org.scalatest.{FlatSpec, Matchers}
import play.api.libs.json.Json

class FileAftReturnSpec extends FlatSpec with Matchers {

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

    val expected: Map[String, String] = Map(
      "pstr" -> pstr,
      "quarterStartDate" -> quarterStartDate,
      "aftStatus" -> journeyType,
      "status" -> status.toString,
      "request" -> Json.prettyPrint(request),
      "response" -> Json.prettyPrint(response)
    )

    event.details shouldBe expected

  }

}

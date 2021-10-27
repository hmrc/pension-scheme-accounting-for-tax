/*
 * Copyright 2021 HM Revenue & Customs
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

import org.scalatest.matchers.should.Matchers
import org.scalatest.flatspec.AnyFlatSpec
import play.api.libs.json.{JsObject, Json}

class FinancialInfoAuditServiceSpec extends AnyFlatSpec with Matchers {

  val status: Int = 200
  val response: JsObject = Json.obj("name" -> "response")

  "GetPsaFS.details" should "output the correct map of data" in {

    val psaId: String = "test-psa-id"

    val event = GetPsaFS(psaId, status, Some(response))

    val expected: JsObject = Json.obj(
      "psaId" -> psaId,
      "status" -> status.toString,
      "response" -> response
    )

    event.details shouldBe expected

  }

  "SchemePsaFS.details" should "output the correct map of data" in {

    val pstr: String = "test-pstr"

    val event = GetSchemeFS(pstr, status, Some(response))

    val expected: JsObject = Json.obj(
      "pstr" -> pstr,
      "status" -> status.toString,
      "response" -> response
    )

    event.details shouldBe expected

  }

}

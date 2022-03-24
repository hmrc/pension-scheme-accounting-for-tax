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

package utils

import org.mockito.MockitoSugar
import org.scalatest.BeforeAndAfter
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.Json

import java.net.URL

class InvalidPayloadHandlerSpec extends AnyWordSpec with MockitoSugar with Matchers with BeforeAndAfter {

  private val app = new GuiceApplicationBuilder()
    .overrides(
    )
    .build()

  private lazy val invalidPayloadHandler: InvalidPayloadHandler = app.injector.instanceOf[InvalidPayloadHandler]

  "validateJson" must {
    "work for valid file" in {
      val schemaUrl: URL = getClass.getResource( "/resources/schemas/api-1538-file-aft-return-1.5.0.json")
      val schemaPath = schemaUrl.getPath
      val json = Json.parse(
        """
          |{
          |   "aftDetails":{
          |      "aftStatus":"Compiled",
          |      "quarterEndDate":"2020-06-30",
          |      "quarterStartDate":"2020-04-01"
          |   },
          |   "chargeDetails":{
          |      "chargeTypeFDetails":{
          |         "totalAmount":111,
          |         "dateRegiWithdrawn":"2020-04-01"
          |      }
          |   }
          |}
          |""".stripMargin)
      val result = invalidPayloadHandler.validateJson(schemaPath, json)
      result mustBe None
    }


  }

}
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
import play.api.libs.json.{JsDefined, JsError, JsObject, JsSuccess, JsValue, Json}

import java.net.URL



class JSONPayloadSchemaValidatorSpec extends AnyWordSpec with MockitoSugar with Matchers with BeforeAndAfter {

  private val app = new GuiceApplicationBuilder()
    .overrides(
    )
    .build()

  private lazy val jsonPayloadSchemaValidator: JSONPayloadSchemaValidator = app.injector.instanceOf[JSONPayloadSchemaValidator]
  "validateJson" must {
    "Validate payload" in {
      val schemaUrl: URL = getClass.getResource(ExtractErrorDetails.schemaPath)
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
      val result = jsonPayloadSchemaValidator.validateJsonPayload(schemaPath, json)
      result.isSuccess mustBe true
    }

    "Validate invalid payload with single error" in {
      val schemaUrl: URL = getClass.getResource(ExtractErrorDetails.schemaPath)
      val schemaPath = schemaUrl.getPath
      val json = Json.parse(
        """
          |{
          |   "aftDetails":{
          |      "aftStatus":"Compiled",
          |      "quarterEndDate":"2020-06-30",
          |      "quarterStartDate":"2020-10-30"
          |   },
          |   "chargeDetails":{
          |      "chargeTypeFDetails":{
          |         "totalAmount":"XXXXXXXXXXXX",
          |         "dateRegiWithdrawn":"2020-04-01"
          |      }
          |   }
          |}
          |""".stripMargin)
      val result = jsonPayloadSchemaValidator.validateJsonPayload(schemaPath, json)


      result match {
        case JsError(error) =>
          val expectedMessage = "ExtractErrorDetails(Some(#/oneOf/0/definitions/totalAmountType),[\"Wrong type. Expected number, was string.\"])"
          ExtractErrorDetails.getErrors(error) mustBe expectedMessage
        case JsSuccess(s, x) =>
      }
    }

    "Validate invalid payload with multiple errors" in {
      val schemaUrl: URL = getClass.getResource(ExtractErrorDetails.schemaPath)
      val schemaPath = schemaUrl.getPath
      val json = Json.parse(
        """
          |{
          |   "aftDetails":{
          |      "aftStatus":"Compiled",
          |      "quarterEndDate":"2020-06-30",
          |      "quarterStartDate":"XXXXXXXXXXXX"
          |   },
          |   "chargeDetails":{
          |      "chargeTypeFDetails":{
          |         "totalAmount":"XXXXXXXXXXXX",
          |         "dateRegiWithdrawn":"2020-04-01"
          |      }
          |   }
          |}
          |""".stripMargin)
      val result = jsonPayloadSchemaValidator.validateJsonPayload(schemaPath, json)


      result match {
        case JsError(error) =>
          val expectedMessage = "ExtractErrorDetails(Some(#/oneOf/0/definitions/totalAmountType)," +
            "[\"Wrong type. Expected number, was string.\"]) " +
            "ExtractErrorDetails(Some(#/oneOf/0/definitions/dateType)," +
            "[\"'XXXXXXXXXXXX' does not match pattern " +
            "'^(((19|20)([2468][048]|[13579][26]|0[48])|2000)[-]02[-]29|((19|20)[0-9]{2}[-]" +
            "(0[469]|11)[-](0[1-9]|1[0-9]|2[0-9]|30)|(19|20)[0-9]{2}[-](0[13578]|1[02])[-](0[1-9]|[12][0-9]|3[01])|" +
            "(19|20)[0-9]{2}[-]02[-](0[1-9]|1[0-9]|2[0-8])))$'.\"])"
          ExtractErrorDetails.getErrors(error) mustBe expectedMessage
        case JsSuccess(s, x) =>
      }
    }
  }
}
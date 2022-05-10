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
import play.api.libs.json.{JsError, JsSuccess, Json}

import java.net.URL



class JSONPayloadSchemaValidatorSpec extends AnyWordSpec with MockitoSugar with Matchers with BeforeAndAfter {

  private val app = new GuiceApplicationBuilder()
    .overrides(
    )
    .build()

  private lazy val jsonPayloadSchemaValidator: JSONPayloadSchemaValidator = app.injector.instanceOf[JSONPayloadSchemaValidator]
  "validateJson" must {
    "Validate payload" in {
      val schemaUrl: URL = getClass.getResource(ErrorDetailsExtractor.schemaPath)
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

    "Validate full payload" in {
      val schemaUrl: URL = getClass.getResource(ErrorDetailsExtractor.schemaPath)
      val schemaPath = schemaUrl.getPath
      val json = Json.parse(
        """
          |{
          |  "aftDetails": {
          |    "aftStatus": "Submitted",
          |    "quarterEndDate": "2020-12-31",
          |    "quarterStartDate": "2020-10-01"
          |  },
          |  "aftDeclarationDetails": {
          |    "submittedBy": "PSA",
          |    "submittedID": "A2100005",
          |    "psaDeclarationDetails": {
          |      "psaDeclaration1": true,
          |      "psaDeclaration2": true
          |    }
          |  },
          |  "chargeDetails": {
          |    "chargeTypeCDetails": {
          |      "totalAmount": 90.02,
          |      "amendedVersion": 1,
          |      "memberDetails": [
          |        {
          |          "dateOfPayment": "2020-10-18",
          |          "memberTypeDetails": {
          |            "individualDetails": {
          |              "firstName": "Ray",
          |              "lastName": "Golding",
          |              "nino": "AA000020A"
          |            },
          |            "memberType": "Individual"
          |          },
          |          "totalAmountOfTaxDue": 2300.02,
          |          "memberStatus": "New",
          |          "memberAFTVersion": 1,
          |          "correspondenceAddressDetails": {
          |            "countryCode": "GB",
          |            "postalCode": "TF3 4NT",
          |            "addressLine1": "Plaza 2 ",
          |            "addressLine2": "Ironmasters Way",
          |            "addressLine3": "Telford",
          |            "addressLine4": "Shropshire",
          |            "nonUKAddress": "False"
          |          }
          |        },
          |        {
          |          "dateOfPayment": "2020-10-28",
          |          "memberTypeDetails": {
          |            "individualDetails": {
          |              "firstName": "Craig",
          |              "lastName": "McMillan",
          |              "nino": "AA000620A"
          |            },
          |            "memberType": "Individual"
          |          },
          |          "totalAmountOfTaxDue": 12340.02,
          |          "memberStatus": "New",
          |          "memberAFTVersion": 1,
          |          "correspondenceAddressDetails": {
          |            "countryCode": "GB",
          |            "postalCode": "B1 1LA",
          |            "addressLine1": "45 UpperMarshall Street",
          |            "addressLine2": "Post Box APTS",
          |            "addressLine3": "Birmingham",
          |            "addressLine4": "Warwickshire",
          |            "nonUKAddress": "False"
          |          }
          |        }
          |      ]
          |    },
          |    "chargeTypeDDetails": {
          |      "totalAmount": 2345.02,
          |      "amendedVersion": 1,
          |      "memberDetails": [
          |        {
          |          "totalAmtOfTaxDueAtHigherRate": 9.02,
          |          "individualsDetails": {
          |            "firstName": "Joy",
          |            "lastName": "Kenneth",
          |            "nino": "AA089000A"
          |          },
          |          "memberStatus": "New",
          |          "totalAmtOfTaxDueAtLowerRate": 1.02,
          |          "memberAFTVersion": 1,
          |          "dateOfBeneCrysEvent": "2020-10-18"
          |        },
          |        {
          |          "totalAmtOfTaxDueAtHigherRate": 10.02,
          |          "individualsDetails": {
          |            "firstName": "Brian",
          |            "lastName": "Lara",
          |            "nino": "AA100000A"
          |          },
          |          "memberStatus": "New",
          |          "totalAmtOfTaxDueAtLowerRate": 3.02,
          |          "memberAFTVersion": 1,
          |          "dateOfBeneCrysEvent": "2020-10-28"
          |        }
          |      ]
          |    },
          |    "chargeTypeADetails": {
          |      "totalAmtOfTaxDueAtHigherRate": 2500.02,
          |      "totalAmount": 4500.04,
          |      "numberOfMembers": 2,
          |      "amendedVersion": 1,
          |      "totalAmtOfTaxDueAtLowerRate": 2000.02
          |    },
          |    "chargeTypeBDetails": {
          |      "totalAmount": 100.02,
          |      "numberOfMembers": 2,
          |      "amendedVersion": 1
          |    }
          |  }
          |}
          |""".stripMargin)
      val result = jsonPayloadSchemaValidator.validateJsonPayload(schemaPath, json)
      result.isSuccess mustBe true
    }


    "Validate invalid payload with single error" in {
      val schemaUrl: URL = getClass.getResource(ErrorDetailsExtractor.schemaPath)
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
          val expectedMessage = "ErrorDetailsExtractor(Some(#/oneOf/0/definitions/totalAmountType),[\"Wrong type. Expected number, was string.\"])"
          ErrorDetailsExtractor.getErrors(error) mustBe expectedMessage
        case JsSuccess(s, x) =>
      }
    }

    "Validate invalid payload with multiple errors" in {
      val schemaUrl: URL = getClass.getResource(ErrorDetailsExtractor.schemaPath)
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
          val expectedMessage = "ErrorDetailsExtractor(Some(#/oneOf/0/definitions/totalAmountType)," +
            "[\"Wrong type. Expected number, was string.\"]) " +
            "ErrorDetailsExtractor(Some(#/oneOf/0/definitions/dateType)," +
            "[\"'XXXXXXXXXXXX' does not match pattern " +
            "'^(((19|20)([2468][048]|[13579][26]|0[48])|2000)[-]02[-]29|((19|20)[0-9]{2}[-]" +
            "(0[469]|11)[-](0[1-9]|1[0-9]|2[0-9]|30)|(19|20)[0-9]{2}[-](0[13578]|1[02])[-](0[1-9]|[12][0-9]|3[01])|" +
            "(19|20)[0-9]{2}[-]02[-](0[1-9]|1[0-9]|2[0-8])))$'.\"])"
          ErrorDetailsExtractor.getErrors(error) mustBe expectedMessage
        case JsSuccess(s, x) =>
      }
    }
  }
}
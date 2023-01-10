/*
 * Copyright 2023 HM Revenue & Customs
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

import org.scalatest.BeforeAndAfter
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.mockito.MockitoSugar
import play.api.libs.json.Json

class JSONPayloadSchemaValidatorSpec extends AnyWordSpec with MockitoSugar with Matchers with BeforeAndAfter {
  val schemaPath = "/resources/schemas/api-1538-file-aft-return-request-schema-0.1.0.json"

  private val jsonPayloadSchemaValidator = new JSONPayloadSchemaValidator
  "validateJson" must {
    "Validate payload" in {
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
      result.toOption.get mustBe true
    }

    "Validate Compiled payload" in {
      val validCompiledPayload = Json.parse(
        """
          |{
          |   "aftDetails":{
          |      "aftStatus":"Compiled",
          |      "quarterEndDate":"2020-12-31",
          |      "quarterStartDate":"2020-10-01"
          |   },
          |   "chargeDetails":{
          |      "chargeTypeEDetails":{
          |         "totalAmount":1000.02,
          |         "amendedVersion":1,
          |         "memberDetails":[
          |            {
          |               "taxYearEnding":"2018",
          |               "individualsDetails":{
          |                  "firstName":"Ant",
          |                  "lastName":"Woakes",
          |                  "nino":"AA001230A"
          |               },
          |               "dateOfNotice":"2018-02-28",
          |               "amountOfCharge":110.02,
          |               "paidUnder237b":"Yes",
          |               "memberStatus":"New",
          |               "memberAFTVersion":1
          |            },
          |            {
          |               "taxYearEnding":"2018",
          |               "individualsDetails":{
          |                  "firstName":"Victor",
          |                  "lastName":"Brown",
          |                  "nino":"AA034000A"
          |               },
          |               "dateOfNotice":"2018-02-28",
          |               "amountOfCharge":1230.02,
          |               "paidUnder237b":"Yes",
          |               "memberStatus":"New",
          |               "memberAFTVersion":1
          |            }
          |         ]
          |      },
          |      "chargeTypeCDetails":{
          |         "totalAmount":90.02,
          |         "amendedVersion":1,
          |         "memberDetails":[
          |            {
          |               "dateOfPayment":"2020-10-28",
          |               "memberTypeDetails":{
          |                  "individualDetails":{
          |                     "firstName":"Ray",
          |                     "lastName":"Golding",
          |                     "nino":"AA000020A"
          |                  },
          |                  "memberType":"Individual"
          |               },
          |               "totalAmountOfTaxDue":2300.02,
          |               "memberStatus":"New",
          |               "memberAFTVersion":1,
          |               "correspondenceAddressDetails":{
          |                  "countryCode":"GB",
          |                  "postalCode":"TF3 4NT",
          |                  "addressLine1":"Plaza 2 ",
          |                  "addressLine2":"Ironmasters Way",
          |                  "addressLine3":"Telford",
          |                  "addressLine4":"Shropshire",
          |                  "nonUKAddress":"False"
          |               }
          |            },
          |            {
          |               "dateOfPayment":"2020-10-28",
          |               "memberTypeDetails":{
          |                  "individualDetails":{
          |                     "firstName":"Craig",
          |                     "lastName":"McMillan",
          |                     "nino":"AA000620A"
          |                  },
          |                  "memberType":"Individual"
          |               },
          |               "totalAmountOfTaxDue":12340.02,
          |               "memberStatus":"New",
          |               "memberAFTVersion":1,
          |               "correspondenceAddressDetails":{
          |                  "countryCode":"GB",
          |                  "postalCode":"B1 1LA",
          |                  "addressLine1":"45 UpperMarshall Street",
          |                  "addressLine2":"Post Box APTS",
          |                  "addressLine3":"Birmingham",
          |                  "addressLine4":"Warwickshire",
          |                  "nonUKAddress":"False"
          |               }
          |            }
          |         ]
          |      },
          |      "chargeTypeGDetails":{
          |         "totalAmount":1230.02,
          |         "amendedVersion":1,
          |         "memberDetails":[
          |            {
          |               "individualsDetails":{
          |                  "firstName":"Craig",
          |                  "lastName":"White",
          |                  "dateOfBirth":"1980-02-28",
          |                  "nino":"AA012000A"
          |               },
          |               "dateOfTransfer":"2020-10-18",
          |               "qropsReference":"Q300000",
          |               "amountOfTaxDeducted":4560.02,
          |               "memberStatus":"New",
          |               "amountTransferred":45670.02,
          |               "memberAFTVersion":1
          |            },
          |            {
          |               "individualsDetails":{
          |                  "firstName":"James",
          |                  "lastName":"Reynolds",
          |                  "dateOfBirth":"1960-02-28",
          |                  "nino":"AA200000A"
          |               },
          |               "dateOfTransfer":"2020-10-28",
          |               "qropsReference":"Q000020",
          |               "amountOfTaxDeducted":2450.02,
          |               "memberStatus":"New",
          |               "amountTransferred":24560.02,
          |               "memberAFTVersion":1
          |            }
          |         ]
          |      },
          |      "chargeTypeDDetails":{
          |         "totalAmount":2345.02,
          |         "amendedVersion":1,
          |         "memberDetails":[
          |            {
          |               "totalAmtOfTaxDueAtHigherRate":9.02,
          |               "individualsDetails":{
          |                  "firstName":"Joy",
          |                  "lastName":"Kenneth",
          |                  "nino":"AA089000A"
          |               },
          |               "memberStatus":"New",
          |               "totalAmtOfTaxDueAtLowerRate":1.02,
          |               "memberAFTVersion":1,
          |               "dateOfBeneCrysEvent":"2020-10-18"
          |            },
          |            {
          |               "totalAmtOfTaxDueAtHigherRate":10.02,
          |               "individualsDetails":{
          |                  "firstName":"Brian",
          |                  "lastName":"Lara",
          |                  "nino":"AA100000A"
          |               },
          |               "memberStatus":"New",
          |               "totalAmtOfTaxDueAtLowerRate":3.02,
          |               "memberAFTVersion":1,
          |               "dateOfBeneCrysEvent":"2020-10-28"
          |            }
          |         ]
          |      },
          |      "chargeTypeFDetails":{
          |         "totalAmount":1000.02,
          |         "amendedVersion":1,
          |         "dateRegiWithdrawn":"2020-10-28"
          |      },
          |      "chargeTypeADetails":{
          |         "totalAmtOfTaxDueAtHigherRate":250,
          |         "totalAmount":270,
          |         "numberOfMembers":2,
          |         "amendedVersion":1,
          |         "totalAmtOfTaxDueAtLowerRate":20
          |      },
          |      "chargeTypeBDetails":{
          |         "totalAmount":100.02,
          |         "numberOfMembers":2,
          |         "amendedVersion":1
          |      }
          |   }
          |}
          |""".stripMargin
      )
      jsonPayloadSchemaValidator.validateJsonPayload(schemaPath, validCompiledPayload).toOption.get mustBe true
    }

    "Validate Compiled invalid payload with multiple errors" in {
      val validCompiledPayload = Json.parse(
        """
          |{
          |   "aftDetails":{
          |      "aftStatus":"Compiled",
          |      "quarterEndDate":"SSSSSSSSSS",
          |      "quarterStartDate":"DDDDDDDDD"
          |   },
          |   "chargeDetails":{
          |      "chargeTypeEDetails":{
          |         "totalAmount":1000.02,
          |         "amendedVersion":1,
          |         "memberDetails":[
          |            {
          |               "taxYearEnding":"2018",
          |               "individualsDetails":{
          |                  "firstName":"Ant",
          |                  "lastName":"Woakes",
          |                  "nino":"AA001230A"
          |               },
          |               "dateOfNotice":"2018-02-28",
          |               "amountOfCharge":110.02,
          |               "paidUnder237b":"Yes",
          |               "memberStatus":"New",
          |               "memberAFTVersion":1
          |            },
          |            {
          |               "taxYearEnding":"2018",
          |               "individualsDetails":{
          |                  "firstName":"Victor",
          |                  "lastName":"Brown",
          |                  "nino":"AA034000A"
          |               },
          |               "dateOfNotice":"2018-02-28",
          |               "amountOfCharge":1230.02,
          |               "paidUnder237b":"Yes",
          |               "memberStatus":"New",
          |               "memberAFTVersion":1
          |            }
          |         ]
          |      },
          |      "chargeTypeCDetails":{
          |         "totalAmount":90.02,
          |         "amendedVersion":1,
          |         "memberDetails":[
          |            {
          |               "dateOfPayment":"2020-10-28",
          |               "memberTypeDetails":{
          |                  "individualDetails":{
          |                     "firstName":"Ray",
          |                     "lastName":"Golding",
          |                     "nino":"AA000020A"
          |                  },
          |                  "memberType":"Individual"
          |               },
          |               "totalAmountOfTaxDue":2300.02,
          |               "memberStatus":"New",
          |               "memberAFTVersion":1,
          |               "correspondenceAddressDetails":{
          |                  "countryCode":"GB",
          |                  "postalCode":"TF3 4NT",
          |                  "addressLine1":"Plaza 2 ",
          |                  "addressLine2":"Ironmasters Way",
          |                  "addressLine3":"Telford",
          |                  "addressLine4":"Shropshire",
          |                  "nonUKAddress":"False"
          |               }
          |            },
          |            {
          |               "dateOfPayment":"2020-10-28",
          |               "memberTypeDetails":{
          |                  "individualDetails":{
          |                     "firstName":"Craig",
          |                     "lastName":"McMillan",
          |                     "nino":"AA000620A"
          |                  },
          |                  "memberType":"Individual"
          |               },
          |               "totalAmountOfTaxDue":12340.02,
          |               "memberStatus":"New",
          |               "memberAFTVersion":1,
          |               "correspondenceAddressDetails":{
          |                  "countryCode":"GB",
          |                  "postalCode":"B1 1LA",
          |                  "addressLine1":"45 UpperMarshall Street",
          |                  "addressLine2":"Post Box APTS",
          |                  "addressLine3":"Birmingham",
          |                  "addressLine4":"Warwickshire",
          |                  "nonUKAddress":"False"
          |               }
          |            }
          |         ]
          |      },
          |      "chargeTypeGDetails":{
          |         "totalAmount":1230.02,
          |         "amendedVersion":1,
          |         "memberDetails":[
          |            {
          |               "individualsDetails":{
          |                  "firstName":"Craig",
          |                  "lastName":"White",
          |                  "dateOfBirth":"1980-02-28",
          |                  "nino":"AA012000A"
          |               },
          |               "dateOfTransfer":"2020-10-18",
          |               "qropsReference":"Q300000",
          |               "amountOfTaxDeducted":4560.02,
          |               "memberStatus":"New",
          |               "amountTransferred":45670.02,
          |               "memberAFTVersion":1
          |            },
          |            {
          |               "individualsDetails":{
          |                  "firstName":"James",
          |                  "lastName":"Reynolds",
          |                  "dateOfBirth":"1960-02-28",
          |                  "nino":"AA200000A"
          |               },
          |               "dateOfTransfer":"2020-10-28",
          |               "qropsReference":"Q000020",
          |               "amountOfTaxDeducted":2450.02,
          |               "memberStatus":"New",
          |               "amountTransferred":24560.02,
          |               "memberAFTVersion":1
          |            }
          |         ]
          |      },
          |      "chargeTypeDDetails":{
          |         "totalAmount":2345.02,
          |         "amendedVersion":1,
          |         "memberDetails":[
          |            {
          |               "totalAmtOfTaxDueAtHigherRate":9.02,
          |               "individualsDetails":{
          |                  "firstName":"Joy",
          |                  "lastName":"Kenneth",
          |                  "nino":"AA089000A"
          |               },
          |               "memberStatus":"New",
          |               "totalAmtOfTaxDueAtLowerRate":1.02,
          |               "memberAFTVersion":1,
          |               "dateOfBeneCrysEvent":"2020-10-18"
          |            },
          |            {
          |               "totalAmtOfTaxDueAtHigherRate":10.02,
          |               "individualsDetails":{
          |                  "firstName":"Brian",
          |                  "lastName":"Lara",
          |                  "nino":"AA100000A"
          |               },
          |               "memberStatus":"New",
          |               "totalAmtOfTaxDueAtLowerRate":3.02,
          |               "memberAFTVersion":1,
          |               "dateOfBeneCrysEvent":"2020-10-28"
          |            }
          |         ]
          |      },
          |      "chargeTypeFDetails":{
          |         "totalAmount":1000.02,
          |         "amendedVersion":1,
          |         "dateRegiWithdrawn":"2020-10-28"
          |      },
          |      "chargeTypeADetails":{
          |         "totalAmtOfTaxDueAtHigherRate":250,
          |         "totalAmount":270,
          |         "numberOfMembers":2,
          |         "amendedVersion":1,
          |         "totalAmtOfTaxDueAtLowerRate":20
          |      },
          |      "chargeTypeBDetails":{
          |         "totalAmount":100.02,
          |         "numberOfMembers":2,
          |         "amendedVersion":1
          |      }
          |   }
          |}
          |""".stripMargin
      )
      val result = jsonPayloadSchemaValidator.validateJsonPayload(schemaPath, validCompiledPayload)
      result.swap.toOption.get.mkString mustBe "ErrorReport({\"pointer\":\"/aftDetails/quarterEndDate\"},\"ECMA 262 regex" +
        " \\\"^(((19|20)([2468][048]|[13579][26]|0[48])|2000)[-]02[-]29|((19|20)[0-9]{2}[-](0[469]|11)" +
        "[-](0[1-9]|1[0-9]|2[0-9]|30)|(19|20)[0-9]{2}[-](0[13578]|1[02])[-](0[1-9]|[12][0-9]|3[01])|(19|20)[0-9]{2}[-]02[-](0[1-9]|1[0-9]|2[0-8])))" +
        "$\\\" does not match )" +
        "ErrorReport({\"pointer\":\"/aftDetails/quarterStartDate\"}," +
        "\"ECMA 262 regex \\\"^(((19|20)([2468][048]|[13579][26]|0[48])|2000)[-]02[-]29|((19|20)[0-9]{2}[-](0[469]|11)[-]" +
        "(0[1-9]|1[0-9]|2[0-9]|30)|(19|20)[0-9]{2}[-](0[13578]|1[02])[-](0[1-9]|[12][0-9]|3[01])|(19|20)[0-9]{2}[-]02[-](0[1-9]|1[0-9]|2[0-8])))" +
        "$\\\" does not match )"
    }

    "Validate full payload" in {
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
      result.toOption.get mustBe true
    }


    "Validate invalid payload with single error" in {
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
      val expectedError = "ErrorReport({\"pointer\":\"/chargeDetails/chargeTypeFDetails/totalAmount\"}," +
        "\"instance type (string) does not match any allowed primitive type (allowed: [\\\"integer\\\",\\\"number\\\"])\")"
      result.swap.toOption.get.mkString mustBe expectedError
    }

    "Validate invalid payload with multiple errors" in {
      val json = Json.parse(
        """
          |{
          |   "aftDetails":{
          |      "aftStatus":"Compiled",
          |      "quarterEndDate":"hhhhhhhhhhhhhhhhhhhhhhh",
          |      "quarterStartDate":"MMMMMMMMMMMMMMMM"
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
      result.swap.toOption.get.mkString mustBe "ErrorReport({\"pointer\":\"/aftDetails/quarterEndDate\"},\"" +
        "ECMA 262 regex \\\"^(((19|20)([2468][048]|[13579][26]|0[48])|2000)[-]02[-]29|((19|20)[0-9]{2}[-]" +
        "(0[469]|11)[-](0[1-9]|1[0-9]|2[0-9]|30)|(19|20)[0-9]{2}[-](0[13578]|1[02])[-](0[1-9]|[12][0-9]|3[01])" +
        "|(19|20)[0-9]{2}[-]02[-](0[1-9]|1[0-9]|2[0-8])))$\\\" does not match )" +
        "ErrorReport({\"pointer\":\"/aftDetails/quarterStartDate\"},\"ECMA 262 regex \\\"^(((19|20)([2468][048]|[13579][26]|0[48])|2000)" +
        "[-]02[-]29|((19|20)[0-9]{2}[-](0[469]|11)[-](0[1-9]|1[0-9]|2[0-9]|30)|(19|20)[0-9]{2}[-](0[13578]|1[02])" +
        "[-](0[1-9]|[12][0-9]|3[01])|(19|20)[0-9]{2}[-]02[-](0[1-9]|1[0-9]|2[0-8])))$\\\" does not match )" +
        "ErrorReport({\"pointer\":\"/chargeDetails/chargeTypeFDetails/totalAmount\"}," +
        "\"instance type (string) does not match any allowed primitive type (allowed: [\\\"integer\\\",\\\"number\\\"])\")"
    }
  }
}
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

package transformations.ETMPToUserAnswers

import org.scalatest.OptionValues
import org.scalatest.freespec.AnyFreeSpec
import play.api.libs.json.Json
import transformations.generators.AFTETMPResponseGenerators

import java.time.LocalDate

class AFTDetailsTransformerSpec extends AnyFreeSpec with AFTETMPResponseGenerators with OptionValues { // scalastyle:off magic.number

  import AFTDetailsTransformerSpec._

  private val chargeATransformer = new ChargeATransformer
  private val chargeBTransformer = new ChargeBTransformer
  private val chargeCTransformer = new ChargeCTransformer
  private val chargeDTransformer = new ChargeDTransformer
  private val chargeETransformer = new ChargeETransformer
  private val chargeFTransformer = new ChargeFTransformer
  private val chargeGTransformer = new ChargeGTransformer

  "An AFT Details Transformer" - {
    "must transform from ETMP Get Details API Format to UserAnswers format" in {
      val transformer = new AFTDetailsTransformer(chargeATransformer, chargeBTransformer, chargeCTransformer,
        chargeDTransformer, chargeETransformer, chargeFTransformer, chargeGTransformer)
      val transformedUserAnswersJson = etmpResponseJson.transform(transformer.transformToUserAnswers).asOpt.value
      transformedUserAnswersJson mustBe userAnswersJson
    }

    "must tranform a datetime to localdate" in {
      val aftDetailsJson = Json.obj(
        "abc" -> Json.obj(
          "def" -> Json.toJson("2020-12-12T09:30:47Z")
        )
      )

      val result = (aftDetailsJson \ "abc" \ "def").asOpt[LocalDate](AFTDetailsTransformer.localDateDateReads)
      result mustBe Some(LocalDate.of(2020, 12, 12))
    }
  }
}

object AFTDetailsTransformerSpec {

  private val userAnswersJson = Json.parse(
    """{
      |  "aftStatus": "Compiled",
      |  "aftVersion": 1,
      |  "pstr": "1234",
      |  "schemeName": "Test Scheme",
      |  "quarter": {
      |    "startDate": "2019-01-01",
      |    "endDate": "2019-03-31"
      |  },
      |  "chargeADetails": {
      |    "chargeDetails": {
      |      "numberOfMembers": 2,
      |      "totalAmtOfTaxDueAtLowerRate": 200.02,
      |      "totalAmtOfTaxDueAtHigherRate": 200.02,
      |      "totalAmount": 200.02
      |    },
      |    "amendedVersion": 1
      |  },
      |  "chargeBDetails": {
      |    "chargeDetails": {
      |      "numberOfDeceased": 2,
      |      "totalAmount": 100.02
      |    },
      |    "amendedVersion": 1
      |  },
      |  "chargeCDetails": {
      |    "employers": [
      |      {
      |        "memberStatus": "New",
      |        "memberAFTVersion": 2,
      |        "whichTypeOfSponsoringEmployer": "individual",
      |        "chargeDetails": {
      |          "paymentDate": "2020-01-01",
      |          "amountTaxDue": 500.02
      |        },
      |        "sponsoringIndividualDetails": {
      |          "firstName": "testFirst",
      |          "lastName": "testLast",
      |          "nino": "AB100100A"
      |        },
      |        "sponsoringEmployerAddress": {
      |          "line1": "line1",
      |          "line2": "line2",
      |          "line3": "line3",
      |          "line4": "line4",
      |          "postcode": "NE20 0GG",
      |          "country": "GB"
      |        }
      |      }
      |    ],
      |    "totalChargeAmount": 500.02,
      |    "amendedVersion": 1
      |  },
      |  "chargeDDetails": {
      |    "members": [
      |      {
      |        "memberStatus": "Changed",
      |        "memberAFTVersion": 1,
      |        "memberDetails": {
      |          "firstName": "Joy",
      |          "lastName": "Kenneth",
      |          "nino": "AA089000A"
      |        },
      |        "chargeDetails": {
      |          "dateOfEvent": "2016-02-29",
      |          "taxAt25Percent": 1.02,
      |          "taxAt55Percent": 9.02
      |        }
      |      }
      |    ],
      |    "totalChargeAmount": 2345.02,
      |    "amendedVersion": 1
      |  },
      |  "chargeEDetails": {
      |    "members": [
      |      {
      |        "memberStatus": "New",
      |        "memberAFTVersion": 3,
      |        "memberDetails": {
      |          "firstName": "eFirstName",
      |          "lastName": "eLastName",
      |          "nino": "AE100100A"
      |        },
      |        "annualAllowanceYear": "2019",
      |        "chargeDetails": {
      |          "dateNoticeReceived": "2020-01-11",
      |          "chargeAmount": 200.02,
      |          "isPaymentMandatory": true
      |        }
      |      }
      |    ],
      |    "totalChargeAmount": 200.02,
      |    "amendedVersion": 1
      |  },
      |  "chargeFDetails": {
      |    "chargeDetails": {
      |      "totalAmount": 200.02,
      |      "deRegistrationDate": "1980-02-29"
      |    },
      |    "amendedVersion": 1
      |  },
      |  "chargeGDetails": {
      |    "members": [
      |      {
      |        "memberStatus": "Deleted",
      |        "memberAFTVersion": 1,
      |        "memberDetails": {
      |          "firstName": "Craig",
      |          "lastName": "White",
      |          "dob": "1980-02-29",
      |          "nino": "AA012000A"
      |        },
      |        "chargeDetails": {
      |          "qropsReferenceNumber": "300000",
      |          "qropsTransferDate": "2016-02-29"
      |        },
      |        "chargeAmounts": {
      |          "amountTransferred": 45670.02,
      |          "amountTaxDue": 4560.02
      |        }
      |      }
      |    ],
      |    "totalChargeAmount": 1230.02,
      |    "amendedVersion": 1
      |  },
      |    "submitterDetails": {
      |      "submitterType": "PSP",
      |      "submitterID": "10000240",
      |      "authorisingPsaId": "A0003450",
      |      "submitterName": "Martin Brookes",
      |      "receiptDate": "2016-12-17"
      |    }
      |}""".stripMargin)

  private val etmpResponseJson = Json.parse(
    """{
      |  "aftDetails": {
      |    "aftStatus": "Compiled",
      |    "aftVersion": "001",
      |    "quarterStartDate": "2019-01-01",
      |    "quarterEndDate": "2019-03-31",
      |    "receiptDate": "2016-12-17T09:30:47Z"
      |  },
      |  "schemeDetails": {
      |    "schemeName": "Test Scheme",
      |    "pstr": "1234"
      |  },
      |  "chargeDetails": {
      |    "chargeTypeA": {
      |      "amendedVersion": "001",
      |      "numberOfMembers": 2,
      |      "totalAmtOfTaxDueAtLowerRate": 200.02,
      |      "totalAmtOfTaxDueAtHigherRate": 200.02,
      |      "totalAmount": 200.02
      |    },
      |    "chargeTypeB": {
      |      "amendedVersion": "001",
      |      "numberOfMembers": 2,
      |      "totalAmount": 100.02
      |    },
      |    "chargeTypeC": {
      |      "totalAmount": 500.02,
      |      "amendedVersion": "001",
      |      "memberDetails": [
      |        {
      |          "memberStatus": "New",
      |          "memberAFTVersion": "002",
      |            "memberType": "Individual",
      |            "individualDetails": {
      |            "firstName": "testFirst",
      |            "lastName": "testLast",
      |            "ninoRef": "AB100100A"
      |          },
      |          "addressDetails": {
      |            "nonUKAddress": false,
      |            "postCode": "NE20 0GG",
      |            "addressLine1": "line1",
      |            "addressLine2": "line2",
      |            "addressLine3": "line3",
      |            "addressLine4": "line4",
      |            "country": "GB"
      |          },
      |          "dateOfPayment": "2020-01-01",
      |          "totalAmountOfTaxDue": 500.02
      |        }
      |      ]
      |    },
      |    "chargeTypeD": {
      |      "amendedVersion": "001",
      |      "totalAmount": 2345.02,
      |      "memberDetails": [
      |        {
      |          "memberStatus": "Changed",
      |          "memberAFTVersion": "001",
      |          "individualDetails": {
      |            "title": "Mr",
      |            "firstName": "Joy",
      |            "middleName": "H",
      |            "lastName": "Kenneth",
      |            "ninoRef": "AA089000A"
      |          },
      |          "dateOfBenefitCrystalizationEvent": "2016-02-29",
      |          "totalAmountDueAtLowerRate": 1.02,
      |          "totalAmountDueAtHigherRate": 9.02
      |        }
      |      ]
      |    },
      |    "chargeTypeE": {
      |      "amendedVersion": "001",
      |      "totalAmount": 200.02,
      |      "memberDetails": [
      |        {
      |          "memberStatus": "New",
      |          "memberAFTVersion": "003",
      |          "individualDetails": {
      |            "firstName": "eFirstName",
      |            "lastName": "eLastName",
      |            "ninoRef": "AE100100A"
      |          },
      |          "amountOfCharge": 200.02,
      |          "taxYearEnding": "2020",
      |          "dateOfNotice": "2020-01-11",
      |          "paidUnder237b": "Yes"
      |        }
      |      ]
      |    },
      |    "chargeTypeF": {
      |      "amendedVersion": "001",
      |      "totalAmount": 200.02,
      |      "dateRegiWithdrawn": "1980-02-29"
      |    },
      |    "chargeTypeG": {
      |      "amendedVersion": "001",
      |      "totalOTCAmount": 1230.02,
      |      "memberDetails": [
      |        {
      |          "memberStatus": "Deleted",
      |          "memberAFTVersion": "001",
      |          "individualDetails": {
      |            "title": "Mr",
      |            "firstName": "Craig",
      |            "middleName": "H",
      |            "lastName": "White",
      |            "dateOfBirth": "1980-02-29",
      |            "ninoRef": "AA012000A"
      |          },
      |          "qropsReference": "Q300000",
      |          "amountTransferred": 45670.02,
      |          "dateOfTransfer": "2016-02-29",
      |          "amountOfTaxDeducted": 4560.02
      |        }
      |      ]
      |    }
      |  },
      |    "aftDeclarationDetails":  {
      |      "submittedBy": "PSP",
      |      "submitterId": "10000240",
      |      "psaId": "A0003450",
      |      "submitterName": "Martin Brookes",
      |      "pspDeclarationDetails": {
      |        "pspDeclaration1": "true",
      |        "pspDeclaration2": "true"
      |      }
      |    }
      |}
      |""".stripMargin)
}

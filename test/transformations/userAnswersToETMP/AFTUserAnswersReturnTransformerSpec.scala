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

package transformations.userAnswersToETMP

import org.scalatest.FreeSpec
import play.api.libs.json.Json
import transformations.generators.AFTUserAnswersGenerators

class AFTUserAnswersReturnTransformerSpec extends FreeSpec with AFTUserAnswersGenerators {

  private val chargeATransformer = new ChargeATransformer
  private val chargeBTransformer = new ChargeBTransformer
  private val chargeCTransformer = new ChargeCTransformer
  private val chargeDTransformer = new ChargeDTransformer
  private val chargeETransformer = new ChargeETransformer
  private val chargeFTransformer = new ChargeFTransformer
  private val chargeGTransformer = new ChargeGTransformer

  "An AFTReturn Transformer" - {
    "must transform from UserAnswers to ETMP AFT Return format" in {
      val transformer = new AFTReturnTransformer(chargeATransformer, chargeBTransformer,
        chargeCTransformer, chargeDTransformer, chargeETransformer, chargeFTransformer, chargeGTransformer)

      val transformedEtmpJson = userAnswersRequestJson.transform(transformer.transformToETMPFormat).asOpt.value
      transformedEtmpJson mustBe etmpResponseJson
    }
  }

  private val userAnswersRequestJson = Json.parse(
    """{
      |  "aftStatus": "Compiled",
      |  "quarter": {
      |    "startDate": "2019-01-01",
      |    "endDate": "2019-03-31"
      |  },
      |  "chargeADetails": {
      |    "numberOfMembers": 2,
      |    "totalAmtOfTaxDueAtLowerRate": 200.02,
      |    "totalAmtOfTaxDueAtHigherRate": 200.02,
      |    "totalAmount": 200.02
      |  },
      |  "chargeBDetails": {
      |    "numberOfDeceased": 4,
      |    "amountTaxDue": 55.55
      |  },
      |  "chargeCDetails": {
      |    "isSponsoringEmployerIndividual": true,
      |    "chargeDetails": {
      |      "paymentDate": "2020-01-01",
      |      "amountTaxDue": 500.02
      |    },
      |    "sponsoringIndividualDetails": {
      |      "firstName": "testFirst",
      |      "lastName": "testLast",
      |      "nino": "AB100100A"
      |    },
      |    "sponsoringEmployerAddress": {
      |      "line1": "line1",
      |      "line2": "line2",
      |      "line3": "line3",
      |      "line4": "line4",
      |      "postcode": "NE20 0GG",
      |      "country": "GB"
      |    }
      |  },
      |  "chargeDDetails": {
      |    "numberOfMembers": 2,
      |    "members": [
      |      {
      |        "memberDetails": {
      |          "firstName": "firstName",
      |          "lastName": "lastName",
      |          "nino": "AC100100A",
      |          "isDeleted": false
      |        },
      |        "chargeDetails": {
      |          "dateOfEvent": "2020-01-10",
      |          "taxAt25Percent": 100,
      |          "taxAt55Percent": 100.02
      |        }
      |      }
      |    ],
      |    "totalChargeAmount": 200.02
      |  },
      |  "chargeEDetails": {
      |    "members": [
      |      {
      |        "memberDetails": {
      |          "firstName": "eFirstName",
      |          "lastName": "eLastName",
      |          "nino": "AE100100A",
      |          "isDeleted": false
      |        },
      |        "annualAllowanceYear": "2020",
      |        "chargeDetails": {
      |          "dateNoticeReceived": "2020-01-11",
      |          "chargeAmount": 200.02,
      |          "isPaymentMandatory": true
      |        }
      |      }
      |    ],
      |    "totalChargeAmount": 200.02
      |  },
      |  "chargeFDetails": {
      |    "amountTaxDue": 200.02,
      |    "deRegistrationDate": "1980-02-29"
      |  }
      |}""".stripMargin)

  private val etmpResponseJson = Json.parse(
    """{
      |  "aftDetails": {
      |    "aftStatus": "Compiled",
      |    "quarterStartDate": "2019-01-01",
      |    "quarterEndDate": "2019-03-31"
      |  },
      |  "chargeDetails": {
      |    "chargeTypeADetails": {
      |      "numberOfMembers": 2,
      |      "totalAmtOfTaxDueAtLowerRate": 200.02,
      |      "totalAmtOfTaxDueAtHigherRate": 200.02,
      |      "totalAmount": 200.02
      |    },
      |    "chargeTypeBDetails": {
      |      "numberOfMembers": 4,
      |      "totalAmount": 55.55
      |    },
      |    "chargeTypeCDetails": {
      |      "totalAmount": 500.02,
      |      "memberDetails": [
      |        {
      |          "memberStatus": "New",
      |          "memberTypeDetails": {
      |            "memberType": "Individual",
      |            "individualDetails": {
      |              "firstName": "testFirst",
      |              "lastName": "testLast",
      |              "nino": "AB100100A"
      |            }
      |          },
      |          "correspondenceAddressDetails": {
      |            "nonUKAddress": "False",
      |            "postCode": "NE20 0GG",
      |            "addressLine1": "line1",
      |            "addressLine2": "line2",
      |            "addressLine3": "line3",
      |            "addressLine4": "line4",
      |            "countryCode": "GB"
      |          },
      |          "dateOfPayment": "2020-01-01",
      |          "totalAmountOfTaxDue": 500.02
      |        }
      |      ]
      |    },
      |    "chargeTypeDDetails": {
      |      "totalAmount": 200.02,
      |      "memberDetails": [
      |        {
      |          "memberStatus": "New",
      |          "individualsDetails": {
      |            "firstName": "firstName",
      |            "lastName": "lastName",
      |            "nino": "AC100100A"
      |          },
      |          "dateOfBeneCrysEvent": "2020-01-10",
      |          "totalAmtOfTaxDueAtLowerRate": 100,
      |          "totalAmtOfTaxDueAtHigherRate": 100.02
      |        }
      |      ]
      |    },
      |    "chargeTypeEDetails": {
      |      "totalAmount": 200.02,
      |      "memberDetails": [
      |        {
      |          "memberStatus": "New",
      |          "individualsDetails": {
      |            "firstName": "eFirstName",
      |            "lastName": "eLastName",
      |            "nino": "AE100100A"
      |          },
      |          "amountOfCharge": 200.02,
      |          "taxYearEnding": "2020",
      |          "dateOfNotice": "2020-01-11",
      |          "paidUnder237b": "Yes"
      |        }
      |      ]
      |    },
      |    "chargeTypeFDetails": {
      |      "totalAmount": 200.02,
      |      "dateRegiWithdrawn": "1980-02-29"
      |    }
      |  }
      |}
      |""".stripMargin)
}

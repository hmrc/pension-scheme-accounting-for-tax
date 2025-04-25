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

package transformations.userAnswersToETMP

import org.scalatest.OptionValues
import org.scalatest.freespec.AnyFreeSpec
import play.api.libs.json.{Json, __}
import transformations.generators.AFTUserAnswersGenerators

class AFTUserAnswersReturnTransformerSpec extends AnyFreeSpec with AFTUserAnswersGenerators with OptionValues {

  import AFTUserAnswersReturnTransformerSpec._

  "An AFTReturn Transformer" - {
    "must transform from UserAnswers to ETMP AFT Return format for PSA when all mandatory UserAnswers are present"  in {
        val transformer = new AFTReturnTransformer(chargeATransformer, chargeBTransformer,
          chargeCTransformer, chargeDTransformer, chargeETransformer, chargeFTransformer, chargeGTransformer)

        val transformedEtmpJson = userAnswersRequestJsonPSA.transform(transformer.transformToETMPFormat).asOpt.value
        transformedEtmpJson `mustBe` etmpResponseJsonPSA
      }

    "must transform from UserAnswers to ETMP AFT Return format for PSA when a mandatory field in chargeD UserAnswers is missing" in {
        val transformer = new AFTReturnTransformer(chargeATransformer, chargeBTransformer,
          chargeCTransformer, chargeDTransformer, chargeETransformer, chargeFTransformer, chargeGTransformer)

        val userAnswersMissingField = (__ \ "chargeDDetails" \ "totalChargeAmount").prune(userAnswersRequestJsonPSA).asOpt.value
        val transformedEtmpJson = userAnswersMissingField.transform(transformer.transformToETMPFormat).asOpt.value

        val etmpResponseWithoutChargeD = (__ \ "chargeDetails" \ "chargeTypeDDetails").prune(etmpResponseJsonPSA).asOpt.value
        transformedEtmpJson mustBe etmpResponseWithoutChargeD
      }

    "must transform from UserAnswers to ETMP AFT Return format for PSP when all mandatory UserAnswers are present" in {
        val transformer = new AFTReturnTransformer(chargeATransformer, chargeBTransformer,
          chargeCTransformer, chargeDTransformer, chargeETransformer, chargeFTransformer, chargeGTransformer)

        val transformedEtmpJson = userAnswersRequestJsonPSP.transform(transformer.transformToETMPFormat).asOpt.value
        transformedEtmpJson mustBe etmpResponseJsonPSP
    }

    "must transform from UserAnswers to ETMP AFT Return format when a mandatory field in chargeD UserAnswers is missing" in {
      val transformer = new AFTReturnTransformer(chargeATransformer, chargeBTransformer,
        chargeCTransformer, chargeDTransformer, chargeETransformer, chargeFTransformer, chargeGTransformer)

      val userAnswersMissingField = (__ \ "chargeDDetails" \ "totalChargeAmount").prune(userAnswersRequestJsonPSA).asOpt.value
      val transformedEtmpJson = userAnswersMissingField.transform(transformer.transformToETMPFormat).asOpt.value

      val etmpResponseWithoutChargeD = (__ \ "chargeDetails" \ "chargeTypeDDetails").prune(etmpResponseJsonPSA).asOpt.value
      transformedEtmpJson mustBe etmpResponseWithoutChargeD
    }
  }
}

object AFTUserAnswersReturnTransformerSpec {
  private val chargeATransformer = new ChargeATransformer
  private val chargeBTransformer = new ChargeBTransformer
  private val chargeCTransformer = new ChargeCTransformer
  private val chargeDTransformer = new ChargeDTransformer
  private val chargeETransformer = new ChargeETransformer
  private val chargeFTransformer = new ChargeFTransformer
  private val chargeGTransformer = new ChargeGTransformer

  private val userAnswersRequestJsonPSA = Json.parse(
    """{
      |  "aftStatus": "Compiled",
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
      |    "amendedVersion": 2
      |  },
      |  "chargeBDetails": {
      |    "chargeDetails": {
      |      "numberOfDeceased": 4,
      |      "totalAmount": 55.55
      |    }
      |  },
      |  "chargeCDetails": {
      |    "employers": [
      |      {
      |        "whichTypeOfSponsoringEmployer": "individual",
      |        "memberStatus": "Changed",
      |        "memberAFTVersion": 1,
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
      |    "numberOfMembers": 2,
      |    "members": [
      |      {
      |        "memberStatus": "Deleted",
      |        "memberAFTVersion": 1,
      |        "memberDetails": {
      |          "firstName": "firstName",
      |          "lastName": "lastName",
      |          "nino": "AC100100A",
      |          "isDeleted": true
      |        },
      |        "chargeDetails": {
      |          "dateOfEvent": "2020-01-10",
      |          "taxAt25Percent": 100,
      |          "taxAt55Percent": 100.02
      |        }
      |      },
      |      {
      |        "memberStatus": "New",
      |        "memberAFTVersion": 1,
      |        "memberDetails": {
      |          "firstName": "secondName",
      |          "lastName": "lastName",
      |          "nino": "AC100100A",
      |          "isDeleted": true
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
      |          "nino": "AE100100A"
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
      |    "chargeDetails": {
      |      "totalAmount": 200.02,
      |      "deRegistrationDate": "1980-02-29"
      |    }
      |  },
      |  "chargeGDetails": {
      |    "members": [
      |      {
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
      |    "totalChargeAmount": 1230.02
      |  },
      |  "declaration" : {
      |    "submittedBy" : "PSA",
      |    "submittedID" : "A2000000",
      |    "hasAgreed" : true
      |  }
      |}""".stripMargin)

  private val etmpResponseJsonPSA = Json.parse(
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
      |      "totalAmount": 200.02,
      |      "amendedVersion": 2
      |    },
      |    "chargeTypeBDetails": {
      |      "numberOfMembers": 4,
      |      "totalAmount": 55.55
      |    },
      |    "chargeTypeCDetails": {
      |      "totalAmount": 500.02,
      |      "amendedVersion": 1,
      |      "memberDetails": [
      |        {
      |          "memberStatus": "Changed",
      |          "memberAFTVersion": 1,
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
      |            "postalCode": "NE20 0GG",
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
      |  "chargeTypeDDetails": {
      |      "memberDetails": [
      |         {
      |             "dateOfBeneCrysEvent": "2020-01-10",
      |             "individualsDetails": {
      |                 "firstName": "firstName",
      |                 "lastName": "lastName",
      |                 "nino": "AC100100A"
      |             },
      |             "memberAFTVersion": 1,
      |             "lfAllowanceChgPblSerRem":"No",
      |             "memberStatus": "Deleted",
      |             "totalAmtOfTaxDueAtHigherRate": 100.02,
      |             "totalAmtOfTaxDueAtLowerRate": 100
      |         },
      |         {
      |             "dateOfBeneCrysEvent": "2020-01-10",
      |             "individualsDetails": {
      |                 "firstName": "secondName",
      |                 "lastName": "lastName",
      |                 "nino": "AC100100A"
      |             },
      |             "memberAFTVersion": 1,
      |             "lfAllowanceChgPblSerRem":"No",
      |             "memberStatus": "New",
      |             "totalAmtOfTaxDueAtHigherRate": 100.02,
      |             "totalAmtOfTaxDueAtLowerRate": 100
      |         }
      |     ],
      |     "totalAmount": 200.02
      | },
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
      |          "anAllowanceChgPblSerRem": "No",
      |          "paidUnder237b": "Yes"
      |        }
      |      ]
      |    },
      |    "chargeTypeFDetails": {
      |      "totalAmount": 200.02,
      |      "dateRegiWithdrawn": "1980-02-29"
      |    },
      |    "chargeTypeGDetails": {
      |      "totalAmount": 1230.02,
      |      "memberDetails": [
      |        {
      |          "memberStatus": "New",
      |          "individualsDetails": {
      |            "firstName": "Craig",
      |            "lastName": "White",
      |            "dateOfBirth": "1980-02-29",
      |            "nino": "AA012000A"
      |          },
      |          "qropsReference": "Q300000",
      |          "amountTransferred": 45670.02,
      |          "dateOfTransfer": "2016-02-29",
      |          "amountOfTaxDeducted": 4560.02
      |        }
      |      ]
      |    }
      |  },
      |  "aftDeclarationDetails": {
      |    "submittedBy": "PSA",
      |    "submittedID": "A2000000",
      |    "psaDeclarationDetails": {
      |      "psaDeclaration1": true,
      |      "psaDeclaration2": true
      |    }
      |  }
      |}
      |""".stripMargin)


  private val userAnswersRequestJsonPSP = Json.parse(
    """{
      |  "aftStatus": "Compiled",
      |  "quarter": {
      |    "startDate": "2019-01-01",
      |    "endDate": "2019-03-31"
      |  },
      |  "enterPsaId": "A2000000",
      |  "chargeADetails": {
      |    "chargeDetails": {
      |      "numberOfMembers": 2,
      |      "totalAmtOfTaxDueAtLowerRate": 200.02,
      |      "totalAmtOfTaxDueAtHigherRate": 200.02,
      |      "totalAmount": 200.02
      |    },
      |    "amendedVersion": 2
      |  },
      |  "chargeBDetails": {
      |    "chargeDetails": {
      |      "numberOfDeceased": 4,
      |      "totalAmount": 55.55
      |    }
      |  },
      |  "chargeCDetails": {
      |    "employers": [
      |      {
      |        "whichTypeOfSponsoringEmployer": "individual",
      |        "memberStatus": "Changed",
      |        "memberAFTVersion": 1,
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
      |    "numberOfMembers": 2,
      |    "members": [
      |      {
      |        "memberStatus": "Deleted",
      |        "memberAFTVersion": 1,
      |        "memberDetails": {
      |          "firstName": "firstName",
      |          "lastName": "lastName",
      |          "nino": "AC100100A",
      |          "isDeleted": true
      |        },
      |        "chargeDetails": {
      |          "dateOfEvent": "2020-01-10",
      |          "taxAt25Percent": 100,
      |          "taxAt55Percent": 100.02
      |        }
      |      },
      |      {
      |        "memberStatus": "New",
      |        "memberAFTVersion": 1,
      |        "memberDetails": {
      |          "firstName": "secondName",
      |          "lastName": "lastName",
      |          "nino": "AC100100A",
      |          "isDeleted": true
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
      |          "nino": "AE100100A"
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
      |    "chargeDetails": {
      |      "totalAmount": 200.02,
      |      "deRegistrationDate": "1980-02-29"
      |    }
      |  },
      |  "chargeGDetails": {
      |    "members": [
      |      {
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
      |    "totalChargeAmount": 1230.02
      |  },
      |  "declaration" : {
      |    "submittedBy" : "PSP",
      |    "submittedID" : "22000000",
      |    "hasAgreed" : true
      |  }
      |}""".stripMargin)

  private val etmpResponseJsonPSP = Json.parse(
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
      |      "totalAmount": 200.02,
      |      "amendedVersion": 2
      |    },
      |    "chargeTypeBDetails": {
      |      "numberOfMembers": 4,
      |      "totalAmount": 55.55
      |    },
      |    "chargeTypeCDetails": {
      |      "totalAmount": 500.02,
      |      "amendedVersion": 1,
      |      "memberDetails": [
      |        {
      |          "memberStatus": "Changed",
      |          "memberAFTVersion": 1,
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
      |            "postalCode": "NE20 0GG",
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
      |  "chargeTypeDDetails": {
      |      "memberDetails": [
      |         {
      |             "dateOfBeneCrysEvent": "2020-01-10",
      |             "individualsDetails": {
      |                 "firstName": "firstName",
      |                 "lastName": "lastName",
      |                 "nino": "AC100100A"
      |             },
      |             "memberAFTVersion": 1,
      |             "lfAllowanceChgPblSerRem":"No",
      |             "memberStatus": "Deleted",
      |             "totalAmtOfTaxDueAtHigherRate": 100.02,
      |             "totalAmtOfTaxDueAtLowerRate": 100
      |         },
      |         {
      |             "dateOfBeneCrysEvent": "2020-01-10",
      |             "individualsDetails": {
      |                 "firstName": "secondName",
      |                 "lastName": "lastName",
      |                 "nino": "AC100100A"
      |             },
      |             "memberAFTVersion": 1,
      |             "lfAllowanceChgPblSerRem":"No",
      |             "memberStatus": "New",
      |             "totalAmtOfTaxDueAtHigherRate": 100.02,
      |             "totalAmtOfTaxDueAtLowerRate": 100
      |         }
      |     ],
      |     "totalAmount": 200.02
      | },
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
      |          "anAllowanceChgPblSerRem": "No",
      |          "paidUnder237b": "Yes"
      |        }
      |      ]
      |    },
      |    "chargeTypeFDetails": {
      |      "totalAmount": 200.02,
      |      "dateRegiWithdrawn": "1980-02-29"
      |    },
      |    "chargeTypeGDetails": {
      |      "totalAmount": 1230.02,
      |      "memberDetails": [
      |        {
      |          "memberStatus": "New",
      |          "individualsDetails": {
      |            "firstName": "Craig",
      |            "lastName": "White",
      |            "dateOfBirth": "1980-02-29",
      |            "nino": "AA012000A"
      |          },
      |          "qropsReference": "Q300000",
      |          "amountTransferred": 45670.02,
      |          "dateOfTransfer": "2016-02-29",
      |          "amountOfTaxDeducted": 4560.02
      |        }
      |      ]
      |    }
      |  },
      |  "aftDeclarationDetails": {
      |    "submittedBy": "PSP",
      |    "submittedID": "22000000",
      |    "psaid": "A2000000",
      |    "pspDeclarationDetails": {
      |      "pspDeclaration1": true,
      |      "pspDeclaration2": true
      |    }
      |  }
      |}
      |""".stripMargin)
}

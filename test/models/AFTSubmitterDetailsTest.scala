package models

import org.scalatestplus.play.PlaySpec
import play.api.libs.json._
import java.time.LocalDate
import models.AFTSubmitterDetails._


class AFTSubmitterDetailsTest extends PlaySpec {

  "readAftDetailsFromIF" must {
    "transform valid JSON into an AFTSubmitterDetails instance" in {
      val aftDetailsJson = Json.parse(
        """
          |{
          |  "processingDate": "2016-12-17T09:32:47Z",
          |  "schemeDetails": {
          |    "pstr": "24000041IN",
          |    "schemeName": "Open Scheme Overview API Test 2"
          |  },
          |  "aftDetails": {
          |    "aftVersion": "001",
          |    "aftStatus": "Submitted",
          |    "quarterStartDate": "2020-10-01",
          |    "quarterEndDate": "2020-12-31",
          |    "receiptDate": "2016-12-17T09:30:47Z",
          |    "aftReturnType": "1"
          |  },
          |  "chargeDetails": {
          |    "chargeTypeA": {
          |      "amendedVersion": "001",
          |      "numberOfMembers": 2,
          |      "totalAmtOfTaxDueAtLowerRate": 2000.02,
          |      "totalAmtOfTaxDueAtHigherRate": 2500.02,
          |      "totalAmount": 4500.04
          |    },
          |    "chargeTypeB": {
          |      "amendedVersion": "001",
          |      "numberOfMembers": 2,
          |      "totalAmount": 100.02
          |    },
          |    "chargeTypeC": {
          |      "amendedVersion": "001",
          |      "totalAmount": 90.02,
          |      "memberDetails": [
          |        {
          |          "memberStatus": "New",
          |          "memberAFTVersion": "001",
          |          "memberType": "Individual",
          |          "individualDetails": {
          |            "title": "Mr",
          |            "firstName": "Ray",
          |            "middleName": "S",
          |            "lastName": "Golding",
          |            "ninoRef": "AA000020A"
          |          },
          |          "addressDetails": {
          |            "nonUKAddress": false,
          |            "addressLine1": "Plaza 2 ",
          |            "addressLine2": "Ironmasters Way",
          |            "addressLine3": "Telford",
          |            "addressLine4": "Shropshire",
          |            "country": "GB",
          |            "postCode": "TF3 4NT"
          |          },
          |          "dateOfPayment": "2016-06-29",
          |          "totalAmountOfTaxDue": 2300.02
          |        },
          |        {
          |          "memberStatus": "New",
          |          "memberAFTVersion": "001",
          |          "memberType": "Individual",
          |          "individualDetails": {
          |            "title": "Mr",
          |            "firstName": "Craig",
          |            "middleName": "A",
          |            "lastName": "McMillan",
          |            "ninoRef": "AA000620A"
          |          },
          |          "addressDetails": {
          |            "nonUKAddress": false,
          |            "addressLine1": "45 UpperMarshall Street",
          |            "addressLine2": "Post Box APTS",
          |            "addressLine3": "Birmingham",
          |            "addressLine4": "Warwickshire",
          |            "country": "GB",
          |            "postCode": "B1 1LA"
          |          },
          |          "dateOfPayment": "2016-02-29",
          |          "totalAmountOfTaxDue": 12340.02
          |        }
          |      ]
          |    },
          |    "chargeTypeD": {
          |      "amendedVersion": "001",
          |      "totalAmount": 2345.02,
          |      "memberDetails": [
          |        {
          |          "memberStatus": "New",
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
          |        },
          |        {
          |          "memberStatus": "New",
          |          "memberAFTVersion": "001",
          |          "individualDetails": {
          |            "title": "Mr",
          |            "firstName": "Brian",
          |            "middleName": "C",
          |            "lastName": "Lara",
          |            "ninoRef": "AA100000A"
          |          },
          |          "dateOfBenefitCrystalizationEvent": "2016-02-29",
          |          "totalAmountDueAtLowerRate": 3.02,
          |          "totalAmountDueAtHigherRate": 10.02
          |        }
          |      ]
          |    }
          |  },
          |  "aftDeclarationDetails": {
          |    "submittedBy": "PSP",
          |    "submitterId": "10000240",
          |    "psaId": "A2100005",
          |    "submitterName": "Nigel",
          |    "pspDeclarationDetails": {
          |      "pspDeclaration1": true,
          |      "pspDeclaration2": true
          |    }
          |  }
          |}
        """.stripMargin)


      val result: JsResult[AFTInput] = aftDetailsJson.validate[AFTInput]

      val transformedResult: JsResult[AFTSubmitterDetails] = result.map(aft => AFTTransformer.transform(aft))

        transformedResult mustBe JsSuccess(
        AFTSubmitterDetails(
          submitterType = "PSP",
          submitterName = "Nigel",
          submitterID = "10000240",
          authorisingPsaId = Some("A2100005"),
          receiptDate = LocalDate.of(2016, 12, 17)
        )
      )
    }
  }
}
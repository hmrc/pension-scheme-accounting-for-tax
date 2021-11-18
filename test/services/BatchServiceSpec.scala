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

package services

import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.must.Matchers
import play.api.libs.json.{JsObject, JsArray, Json}
import services.BatchService.{BatchInfo, BatchType}

/*
chargeA = Short service refund lump sum charge
chargeB = Special lump sum death benefits charge
chargeC = Auth surplus
chargeD = Lifetime allowance charge
chargeE = Annual allowance charge
chargeF = De-registration charge
chargeG = Overseas transfer charge
 */

class BatchServiceSpec extends AnyWordSpec with Matchers {
  import BatchServiceSpec._
  "split" must {
    "return correct batch info with no member-based charges" in {
      val payload = payloadHeader ++ payloadChargeTypeA
      batchService.split(payload, batchSize) mustBe Seq(
        BatchInfo(BatchType.Other, 1, payload)
      )
    }

    "return correct batch info with one scheme-based charge (A) and one member-based charge (C)" in {
      val payloadChargeC = payloadChargeTypeC(numberOfItems = 1)
      val payload = payloadHeader ++ payloadChargeTypeA ++ payloadChargeC
      batchService.split(payload, batchSize) mustBe Seq(
        BatchInfo(BatchType.Other, 1,
          payloadHeader ++
            payloadChargeTypeA ++
            concatenateNodes(Seq(payloadChargeTypeCMinusEmployers(numberOfItems = 1)), nodeNameChargeC)
        ),
        BatchInfo(BatchType.ChargeC, 1, payloadChargeTypeCEmployer(numberOfItems = 1))
      )
    }

    "return correct batch info with one scheme-based charge (B) and one member-based charge (D)" in {
      val payloadChargeD = payloadChargeTypeD(numberOfItems = 1)
      val payload = payloadHeader ++ payloadChargeTypeB ++ payloadChargeD
      batchService.split(payload, batchSize) mustBe Seq(
        BatchInfo(BatchType.Other, 1,
          payloadHeader ++
            payloadChargeTypeB ++
            concatenateNodes(Seq(payloadChargeTypeDMinusMembers(numberOfItems = 1)), nodeNameChargeD)
        ),
        BatchInfo(BatchType.ChargeD, 1, payloadChargeTypeDMember(numberOfItems = 1))
      )
    }

    "return correct batch info with one scheme-based charge (F) and one member-based charge (E)" in {
      val payloadChargeE = payloadChargeTypeE(numberOfItems = 1)
      val payload = payloadHeader ++ payloadChargeTypeF ++ payloadChargeE
      batchService.split(payload, batchSize) mustBe Seq(
        BatchInfo(BatchType.Other, 1,
          payloadHeader ++
            payloadChargeTypeF ++
            concatenateNodes(Seq(payloadChargeTypeEMinusMembers(numberOfItems = 1)), nodeNameChargeE)
        ),
        BatchInfo(BatchType.ChargeE, 1, payloadChargeTypeEMember(numberOfItems = 1))
      )
    }

    "return correct batch info with one scheme-based charge (A) and one member-based charge (G)" in {
      val payloadChargeG = payloadChargeTypeG(numberOfItems = 1)
      val payload = payloadHeader ++ payloadChargeTypeA ++ payloadChargeG
      batchService.split(payload, batchSize) mustBe Seq(
        BatchInfo(BatchType.Other, 1,
          payloadHeader ++
            payloadChargeTypeA ++
            concatenateNodes(Seq(payloadChargeTypeGMinusMembers(numberOfItems = 1)), nodeNameChargeG)
        ),
        BatchInfo(BatchType.ChargeG, 1, payloadChargeTypeGMember(numberOfItems = 1))
      )
    }

    "return correct batch info with all three scheme-based charges and all four member-based charges" in {
      val payloadChargeC = payloadChargeTypeC(numberOfItems = 1)
      val payloadChargeD = payloadChargeTypeD(numberOfItems = 1)
      val payloadChargeE = payloadChargeTypeE(numberOfItems = 1)
      val payloadChargeG = payloadChargeTypeG(numberOfItems = 1)
      val payload = payloadHeader ++
        payloadChargeTypeA ++ payloadChargeTypeB ++ payloadChargeTypeF ++
        payloadChargeC ++ payloadChargeD ++ payloadChargeE ++ payloadChargeG
      batchService.split(payload, batchSize) mustBe Seq(
        BatchInfo(BatchType.Other, 1,
          payloadHeader ++
            payloadChargeTypeA ++ payloadChargeTypeB ++ payloadChargeTypeF ++
            concatenateNodes(Seq(payloadChargeTypeCMinusEmployers(numberOfItems = 1)), nodeNameChargeC) ++
            concatenateNodes(Seq(payloadChargeTypeDMinusMembers(numberOfItems = 1)), nodeNameChargeD) ++
            concatenateNodes(Seq(payloadChargeTypeEMinusMembers(numberOfItems = 1)), nodeNameChargeE) ++
            concatenateNodes(Seq(payloadChargeTypeGMinusMembers(numberOfItems = 1)), nodeNameChargeG)
        ),
        BatchInfo(BatchType.ChargeC, 1, payloadChargeTypeCEmployer(numberOfItems = 1)),
        BatchInfo(BatchType.ChargeD, 1, payloadChargeTypeDMember(numberOfItems = 1)),
        BatchInfo(BatchType.ChargeE, 1, payloadChargeTypeEMember(numberOfItems = 1)),
        BatchInfo(BatchType.ChargeG, 1, payloadChargeTypeGMember(numberOfItems = 1))
      )
    }
  }
}

object BatchServiceSpec {

  private val batchSize = 2
  private val batchService = new BatchService

  private def intToString(c:Int):String = ('A' + c - 1).toChar.toString

  private def concatenateNodes(nodes: Seq[JsObject], nodeName:String): JsObject = {
    val allNodes = nodes.foldLeft(Json.obj()) { (a,b) => a ++ b }
    Json.obj(
      nodeName -> allNodes
    )
  }

  private val nodeNameChargeC = "chargeCDetails"
  private val nodeNameChargeD = "chargeDDetails"
  private val nodeNameChargeE = "chargeEDetails"
  private val nodeNameChargeG = "chargeGDetails"

  private val payloadHeader = {
    Json.obj(
      "schemeStatus" -> "Open",
      "loggedInPersonEmail" -> "nigel@test.com",
      "loggedInPersonName" -> "Nigel Robert Smith",
      "minimalFlags" -> Json.obj(
        "deceasedFlag" -> false,
        "rlsFlag" -> false
      ),
      "quarter" -> Json.obj(
       "startDate" -> "2020-04-01",
       "endDate" -> "2020-06-30"
      ),
      "aftStatus" -> "Compiled",
      "schemeName" -> "Open Single Trust Scheme with Indiv Establisher and Trustees",
      "pstr" -> "24000001IN",
      "chargeType" -> "lumpSumDeath"
    )
  }

  private val payloadChargeTypeA = {
    Json.parse("""
                 | {
                 |  "chargeADetails" : {
                 |   "chargeDetails" : {
                 |      "numberOfMembers" : 365,
                 |      "totalAmtOfTaxDueAtLowerRate" : 983.67,
                 |      "totalAmtOfTaxDueAtHigherRate" : 12.34,
                 |      "totalAmount" : 996.01
                 |    }
                 |  }
                 | }
                 |""".stripMargin).as[JsObject]
  }

  private val payloadChargeTypeB:JsObject = Json.parse("""
                | {
                |  "chargeBDetails" : {
                |   "chargeDetails" : {
                |    "numberOfDeceased" : 74,
                |    "totalAmount" : 47.39
                |   }
                |  }
                | }
                |""".stripMargin).as[JsObject]

  private def payloadChargeTypeCEmployer(numberOfItems:Int):JsArray = {
    JsArray(
      (1 to numberOfItems) map { item =>
        Json.parse(s"""
                     | {
                     |  "whichTypeOfSponsoringEmployer" : "organisation",
                     |  "sponsoringOrganisationDetails" : {
                     |   "name" : "ACME ${intToString(item)} Ltd",
                     |   "crn" : "AB123456"
                     |  },
                     |  "sponsoringEmployerAddress" : {
                     |   "line1" : "10 Other Place",
                     |   "line2" : "Some District",
                     |   "line3" : "Anytown",
                     |   "country" : "GB",
                     |   "postcode" : "ZZ1 1ZZ"
                     |  },
                     |  "chargeDetails" : {
                     |   "paymentDate" : "2020-04-01",
                     |   "amountTaxDue" : 33.00
                     |  }
                     | }
                     |""".stripMargin).as[JsObject]
      }
    )
  }

  private def payloadChargeTypeC(numberOfItems:Int):JsObject = {
    val employersNode = if (numberOfItems > 0) {
      Json.obj(
        "employers" -> payloadChargeTypeCEmployer(numberOfItems)
      )
    } else {
      Json.obj()
    }
    concatenateNodes(Seq(payloadChargeTypeCMinusEmployers(numberOfItems), employersNode), nodeNameChargeC)
  }

  private def payloadChargeTypeCMinusEmployers(numberOfItems:Int):JsObject = {
    Json.obj(
      "totalChargeAmount" -> 33 * numberOfItems,
      "addEmployers" -> false
    )
  }

  private def payloadChargeTypeDMember(numberOfItems:Int):JsArray = {
    JsArray(
      (1 to numberOfItems) map { item =>
        Json.parse(s"""
                     | {
                     |  "memberDetails": {
                     |   "firstName" : "James ${intToString(item)}",
                     |   "lastName" : "Hughes",
                     |   "nino" : "CS454545C"
                     |  },
                     |  "chargeDetails" : {
                     |   "dateOfEvent" : "2020-04-01",
                     |   "taxAt25Percent" : 36.55,
                     |   "taxAt55Percent" : 13.45
                     |  }
                     | }
                     |""".stripMargin).as[JsObject]
      }
    )
  }

  private def payloadChargeTypeD(numberOfItems:Int):JsObject = {
    val membersNode = if (numberOfItems > 0) {
      Json.obj(
        "members" -> payloadChargeTypeDMember(numberOfItems)
      )
    } else {
      Json.obj()
    }
    concatenateNodes(Seq(payloadChargeTypeDMinusMembers(numberOfItems), membersNode), nodeNameChargeD)
  }

  private def payloadChargeTypeDMinusMembers(numberOfItems:Int):JsObject = {
    Json.obj(
      "totalChargeAmount" -> 50 * numberOfItems,
      "addMembers" -> false
    )
  }

  private def payloadChargeTypeEMember(numberOfItems:Int):JsArray = {
    JsArray(
      (1 to numberOfItems) map { item =>
        Json.parse(s"""
                     | {
                     |  "memberDetails": {
                     |   "firstName" : "Jack ${intToString(item)}",
                     |   "lastName" : "Spratt",
                     |   "nino" : "CS121212C"
                     |  },
                     |  "annualAllowanceYear" : "2020",
                     |  "chargeDetails" : {
                     |   "chargeAmount" : 100.00,
                     |   "dateNoticeReceived" : "2020-01-01",
                     |   "isPaymentMandatory" : true
                     |  }
                     | }
                     |""".stripMargin).as[JsObject]
      }
    )
  }

  private def payloadChargeTypeE(numberOfItems:Int):JsObject = {
    val membersNode = if (numberOfItems > 0) {
      Json.obj(
        "members" -> payloadChargeTypeEMember(numberOfItems)
      )
    } else {
      Json.obj()
    }
    concatenateNodes(Seq(payloadChargeTypeEMinusMembers(numberOfItems), membersNode), nodeNameChargeE)
  }

  private def payloadChargeTypeEMinusMembers(numberOfItems:Int):JsObject = {
    Json.obj(
      "totalChargeAmount" -> 100 * numberOfItems,
      "addMembers" -> false
    )
  }

  private val payloadChargeTypeF:JsObject = Json.parse("""
                | {
                |  "chargeFDetails" : {
                |   "chargeDetails" : {
                |    "deRegistrationDate" : "2020-04-01",
                |    "totalAmount" : 40.12
                |   }
                |  }
                | }
                |""".stripMargin).as[JsObject]

  private def payloadChargeTypeGMember(numberOfItems:Int):JsArray = {
    JsArray(
      (1 to numberOfItems) map { item =>
        Json.parse(s"""
                     | {
                     |  "memberDetails": {
                     |   "firstName" : "Sarah ${intToString(item)}",
                     |   "lastName" : "Wabe",
                     |   "dob" : "2020-05-12",
                     |   "nino" : "CS787878C"
                     |  },
                     |  "chargeDetails" : {
                     |   "qropsReferenceNumber" : "121212",
                     |   "qropsTransferDate" : "2020-06-01"
                     |  },
                     |  "chargeAmounts" : {
                     |   "amountTransferred" : 12.56,
                     |   "amountTaxDue" : 90.00
                     |  }
                     | }
                     |""".stripMargin).as[JsObject]
      }
    )
  }

  private def payloadChargeTypeG(numberOfItems:Int):JsObject = {
    val membersNode = if (numberOfItems > 0) {
      Json.obj(
        "members" -> payloadChargeTypeGMember(numberOfItems)
      )
    } else {
      Json.obj()
    }
    concatenateNodes(Seq(payloadChargeTypeGMinusMembers(numberOfItems), membersNode), nodeNameChargeG)
  }

  private def payloadChargeTypeGMinusMembers(numberOfItems:Int):JsObject = {
    Json.obj(
      "totalChargeAmount" -> 90 * numberOfItems,
      "addMembers" -> false
    )
  }

  private val fullPayload = payloadHeader ++ payloadChargeTypeA ++ payloadChargeTypeB ++ payloadChargeTypeC(2) ++
    payloadChargeTypeD(2) ++ payloadChargeTypeE(2) ++ payloadChargeTypeF ++ payloadChargeTypeG(2)
}

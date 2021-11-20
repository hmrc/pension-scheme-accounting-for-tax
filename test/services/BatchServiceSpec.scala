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
import services.BatchService.BatchType.SessionData
import services.BatchService.{BatchInfo, BatchType}

class BatchServiceSpec extends AnyWordSpec with Matchers {
  // scalastyle:off magic.number
  import BatchServiceSpec._
  "createBatches (with batch size set to 2)" must {

    "return correct batch info with empty userDataFullPayload" in {
      val payload = Json.obj()
      batchService.createBatches(payload, batchSize) mustBe Set(
        BatchInfo(BatchType.Other, 1, payload)
      )
    }

    "return correct batch info with no charges at all" in {
      val payload = payloadOther
      batchService.createBatches(payload, batchSize) mustBe Set(
        BatchInfo(BatchType.Other, 1, payload)
      )
    }

    "return correct batch info with no member-based charges" in {
      val payload = payloadOther ++ payloadChargeTypeA
      batchService.createBatches(payload, batchSize) mustBe Set(
        BatchInfo(BatchType.Other, 1, payload)
      )
    }

    "return correct batch info with no member-based charges plus a session data payload" in {
      val payload = payloadOther ++ payloadChargeTypeA
      val sessionDataPayload = Json.obj("test" -> "test")
      batchService.createBatches(payload, batchSize, Some(sessionDataPayload)) mustBe Set(
        BatchInfo(BatchType.Other, 1, payload),
        BatchInfo(BatchType.SessionData, 1, sessionDataPayload)
      )
    }

    "return correct batch info with one scheme-based charge (A) and one member-based charge (C)" in {
      val payloadChargeC = payloadChargeTypeC(numberOfItems = 1)
      val payload = payloadOther ++ payloadChargeTypeA ++ payloadChargeC
      batchService.createBatches(payload, batchSize) mustBe Set(
        BatchInfo(BatchType.Other, 1,
          payloadOther ++
            payloadChargeTypeA ++
            concatenateNodes(Seq(payloadChargeTypeCMinusEmployers(numberOfItems = 1)), nodeNameChargeC)
        ),
        BatchInfo(BatchType.ChargeC, 1, payloadChargeTypeCEmployer(numberOfItems = 1))
      )
    }

    "return correct batch info with one scheme-based charge (B) and one member-based charge (D)" in {
      val payloadChargeD = payloadChargeTypeD(numberOfItems = 1)
      val payload = payloadOther ++ payloadChargeTypeB ++ payloadChargeD
      batchService.createBatches(payload, batchSize) mustBe Set(
        BatchInfo(BatchType.Other, 1,
          payloadOther ++
            payloadChargeTypeB ++
            concatenateNodes(Seq(payloadChargeTypeDMinusMembers(numberOfItems = 1)), nodeNameChargeD)
        ),
        BatchInfo(BatchType.ChargeD, 1, payloadChargeTypeDMember(numberOfItems = 1))
      )
    }

    "return correct batch info with one scheme-based charge (F) and one member-based charge (E)" in {
      val payloadChargeE = payloadChargeTypeE(numberOfItems = 1)
      val payload = payloadOther ++ payloadChargeTypeF ++ payloadChargeE
      batchService.createBatches(payload, batchSize) mustBe Set(
        BatchInfo(BatchType.Other, 1,
          payloadOther ++
            payloadChargeTypeF ++
            concatenateNodes(Seq(payloadChargeTypeEMinusMembers(numberOfItems = 1)), nodeNameChargeE)
        ),
        BatchInfo(BatchType.ChargeE, 1, payloadChargeTypeEMember(numberOfItems = 1))
      )
    }

    "return correct batch info with one scheme-based charge (A) and one member-based charge (G)" in {
      val payloadChargeG = payloadChargeTypeG(numberOfItems = 1)
      val payload = payloadOther ++ payloadChargeTypeA ++ payloadChargeG
      batchService.createBatches(payload, batchSize) mustBe Set(
        BatchInfo(BatchType.Other, 1,
          payloadOther ++
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
      val payload = payloadOther ++
        payloadChargeTypeA ++ payloadChargeTypeB ++ payloadChargeTypeF ++
        payloadChargeC ++ payloadChargeD ++ payloadChargeE ++ payloadChargeG
      batchService.createBatches(payload, batchSize) mustBe Set(
        BatchInfo(BatchType.Other, 1,
          payloadOther ++
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

    "return correct batch info with one scheme-based charge (A) and three member-based charges (C)" in {
      val payloadChargeC = payloadChargeTypeC(numberOfItems = 3)
      val jsArray = payloadChargeTypeCEmployer(numberOfItems = 3)
      val payload = payloadOther ++ payloadChargeTypeA ++ payloadChargeC
      batchService.createBatches(payload, batchSize) mustBe Set(
        BatchInfo(BatchType.Other, 1,
          payloadOther ++
            payloadChargeTypeA ++
            concatenateNodes(Seq(payloadChargeTypeCMinusEmployers(numberOfItems = 3)), nodeNameChargeC)
        ),
        BatchInfo(BatchType.ChargeC, 1, JsArray( Seq(jsArray(0), jsArray(1)))),
        BatchInfo(BatchType.ChargeC, 2, JsArray( Seq(jsArray(2))))
      )
    }

    "return correct batch info with one scheme-based charge (A) and three member-based charges (D)" in {
      val payloadChargeD = payloadChargeTypeD(numberOfItems = 3)
      val jsArray = payloadChargeTypeDMember(numberOfItems = 3)
      val payload = payloadOther ++ payloadChargeTypeA ++ payloadChargeD
      batchService.createBatches(payload, batchSize) mustBe Set(
        BatchInfo(BatchType.Other, 1,
          payloadOther ++
            payloadChargeTypeA ++
            concatenateNodes(Seq(payloadChargeTypeDMinusMembers(numberOfItems = 3)), nodeNameChargeD)
        ),
        BatchInfo(BatchType.ChargeD, 1, JsArray( Seq(jsArray(0), jsArray(1)))),
        BatchInfo(BatchType.ChargeD, 2, JsArray( Seq(jsArray(2))))
      )
    }

    "return correct batch info with one scheme-based charge (A) and three member-based charges (E)" in {
      val payloadChargeE = payloadChargeTypeE(numberOfItems = 3)
      val jsArray = payloadChargeTypeEMember(numberOfItems = 3)
      val payload = payloadOther ++ payloadChargeTypeA ++ payloadChargeE
      batchService.createBatches(payload, batchSize) mustBe Set(
        BatchInfo(BatchType.Other, 1,
          payloadOther ++
            payloadChargeTypeA ++
            concatenateNodes(Seq(payloadChargeTypeEMinusMembers(numberOfItems = 3)), nodeNameChargeE)
        ),
        BatchInfo(BatchType.ChargeE, 1, JsArray( Seq(jsArray(0), jsArray(1)))),
        BatchInfo(BatchType.ChargeE, 2, JsArray( Seq(jsArray(2))))
      )
    }

    "return correct batch info with one scheme-based charge (A) and three member-based charges (G)" in {
      val payloadChargeG = payloadChargeTypeG(numberOfItems = 3)
      val jsArray = payloadChargeTypeGMember(numberOfItems = 3)
      val payload = payloadOther ++ payloadChargeTypeA ++ payloadChargeG
      batchService.createBatches(payload, batchSize) mustBe Set(
        BatchInfo(BatchType.Other, 1,
          payloadOther ++
            payloadChargeTypeA ++
            concatenateNodes(Seq(payloadChargeTypeGMinusMembers(numberOfItems = 3)), nodeNameChargeG)
        ),
        BatchInfo(BatchType.ChargeG, 1, JsArray( Seq(jsArray(0), jsArray(1)))),
        BatchInfo(BatchType.ChargeG, 2, JsArray( Seq(jsArray(2))))
      )
    }

    "return correct batch info with all three scheme-based charges and various nos of items in all four member-based charges" in {
      val payloadChargeC = payloadChargeTypeC(numberOfItems = 5)
      val payloadChargeD = payloadChargeTypeD(numberOfItems = 4)
      val payloadChargeE = payloadChargeTypeE(numberOfItems = 2)
      val payloadChargeG = payloadChargeTypeG(numberOfItems = 7)

      val jsArrayChargeC = payloadChargeTypeCEmployer(numberOfItems = 5)
      val jsArrayChargeD = payloadChargeTypeDMember(numberOfItems = 4)
      val jsArrayChargeE = payloadChargeTypeEMember(numberOfItems = 2)
      val jsArrayChargeG = payloadChargeTypeGMember(numberOfItems = 7)

      val payload = payloadOther ++
        payloadChargeTypeA ++ payloadChargeTypeB ++ payloadChargeTypeF ++
        payloadChargeC ++ payloadChargeD ++ payloadChargeE ++ payloadChargeG
      batchService.createBatches(payload, batchSize) mustBe Set(
        BatchInfo(BatchType.Other, 1,
          payloadOther ++
            payloadChargeTypeA ++ payloadChargeTypeB ++ payloadChargeTypeF ++
            concatenateNodes(Seq(payloadChargeTypeCMinusEmployers(numberOfItems = 5)), nodeNameChargeC) ++
            concatenateNodes(Seq(payloadChargeTypeDMinusMembers(numberOfItems = 4)), nodeNameChargeD) ++
            concatenateNodes(Seq(payloadChargeTypeEMinusMembers(numberOfItems = 2)), nodeNameChargeE) ++
            concatenateNodes(Seq(payloadChargeTypeGMinusMembers(numberOfItems = 7)), nodeNameChargeG)
        ),
        BatchInfo(BatchType.ChargeC, 1, JsArray( Seq(jsArrayChargeC(0), jsArrayChargeC(1)))),
        BatchInfo(BatchType.ChargeC, 2, JsArray( Seq(jsArrayChargeC(2), jsArrayChargeC(3)))),
        BatchInfo(BatchType.ChargeC, 3, JsArray( Seq(jsArrayChargeC(4)))),

        BatchInfo(BatchType.ChargeD, 1, JsArray( Seq(jsArrayChargeD(0), jsArrayChargeD(1)))),
        BatchInfo(BatchType.ChargeD, 2, JsArray( Seq(jsArrayChargeD(2), jsArrayChargeD(3)))),

        BatchInfo(BatchType.ChargeE, 1, JsArray( Seq(jsArrayChargeE(0), jsArrayChargeE(1)))),

        BatchInfo(BatchType.ChargeG, 1, JsArray( Seq(jsArrayChargeG(0), jsArrayChargeG(1)))),
        BatchInfo(BatchType.ChargeG, 2, JsArray( Seq(jsArrayChargeG(2), jsArrayChargeG(3)))),
        BatchInfo(BatchType.ChargeG, 3, JsArray( Seq(jsArrayChargeG(4), jsArrayChargeG(5)))),
        BatchInfo(BatchType.ChargeG, 4, JsArray( Seq(jsArrayChargeG(6))))
      )
    }
  }

  "createUserDataFullPayload" must {
    "return empty json if there are no batches" in {
      batchService.createUserDataFullPayload(Nil) mustBe Json.obj()
    }

    "return empty json if there is only an empty 'other' batch" in {
      val batches = Seq(BatchInfo(BatchType.Other, 1, Json.obj()))
      batchService.createUserDataFullPayload(batches) mustBe Json.obj()
    }

    "return correct json if there is only an 'other' batch with some values" in {
      val batches = Seq(BatchInfo(BatchType.Other, 1, payloadOther))
      batchService.createUserDataFullPayload(batches) mustBe payloadOther
    }

    "return correct json if there is an 'other' batch with some values and a scheme-based charge (A) and " +
      "two batches of one member-based charge (C)" in {
      val jsArray = payloadChargeTypeCEmployer(numberOfItems = 3)

      val batches = Seq(
        BatchInfo(BatchType.Other, 1,
          payloadOther ++ payloadChargeTypeA ++ concatenateNodes(Seq(payloadChargeTypeCMinusEmployers(3)), nodeNameChargeC)),
        BatchInfo(BatchType.ChargeC, 1, JsArray(Seq(jsArray(0), jsArray(1)))),
        BatchInfo(BatchType.ChargeC, 2, JsArray(Seq(jsArray(2))))
      )
      val expectedPayload = payloadOther ++ payloadChargeTypeA ++ payloadChargeTypeC(numberOfItems = 3)
      batchService.createUserDataFullPayload(batches) mustBe expectedPayload
    }

    "return correct json if there is an 'other' batch with some values and a scheme-based charge (A) and " +
      "two batches of one member-based charge (D)" in {
      val jsArray = payloadChargeTypeDMember(numberOfItems = 3)

      val batches = Seq(
        BatchInfo(BatchType.Other, 1,
          payloadOther ++ payloadChargeTypeA ++ concatenateNodes(Seq(payloadChargeTypeDMinusMembers(3)), nodeNameChargeD)),
        BatchInfo(BatchType.ChargeD, 1, JsArray(Seq(jsArray(0), jsArray(1)))),
        BatchInfo(BatchType.ChargeD, 2, JsArray(Seq(jsArray(2))))
      )
      val expectedPayload = payloadOther ++ payloadChargeTypeA ++ payloadChargeTypeD(numberOfItems = 3)
      batchService.createUserDataFullPayload(batches) mustBe expectedPayload
    }

    "return correct json if there is an 'other' batch with some values and a scheme-based charge (A) and " +
      "two batches of one member-based charge (E)" in {
      val jsArray = payloadChargeTypeEMember(numberOfItems = 3)

      val batches = Seq(
        BatchInfo(BatchType.Other, 1,
          payloadOther ++ payloadChargeTypeA ++ concatenateNodes(Seq(payloadChargeTypeEMinusMembers(3)), nodeNameChargeE)),
        BatchInfo(BatchType.ChargeE, 1, JsArray(Seq(jsArray(0), jsArray(1)))),
        BatchInfo(BatchType.ChargeE, 2, JsArray(Seq(jsArray(2))))
      )
      val expectedPayload = payloadOther ++ payloadChargeTypeA ++ payloadChargeTypeE(numberOfItems = 3)
      batchService.createUserDataFullPayload(batches) mustBe expectedPayload
    }

    "return correct json if there is an 'other' batch with some values and a scheme-based charge (A) and " +
      "two batches of one member-based charge (G)" in {
      val jsArray = payloadChargeTypeGMember(numberOfItems = 3)

      val batches = Seq(
        BatchInfo(BatchType.Other, 1,
          payloadOther ++ payloadChargeTypeA ++ concatenateNodes(Seq(payloadChargeTypeGMinusMembers(3)), nodeNameChargeG)),
        BatchInfo(BatchType.ChargeG, 1, JsArray(Seq(jsArray(0), jsArray(1)))),
        BatchInfo(BatchType.ChargeG, 2, JsArray(Seq(jsArray(2))))
      )
      val expectedPayload = payloadOther ++ payloadChargeTypeA ++ payloadChargeTypeG(numberOfItems = 3)
      batchService.createUserDataFullPayload(batches) mustBe expectedPayload
    }

    "return correct json if there is an 'other' batch with some values and a scheme-based charge (A) and " +
      "different numbers of batches of each member-based charge and one batch out of order" in {
      val jsArrayC = payloadChargeTypeCEmployer(numberOfItems = 2)
      val jsArrayD = payloadChargeTypeDMember(numberOfItems = 3)
      val jsArrayE = payloadChargeTypeEMember(numberOfItems = 1)
      val jsArrayG = payloadChargeTypeGMember(numberOfItems = 5)

      val batches = Seq(
        BatchInfo(BatchType.Other, 1,
          payloadOther ++ payloadChargeTypeA ++
            concatenateNodes(Seq(payloadChargeTypeCMinusEmployers(2)), nodeNameChargeC) ++
            concatenateNodes(Seq(payloadChargeTypeDMinusMembers(3)), nodeNameChargeD) ++
            concatenateNodes(Seq(payloadChargeTypeEMinusMembers(1)), nodeNameChargeE) ++
            concatenateNodes(Seq(payloadChargeTypeGMinusMembers(5)), nodeNameChargeG)
        ),
        BatchInfo(BatchType.ChargeC, 1, JsArray(Seq(jsArrayC(0), jsArrayC(1)))),
        BatchInfo(BatchType.ChargeD, 2, JsArray(Seq(jsArrayD(2)))),
        BatchInfo(BatchType.ChargeD, 1, JsArray(Seq(jsArrayD(0), jsArrayD(1)))),
        BatchInfo(BatchType.ChargeE, 1, JsArray(Seq(jsArrayE(0)))),
        BatchInfo(BatchType.ChargeG, 1, JsArray(Seq(jsArrayG(0), jsArrayG(1)))),
        BatchInfo(BatchType.ChargeG, 2, JsArray(Seq(jsArrayG(2), jsArrayG(3)))),
        BatchInfo(BatchType.ChargeG, 3, JsArray(Seq(jsArrayG(4))))
      )
      val expectedPayload = payloadOther ++ payloadChargeTypeA ++
        payloadChargeTypeC(numberOfItems = 2) ++
        payloadChargeTypeD(numberOfItems = 3) ++
        payloadChargeTypeE(numberOfItems = 1) ++
        payloadChargeTypeG(numberOfItems = 5)
      batchService.createUserDataFullPayload(batches) mustBe expectedPayload
    }
  }

  "createSessionDataPayload" must {
    "return none where no session data batch is present" in {
      batchService.createSessionDataPayload(Nil) mustBe None
    }

    "return the session data payload where present" in {
      val sessionDataJsobject = Json.obj("test" -> "test")
      val batches = Seq(
        BatchInfo(SessionData, 1, sessionDataJsobject)
      )
      batchService.createSessionDataPayload(batches) mustBe Some(sessionDataJsobject)
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

  private val payloadOther = {
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
}

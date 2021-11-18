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
import play.api.libs.json.{JsObject, Json, JsArray}

class BatchServiceSpec extends AnyWordSpec with Matchers {
  import BatchServiceSpec._
  "a" must {
    "a" in {

      val xx = payloadHeader ++ payloadChargeTypeA ++ payloadChargeTypeB ++ payloadChargeTypeC(2) ++
        payloadChargeTypeE(2) ++ payloadChargeTypeF ++ payloadChargeTypeG(2)

      println( "\n>>" + xx)
      true mustBe true
    }
  }

}

object BatchServiceSpec {

  private def intToString(c:Int):String = ('A' + c - 1).toChar.toString

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
                     |   "amountTaxDue" : 50.45
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
    val chargeNode = Json.obj(
      "totalChargeAmount" -> 50.45,
      "addEmployers" -> false
    )
    Json.obj(
      "chargeCDetails" -> (chargeNode ++ employersNode)
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
                     |   "taxAt55Percent" : 12.2
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
    val chargeNode = Json.obj(
      "totalChargeAmount" -> 48.75,
      "addMembers" -> false
    )
    Json.obj(
      "chargeDDetails" -> (chargeNode ++ membersNode)
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
                     |  "chargeDetails" : {
                     |   "chargeAmount" : 100.25,
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
    val chargeNode = Json.obj(
      "totalChargeAmount" -> 100.25,
      "addMembers" -> false
    )
    Json.obj(
      "chargeDDetails" -> (chargeNode ++ membersNode)
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
                     |   "amountTaxDue" : 89.34
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
    val chargeNode = Json.obj(
      "totalChargeAmount" -> 89.34,
      "addMembers" -> false
    )
    Json.obj(
      "chargeGDetails" -> (chargeNode ++ membersNode)
    )
  }
}

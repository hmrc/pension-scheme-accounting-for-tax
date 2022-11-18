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

package models

import play.api.libs.json.{JsArray, JsObject, Json}

object BatchedRepositorySampleData {

  val nodeNameChargeC = "chargeCDetails"
  val nodeNameChargeD = "chargeDDetails"
  val nodeNameChargeE = "chargeEDetails"
  val nodeNameChargeG = "chargeGDetails"

  def intToString(c: Int): String = ('A' + c - 1).toChar.toString

  def concatenateNodes(nodes: Seq[JsObject], nodeName: String): JsObject = {
    val allNodes = nodes.foldLeft(Json.obj()) { (a, b) => a ++ b }
    Json.obj(
      nodeName -> allNodes
    )
  }

  val payloadOther: JsObject = {
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

  val payloadChargeTypeA: JsObject = {
    Json.parse(
      """
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

  val payloadChargeTypeB: JsObject = Json.parse(
    """
      | {
      |  "chargeBDetails" : {
      |   "chargeDetails" : {
      |    "numberOfDeceased" : 74,
      |    "totalAmount" : 47.39
      |   }
      |  }
      | }
      |""".stripMargin).as[JsObject]

  def payloadChargeTypeCEmployer(numberOfItems: Int, employerName: String = "ACME"): JsArray = {
    JsArray(
      (1 to numberOfItems) map { item =>
        Json.parse(
          s"""
             | {
             |  "whichTypeOfSponsoringEmployer" : "organisation",
             |  "sponsoringOrganisationDetails" : {
             |   "name" : "$employerName ${intToString(item)} Ltd",
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

  def payloadChargeTypeC(numberOfItems: Int): JsObject = {
    val employersNode = if (numberOfItems > 0) {
      Json.obj(
        "employers" -> payloadChargeTypeCEmployer(numberOfItems)
      )
    } else {
      Json.obj()
    }
    concatenateNodes(Seq(payloadChargeTypeCMinusEmployers(numberOfItems), employersNode), nodeNameChargeC)
  }

  def payloadChargeTypeCMinusEmployers(numberOfItems: Int): JsObject = {
    Json.obj(
      "totalChargeAmount" -> 33 * numberOfItems,
      "addEmployers" -> false
    )
  }

  def payloadChargeTypeDMember(numberOfItems: Int): JsArray = {
    JsArray(
      (1 to numberOfItems) map { item =>
        Json.parse(
          s"""
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

  def payloadChargeTypeD(numberOfItems: Int): JsObject = {
    val membersNode = if (numberOfItems > 0) {
      Json.obj(
        "members" -> payloadChargeTypeDMember(numberOfItems)
      )
    } else {
      Json.obj()
    }
    concatenateNodes(Seq(payloadChargeTypeDMinusMembers(numberOfItems), membersNode), nodeNameChargeD)
  }

  def payloadChargeTypeDMinusMembers(numberOfItems: Int): JsObject = {
    Json.obj(
      "totalChargeAmount" -> 50 * numberOfItems,
      "addMembers" -> false
    )
  }

  def payloadChargeTypeEMember(numberOfItems: Int): JsArray = {
    JsArray(
      (1 to numberOfItems) map { item =>
        Json.parse(
          s"""
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

  def payloadChargeTypeCMemberMinimal: JsArray = {
    JsArray(
      Seq(
        Json.parse(
          s"""
             | {
             |  "whichTypeOfSponsoringEmployer" : "organisation",
             |  "sponsoringOrganisationDetails" : {
             |   "name" : "ACME Ltd",
             |   "crn" : "AB123456"
             |  },
             |  "sponsoringEmployerAddress" : {
             |   "line1" : "10 Other Place",
             |   "line2" : "Some District",
             |   "line3" : "Anytown",
             |   "country" : "GB",
             |   "postcode" : "ZZ1 1ZZ"
             |  }
             | }
             |""".stripMargin).as[JsObject]
      )
    )
  }

  def payloadChargeTypeEMemberMinimal: JsArray = {
    JsArray(
      Seq(
        Json.parse(
          s"""
             | {
             |  "memberDetails": {
             |   "firstName" : "Jack",
             |   "lastName" : "Spratt",
             |   "nino" : "CS121212C"
             |  }
             | }
             |""".stripMargin).as[JsObject]
      )
    )
  }

  def payloadChargeTypeE(numberOfItems: Int): JsObject = {
    val membersNode = if (numberOfItems > 0) {
      Json.obj(
        "members" -> payloadChargeTypeEMember(numberOfItems)
      )
    } else {
      Json.obj()
    }
    concatenateNodes(Seq(payloadChargeTypeEMinusMembers(numberOfItems), membersNode), nodeNameChargeE)
  }

  def payloadChargeTypeEMinusMembers(numberOfItems: Int): JsObject = {
    Json.obj(
      "totalChargeAmount" -> 100 * numberOfItems,
      "addMembers" -> false
    )
  }

  val payloadChargeTypeF: JsObject = Json.parse(
    """
      | {
      |  "chargeFDetails" : {
      |   "chargeDetails" : {
      |    "deRegistrationDate" : "2020-04-01",
      |    "totalAmount" : 40.12
      |   }
      |  }
      | }
      |""".stripMargin).as[JsObject]

  def payloadChargeTypeGMember(numberOfItems: Int): JsArray = {
    JsArray(
      (1 to numberOfItems) map { item =>
        Json.parse(
          s"""
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

  def payloadChargeTypeG(numberOfItems: Int): JsObject = {
    val membersNode = if (numberOfItems > 0) {
      Json.obj(
        "members" -> payloadChargeTypeGMember(numberOfItems)
      )
    } else {
      Json.obj()
    }
    concatenateNodes(Seq(payloadChargeTypeGMinusMembers(numberOfItems), membersNode), nodeNameChargeG)
  }

  def payloadChargeTypeGMinusMembers(numberOfItems: Int): JsObject = {
    Json.obj(
      "totalChargeAmount" -> 90 * numberOfItems,
      "addMembers" -> false
    )
  }
}

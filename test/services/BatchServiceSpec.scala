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

package services

import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.{JsArray, Json}
import services.BatchService.{BatchIdentifier, BatchInfo, BatchType}

class BatchServiceSpec extends AnyWordSpec with Matchers {
  // scalastyle:off magic.number

  import BatchServiceSpec._
  import models.BatchedRepositorySampleData._

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
        BatchInfo(BatchType.ChargeC, 1, JsArray(Seq(jsArray(0), jsArray(1)))),
        BatchInfo(BatchType.ChargeC, 2, JsArray(Seq(jsArray(2))))
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
        BatchInfo(BatchType.ChargeD, 1, JsArray(Seq(jsArray(0), jsArray(1)))),
        BatchInfo(BatchType.ChargeD, 2, JsArray(Seq(jsArray(2))))
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
        BatchInfo(BatchType.ChargeE, 1, JsArray(Seq(jsArray(0), jsArray(1)))),
        BatchInfo(BatchType.ChargeE, 2, JsArray(Seq(jsArray(2))))
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
        BatchInfo(BatchType.ChargeG, 1, JsArray(Seq(jsArray(0), jsArray(1)))),
        BatchInfo(BatchType.ChargeG, 2, JsArray(Seq(jsArray(2))))
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
        BatchInfo(BatchType.ChargeC, 1, JsArray(Seq(jsArrayChargeC(0), jsArrayChargeC(1)))),
        BatchInfo(BatchType.ChargeC, 2, JsArray(Seq(jsArrayChargeC(2), jsArrayChargeC(3)))),
        BatchInfo(BatchType.ChargeC, 3, JsArray(Seq(jsArrayChargeC(4)))),

        BatchInfo(BatchType.ChargeD, 1, JsArray(Seq(jsArrayChargeD(0), jsArrayChargeD(1)))),
        BatchInfo(BatchType.ChargeD, 2, JsArray(Seq(jsArrayChargeD(2), jsArrayChargeD(3)))),

        BatchInfo(BatchType.ChargeE, 1, JsArray(Seq(jsArrayChargeE(0), jsArrayChargeE(1)))),

        BatchInfo(BatchType.ChargeG, 1, JsArray(Seq(jsArrayChargeG(0), jsArrayChargeG(1)))),
        BatchInfo(BatchType.ChargeG, 2, JsArray(Seq(jsArrayChargeG(2), jsArrayChargeG(3)))),
        BatchInfo(BatchType.ChargeG, 3, JsArray(Seq(jsArrayChargeG(4), jsArrayChargeG(5)))),
        BatchInfo(BatchType.ChargeG, 4, JsArray(Seq(jsArrayChargeG(6))))
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

    "return correct json if there is an 'other' batch with some values but no charge nodes and " +
      "one minimal batch of one member-based charge (C)" in {
      val batches = Seq(
        BatchInfo(BatchType.Other, 1, payloadOther),
        BatchInfo(BatchType.ChargeC, 1, payloadChargeTypeCMemberMinimal)
      )
      val expectedPayload = payloadOther ++ concatenateNodes(
        Seq(
          Json.obj(
            "employers" -> payloadChargeTypeCMemberMinimal
          )
        ), nodeNameChargeC
      )

      batchService.createUserDataFullPayload(batches) mustBe expectedPayload
    }

    "return correct json if there is an 'other' batch with some values but no charge nodes and " +
      "one minimal batch of one member-based charge (E)" in {
      val batches = Seq(
        BatchInfo(BatchType.Other, 1, payloadOther),
        BatchInfo(BatchType.ChargeE, 1, payloadChargeTypeEMemberMinimal)
      )
      val expectedPayload = payloadOther ++ concatenateNodes(
        Seq(
          Json.obj(
            "members" -> payloadChargeTypeEMemberMinimal
          )
        ), nodeNameChargeE
      )

      batchService.createUserDataFullPayload(batches) mustBe expectedPayload
    }
  }

  "lastBatchNo" must {
    "return the last batch number for each batch type" in {
      val dummyPayload = Json.obj()
      val dummyJsArray = JsArray()
      val batches = Set(
        BatchInfo(BatchType.Other, 1, dummyPayload),
        BatchInfo(BatchType.ChargeD, 2, dummyJsArray),
        BatchInfo(BatchType.ChargeD, 1, dummyJsArray),
        BatchInfo(BatchType.ChargeE, 1, dummyJsArray),
        BatchInfo(BatchType.ChargeG, 1, dummyJsArray),
        BatchInfo(BatchType.ChargeG, 2, dummyJsArray),
        BatchInfo(BatchType.ChargeG, 3, dummyJsArray)
      )
      batchService.lastBatchNo(batches) mustBe Set(
        BatchIdentifier(BatchType.ChargeC, 0),
        BatchIdentifier(BatchType.ChargeD, 2),
        BatchIdentifier(BatchType.ChargeE, 1),
        BatchIdentifier(BatchType.ChargeG, 3)
      )
    }
  }

}

object BatchServiceSpec {
  private val batchSize = 2
  private val batchService = new BatchService
}

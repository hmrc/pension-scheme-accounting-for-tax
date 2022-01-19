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

package repository

import play.modules.reactivemongo.ReactiveMongoComponent
import reactivemongo.bson.BSONDocument
import reactivemongo.play.json.ImplicitBSONHandlers._
import services.BatchService
import services.BatchService.{BatchIdentifier, BatchInfo, BatchType}
import com.github.simplyscala.MongoEmbedDatabase
import config.AppConfig
import models.BatchedRepositorySampleData._
import models.{ChargeAndMember, ChargeType, LockDetail}
import org.mockito.{ArgumentCaptor, MockitoSugar, ArgumentMatchers}
import org.scalatest.{BeforeAndAfter, BeforeAndAfterEach}
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.{JsObject, JsArray, Json, JsValue}
import uk.gov.hmrc.mongo.MongoConnector
import org.scalatest.concurrent.ScalaFutures.whenReady
import org.mockito.ArgumentMatchers._
import repository.model.SessionData
import services.BatchService.BatchType.{ChargeE, ChargeD, ChargeC, ChargeG}

import scala.concurrent.{Future, Await}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration

class AftBatchedDataCacheRepositorySpec
  extends AnyWordSpec with MockitoSugar with Matchers with MongoEmbedDatabase with BeforeAndAfter with
    BeforeAndAfterEach { // scalastyle:off magic.number

  import AftBatchedDataCacheRepositorySpec._

  override def beforeEach: Unit = {
    super.beforeEach
    when(mockAppConfig.mongoDBAFTBatchesUserDataBatchSize).thenReturn(2)
    when(mockAppConfig.mongoDBAFTBatchesMaxTTL).thenReturn(43200)
    when(mockAppConfig.mongoDBAFTBatchesTTL).thenReturn(999999)
    when(mockAppConfig.mongoDBAFTBatchesCollectionName).thenReturn(databaseName)
  }

  withEmbedMongoFixture(port = 24680) { _ =>
    "save" must {
      "save de-reg charge batch correctly in Mongo collection where there is some session data" in {
        mongoCollectionDrop()
        mongoCollectionInsertSessionDataBatch(id, sessionId, sessionData(sessionId))
        mongoCollectionInsertBatches(id, sessionId, fullSetOfBatchesToSaveToMongo.toSeq)

        val fullPayload = payloadOther ++ payloadChargeTypeA
        when(batchService.batchIdentifierForChargeAndMember(any(), any()))
          .thenReturn(Some(BatchIdentifier(BatchType.Other, 1)))
        when(batchService.getOtherJsObject(ArgumentMatchers.eq(fullPayload))).thenReturn(dummyJson)

        Await.result(aftBatchedDataCacheRepository
          .save(id, sessionId, Option(ChargeAndMember(ChargeType.ChargeTypeDeRegistration, None)), fullPayload),
          Duration.Inf) mustBe true

        whenReady(aftBatchedDataCacheRepository.find("uniqueAftId" -> uniqueAftId)) { documentsInDB =>
          documentsInDB.size mustBe 12
          val actualOtherBatch = dbDocumentsAsSeqBatchInfo(documentsInDB)
            .filter(filterOnBatchTypeAndNo(batchType = BatchType.Other, batchNo = 1))
          actualOtherBatch.nonEmpty mustBe true
          actualOtherBatch.foreach { _.jsValue mustBe dummyJson}
        }
      }

      "save member-based charge batch for a member correctly in Mongo collection where there is some session data" in {
        mongoCollectionDrop()
        mongoCollectionInsertSessionDataBatch(id, sessionId, sessionData(sessionId))
        mongoCollectionInsertBatches(id, sessionId, fullSetOfBatchesToSaveToMongo.toSeq)

        val fullPayload = payloadOther ++ payloadChargeTypeC(5)
        val amendedPayload = payloadChargeTypeCEmployer(numberOfItems = 5, employerName = "WIDGETS INC")
        val amendedBatchInfo = Some(BatchInfo(BatchType.ChargeC, 2, amendedPayload))
        when(batchService.batchIdentifierForChargeAndMember(any(), any()))
          .thenReturn(Some(BatchIdentifier(BatchType.ChargeC, 4)))
        when(batchService.getChargeTypeJsObjectForBatch(any(), any(), any(), any())).thenReturn(amendedBatchInfo)

        Await.result(aftBatchedDataCacheRepository
          .save(id, sessionId, Option(ChargeAndMember(ChargeType.ChargeTypeAuthSurplus, Some(4))), fullPayload),
          Duration.Inf) mustBe true
        whenReady(aftBatchedDataCacheRepository.find("uniqueAftId" -> uniqueAftId)) { documentsInDB =>
          documentsInDB.size mustBe 12
          val actualOtherBatch = dbDocumentsAsSeqBatchInfo(documentsInDB)
            .filter(filterOnBatchTypeAndNo(batchType = BatchType.ChargeC, batchNo = 2))
          actualOtherBatch.nonEmpty mustBe true
          actualOtherBatch.foreach { _.jsValue.as[JsArray] mustBe amendedPayload}
        }
      }

      "save batch for a new member where member added is first in new batch (batch 3 where currently only 2 batches)" in {
        mongoCollectionDrop()
        mongoCollectionInsertSessionDataBatch(id, sessionId, sessionData(sessionId))
        mongoCollectionInsertBatches(id, sessionId, setOfFourChargeCMembersInTwoBatches.toSeq)

        val amendedPayload = payloadChargeTypeCEmployer(numberOfItems = 1, employerName = "WIDGETS INC")
        val amendedBatchInfo = Some(BatchInfo(BatchType.ChargeC, 3, amendedPayload))
        when(batchService.batchIdentifierForChargeAndMember(any(), any()))
          .thenReturn(Some(BatchIdentifier(BatchType.ChargeC, 3)))
        when(batchService.getChargeTypeJsObjectForBatch(any(), any(), any(), any())).thenReturn(amendedBatchInfo)

        Await.result(aftBatchedDataCacheRepository
          .save(id, sessionId, Option(ChargeAndMember(ChargeType.ChargeTypeAuthSurplus, Some(5))),
            payloadOther ++ payloadChargeTypeC(5)), Duration.Inf) mustBe true
        whenReady(aftBatchedDataCacheRepository.find("uniqueAftId" -> uniqueAftId)) { documentsInDB =>
          documentsInDB.size mustBe 5
          val actualOtherBatch = dbDocumentsAsSeqBatchInfo(documentsInDB)
            .filter(filterOnBatchTypeAndNo(batchType = BatchType.ChargeC, batchNo = 3))
          actualOtherBatch.nonEmpty mustBe true
          actualOtherBatch.foreach { _.jsValue.as[JsArray] mustBe amendedPayload}
        }
      }

      "re-size batches when batch size changed" in {
        mongoCollectionDrop()
        mongoCollectionInsertSessionDataBatch(id, sessionId, sessionData(sessionId))
        mongoCollectionInsertBatches(id, sessionId, fullSetOfBatchesToSaveToMongo.toSeq)

        val biCaptor: ArgumentCaptor[Seq[BatchInfo]] = ArgumentCaptor.forClass(classOf[Seq[BatchInfo]])
        when(mockAppConfig.mongoDBAFTBatchesUserDataBatchSize).thenReturn(4)
        when(batchService.createUserDataFullPayload(biCaptor.capture())).thenReturn(dummyJson)
        when(batchService.createBatches(any(), any())).thenReturn(fullSetOfBatchesToSaveToMongo)
        val fullPayload = payloadOther ++ payloadChargeTypeA
        when(batchService.lastBatchNo(any())).thenReturn(Set())
        when(batchService.batchIdentifierForChargeAndMember(any(), any()))
          .thenReturn(Some(BatchIdentifier(BatchType.Other, 1)))
        when(batchService.getOtherJsObject(ArgumentMatchers.eq(fullPayload))).thenReturn(dummyJson)

        Await.result(aftBatchedDataCacheRepository
          .save(id, sessionId, Option(ChargeAndMember(ChargeType.ChargeTypeDeRegistration, None)), fullPayload),
          Duration.Inf) mustBe true
        biCaptor.getValue.size mustBe 11
        verify(batchService, times(1)).createUserDataFullPayload(any())
        verify(batchService, times(1)).createBatches(any(), any())
      }

      "remove all documents if there is no session data batch (i.e. it has expired)" in {
        mongoCollectionDrop()
        mongoCollectionInsertBatches(id, sessionId, fullSetOfBatchesToSaveToMongo.toSeq)
        Await.result(aftBatchedDataCacheRepository
          .save(id, sessionId, Option(ChargeAndMember(ChargeType.ChargeTypeDeRegistration, None)), payloadOther),
          Duration.Inf) mustBe false
        whenReady(aftBatchedDataCacheRepository.find("uniqueAftId" -> uniqueAftId)) { _.isEmpty mustBe true}
      }
    }

    "setSessionData" must {
      "save all batches correctly in Mongo collection, lock return, and remove batches beyond last batch (charge G batch 3)" in {
        mongoCollectionDrop()
        val jsObjectCaptor: ArgumentCaptor[JsObject] = ArgumentCaptor.forClass(classOf[JsObject])
        when(batchService.createBatches(jsObjectCaptor.capture(), any())).thenReturn(fullSetOfBatchesToSaveToMongo)
        when(batchService.lastBatchNo(any())).thenReturn(Set(BatchIdentifier(BatchType.ChargeG, 3)))

        Await.result(aftBatchedDataCacheRepository
          .setSessionData(id, lockDetail, dummyJson, sessionId, version = version, accessMode = accessMode,
            areSubmittedVersionsAvailable = areSubmittedVersionsAvailable), Duration.Inf) mustBe true
        jsObjectCaptor.getValue mustBe dummyJson
        whenReady(aftBatchedDataCacheRepository.find("uniqueAftId" -> uniqueAftId)) { documentsInDB =>
          documentsInDB.size mustBe 11
          val expectedBatches =
            fullSetOfBatchesToSaveToMongo.filterNot(bi => bi.batchType == BatchType.ChargeG && bi.batchNo == 4) ++
              sessionDataBatch(sessionId, lockDetail)
          dbDocumentsAsSeqBatchInfo(documentsInDB) mustBe expectedBatches
        }
      }

      "save all batches correctly where one charge C batch and last member for last batch for charge E removed" in {
        val jsArrayChargeC = payloadChargeTypeCEmployer(numberOfItems = 2)
        val jsArrayChargeE = payloadChargeTypeEMember(numberOfItems = 1)
        val batchInfoOtherInDB = BatchInfo(BatchType.Other, 1, payloadOther ++
          concatenateNodes(Seq(payloadChargeTypeEMinusMembers(numberOfItems = 1)), nodeNameChargeE) ++
          concatenateNodes(Seq(payloadChargeTypeCMinusEmployers(numberOfItems = 1)), nodeNameChargeC))
        val batchInfoOtherInPayload = BatchInfo(BatchType.Other, 1, payloadOther ++
          concatenateNodes(Seq(payloadChargeTypeCMinusEmployers(numberOfItems = 1)), nodeNameChargeC))
        val batchInfoChargeC = BatchInfo(BatchType.ChargeC, 1, JsArray(Seq(jsArrayChargeC(0), jsArrayChargeC(1))))
        val batchesToInsertIntoDB: Set[BatchInfo] =
          Set(batchInfoOtherInDB, batchInfoChargeC, BatchInfo(BatchType.ChargeE, 1, JsArray(Seq(jsArrayChargeE(0)))))

        mongoCollectionDrop()
        mongoCollectionInsertSessionDataBatch(id, sessionId, sessionData(sessionId), batchSize = 5)
        mongoCollectionInsertBatches(id, sessionId, batchesToInsertIntoDB.toSeq)

        val jsObjectCaptor: ArgumentCaptor[JsObject] = ArgumentCaptor.forClass(classOf[JsObject])
        when(batchService.createBatches(jsObjectCaptor.capture(), any()))
          .thenReturn(Set(batchInfoOtherInPayload, batchInfoChargeC))
        when(batchService.lastBatchNo(any())).thenReturn(Set(
          BatchIdentifier(ChargeC, 1),
          BatchIdentifier(ChargeD, 0),
          BatchIdentifier(ChargeE, 0),
          BatchIdentifier(ChargeG, 0)
        ))

        Await.result(aftBatchedDataCacheRepository
          .setSessionData(id, lockDetail, dummyJson, sessionId, version = version, accessMode = accessMode,
            areSubmittedVersionsAvailable = areSubmittedVersionsAvailable), Duration.Inf) mustBe true
        whenReady(aftBatchedDataCacheRepository.find("uniqueAftId" -> uniqueAftId)) { documentsInDB =>
          documentsInDB.size mustBe 3
          val actualSetBatchInfo = dbDocumentsAsSeqBatchInfo(documentsInDB)
          actualSetBatchInfo.exists(_.batchType == BatchType.ChargeE) mustBe false
          actualSetBatchInfo.count(_.batchType == BatchType.ChargeC) mustBe 1
        }
      }

      "save all batches correctly in Mongo collection when there is no lock detail and not lock" in {
        mongoCollectionDrop()
        val jsObjectCaptor: ArgumentCaptor[JsObject] = ArgumentCaptor.forClass(classOf[JsObject])
        when(batchService.createBatches(jsObjectCaptor.capture(), any())).thenReturn(fullSetOfBatchesToSaveToMongo)
        when(batchService.lastBatchNo(any())).thenReturn(Set())

        Await.result(aftBatchedDataCacheRepository
          .setSessionData(id, None, dummyJson, sessionId, version = version, accessMode = accessMode,
            areSubmittedVersionsAvailable = areSubmittedVersionsAvailable), Duration.Inf) mustBe true
        jsObjectCaptor.getValue mustBe dummyJson
        whenReady(aftBatchedDataCacheRepository.find("uniqueAftId" -> uniqueAftId)) { documentsInDB =>
          documentsInDB.size mustBe 12
          val expectedBatches =
            fullSetOfBatchesToSaveToMongo ++ sessionDataBatch(sessionId, None)
          dbDocumentsAsSeqBatchInfo(documentsInDB) mustBe expectedBatches
        }
      }
    }

    "get" must {
      "return previously entered items minus the session data batch" in {
        mongoCollectionDrop()
        mongoCollectionInsertSessionDataBatch(id, sessionId, sessionData(sessionId))
        mongoCollectionInsertBatches(id, sessionId, fullSetOfBatchesToSaveToMongo.toSeq)

        val biCaptor = ArgumentCaptor.forClass(classOf[Seq[BatchInfo]])
        when(batchService.createUserDataFullPayload(biCaptor.capture())).thenReturn(dummyJson)

        Await.result(aftBatchedDataCacheRepository.get(id, sessionId), Duration.Inf) mustBe Some(dummyJson)
        val capturedSeqBatchInfo: Seq[BatchInfo] = biCaptor.getValue
        capturedSeqBatchInfo.size mustBe 11
        capturedSeqBatchInfo.toSet mustBe fullSetOfBatchesToSaveToMongo
      }

      "remove all documents and return None if there is no session data batch (i.e. it has expired)" in {
        mongoCollectionDrop()
        mongoCollectionInsertBatches(id, sessionId, fullSetOfBatchesToSaveToMongo.toSeq)
        Await.result(aftBatchedDataCacheRepository.get(id, sessionId), Duration.Inf) mustBe None
        whenReady(aftBatchedDataCacheRepository.find("uniqueAftId" -> uniqueAftId)) {_.isEmpty mustBe true}
      }

      "return None if there are no batches but there is a session data batch" in {
        mongoCollectionDrop()
        mongoCollectionInsertSessionDataBatch(id, sessionId, sessionData(sessionId))
        when(batchService.createUserDataFullPayload(any())).thenReturn(dummyJson)
        Await.result(aftBatchedDataCacheRepository.get(id, sessionId), Duration.Inf) mustBe None
      }
    }

    "getSessionData" must {
      "return the session data batch with no lock info if not locked by another user" in {
        mongoCollectionDrop()
        mongoCollectionInsertSessionDataBatch(id, sessionId, sessionData(sessionId, None))
        Await.result(aftBatchedDataCacheRepository.getSessionData(sessionId, id), Duration.Inf) mustBe Some(sessionData(sessionId, None))
      }

      "return the session data batch with lock info if locked by another user" in {
        mongoCollectionDrop()
        mongoCollectionInsertSessionDataBatch(id, sessionId, sessionData(sessionId, None))
        mongoCollectionInsertSessionDataBatch(id, anotherSessionId, sessionData(anotherSessionId))
        Await.result(aftBatchedDataCacheRepository.getSessionData(sessionId, id), Duration.Inf) mustBe Some(sessionData(sessionId))
      }

      "remove all documents and return None if there is no session data batch (i.e. it has expired)" in {
        mongoCollectionDrop()
        mongoCollectionInsertBatches(id, sessionId, fullSetOfBatchesToSaveToMongo.toSeq)
        Await.result(aftBatchedDataCacheRepository.getSessionData(sessionId, id), Duration.Inf) mustBe None
        whenReady(aftBatchedDataCacheRepository.find("uniqueAftId" -> uniqueAftId)) {_.isEmpty mustBe true}
      }
    }

    "lockedBy" must {
      "return None if not locked by another user" in {
        mongoCollectionDrop()
        Await.result(aftBatchedDataCacheRepository.lockedBy(sessionId, id), Duration.Inf) mustBe None
      }

      "return the lock info if locked by another user" in {
        mongoCollectionDrop()
        mongoCollectionInsertSessionDataBatch(id, anotherSessionId, sessionData(anotherSessionId))
        Await.result(aftBatchedDataCacheRepository.lockedBy(sessionId, id), Duration.Inf) mustBe lockDetail
      }

      "return None if locked by current user" in {
        mongoCollectionDrop()
        mongoCollectionInsertSessionDataBatch(id, sessionId, sessionData(sessionId))
        Await.result(aftBatchedDataCacheRepository.lockedBy(sessionId, id), Duration.Inf) mustBe None
      }
    }

    "remove" must {
      "remove all documents for scheme if present" in {
        mongoCollectionInsertBatches(id, sessionId, fullSetOfBatchesToSaveToMongo.toSeq)
        Await.result(aftBatchedDataCacheRepository.remove(id, sessionId), Duration.Inf) mustBe true
        whenReady(aftBatchedDataCacheRepository.find("uniqueAftId" -> uniqueAftId)) {_.isEmpty mustBe true}
      }
    }
  }
}

object AftBatchedDataCacheRepositorySpec extends AnyWordSpec with MockitoSugar {

  import models.BatchedRepositorySampleData._

  private def mongoCollectionDrop(): Boolean = Await
    .result(aftBatchedDataCacheRepository.collection.drop(failIfNotFound = false), Duration.Inf)

  private def mongoCollectionInsertBatches(id: String, sessionId: String, seqBatchInfo: Seq[BatchInfo]): Boolean = {
    def selector(batchType: BatchType, batchNo: Int): BSONDocument = BSONDocument("uniqueAftId" -> (id + sessionId),
      "batchType" -> batchType.toString, "batchNo" -> batchNo)

    val seqFutureUpdateWriteResult = seqBatchInfo.map { bi =>
      val modifier = BSONDocument.apply("$set" -> Json.obj("id" -> id, "id" -> sessionId, "data" -> bi.jsValue))
      aftBatchedDataCacheRepository.collection.update.one(selector(bi.batchType, bi.batchNo), modifier, upsert = true)
    }
    Await.result(Future.sequence(seqFutureUpdateWriteResult).map {
      _.forall(_.ok)
    }, Duration.Inf)
  }

  private def mongoCollectionInsertSessionDataBatch(id: String, sessionId: String, sd: SessionData, batchSize:Int = 2): Boolean = {
    val selector: BSONDocument = BSONDocument("uniqueAftId" -> (id + sessionId),
      "batchType" -> BatchType.SessionData.toString, "batchNo" -> 1)

    val modifier = BSONDocument
      .apply("$set" -> Json.obj("id" -> id, "id" -> sessionId, "data" -> Json.toJson(sd), "batchSize" -> batchSize))

    Await.result(aftBatchedDataCacheRepository.collection.update.one(selector, modifier, upsert = true), Duration.Inf)
      .ok
  }

  private val mockAppConfig = mock[AppConfig]

  private val dummyJson = Json.obj("dummy" -> "value")
  private val version = 1
  private val accessMode = "dummy"
  private val areSubmittedVersionsAvailable = false
  private val id = "S24000000152020-04-01"
  private val sessionId = "session-1"
  private val anotherSessionId = "session-2"
  private val uniqueAftId = id + sessionId
  private val lockDetail = Some(LockDetail(name = "Billy Wiggins", "A123456"))
  private val batchService = mock[BatchService]
  private val databaseName = "aft-batches"
  private val mongoUri: String = s"mongodb://127.0.0.1:27017/$databaseName?heartbeatFrequencyMS=1000&rm.failover=default"
  private val mongoConnectorForTest = MongoConnector(mongoUri)
  private val rmc = new ReactiveMongoComponent {
    override def mongoConnector: MongoConnector = mongoConnectorForTest
  }

  private def sessionData(sessionId: String, lockDetail: Option[LockDetail] = lockDetail) = SessionData(
    sessionId = sessionId, lockDetail = lockDetail, version = version, accessMode = accessMode,
    areSubmittedVersionsAvailable = false)

  private def sessionDataBatch(sessionId: String, lockDetail: Option[LockDetail] = lockDetail):Set[BatchInfo] = {
    val json = Json.toJson(sessionData(sessionId, lockDetail))
    Set(BatchInfo(BatchType.SessionData, 1, json))
  }

  private def dbDocumentsAsSeqBatchInfo(s:Seq[JsValue]):Set[BatchInfo] = {
    s.map { jsValue =>
      val batchType = BatchType.getBatchType((jsValue \ "batchType").as[String])
        .getOrElse(throw new RuntimeException("Unknown batch type"))
      val batchNo = (jsValue \ "batchNo").as[Int]
      val jsData = {
        val t = jsValue \ "data"
        batchType match {
          case BatchType.Other => t.as[JsObject]
          case BatchType.SessionData => t.as[JsObject]
          case _ => t.as[JsArray]
        }
      }
      BatchInfo(batchType, batchNo, jsData)
    }.toSet
  }

  private def filterOnBatchTypeAndNo(batchType: BatchType, batchNo:Int): BatchInfo => Boolean =
    bi => bi.batchType == batchType && bi.batchNo == batchNo

  private val setOfFourChargeCMembersInTwoBatches: Set[BatchInfo] = {
    val payloadOtherBatch = payloadOther
    val jsArrayChargeC = payloadChargeTypeCEmployer(numberOfItems = 4)
    Set(
      BatchInfo(BatchType.Other, 1, payloadOtherBatch),
      BatchInfo(BatchType.ChargeC, 1, JsArray(Seq(jsArrayChargeC(0), jsArrayChargeC(1)))),
      BatchInfo(BatchType.ChargeC, 2, JsArray(Seq(jsArrayChargeC(2), jsArrayChargeC(3))))
    )
  }

  private val fullSetOfBatchesToSaveToMongo: Set[BatchInfo] = {
    val jsArrayChargeC = payloadChargeTypeCEmployer(numberOfItems = 5)
    val jsArrayChargeD = payloadChargeTypeDMember(numberOfItems = 4)
    val jsArrayChargeE = payloadChargeTypeEMember(numberOfItems = 2)
    val jsArrayChargeG = payloadChargeTypeGMember(numberOfItems = 7)
    Set(BatchInfo(BatchType.Other, 1,
      payloadOther ++ payloadChargeTypeA ++ payloadChargeTypeB ++ payloadChargeTypeF ++ concatenateNodes(
        Seq(payloadChargeTypeCMinusEmployers(numberOfItems = 5)), nodeNameChargeC) ++ concatenateNodes(
        Seq(payloadChargeTypeDMinusMembers(numberOfItems = 4)), nodeNameChargeD) ++ concatenateNodes(
        Seq(payloadChargeTypeEMinusMembers(numberOfItems = 2)), nodeNameChargeE) ++ concatenateNodes(
        Seq(payloadChargeTypeGMinusMembers(numberOfItems = 7)), nodeNameChargeG)),
      BatchInfo(BatchType.ChargeC, 1, JsArray(Seq(jsArrayChargeC(0), jsArrayChargeC(1)))),
      BatchInfo(BatchType.ChargeC, 2, JsArray(Seq(jsArrayChargeC(2), jsArrayChargeC(3)))),
      BatchInfo(BatchType.ChargeC, 3, JsArray(Seq(jsArrayChargeC(4)))),
      BatchInfo(BatchType.ChargeD, 1, JsArray(Seq(jsArrayChargeD(0), jsArrayChargeD(1)))),
      BatchInfo(BatchType.ChargeD, 2, JsArray(Seq(jsArrayChargeD(2), jsArrayChargeD(3)))),
      BatchInfo(BatchType.ChargeE, 1, JsArray(Seq(jsArrayChargeE(0), jsArrayChargeE(1)))),
      BatchInfo(BatchType.ChargeG, 1, JsArray(Seq(jsArrayChargeG(0), jsArrayChargeG(1)))),
      BatchInfo(BatchType.ChargeG, 2, JsArray(Seq(jsArrayChargeG(2), jsArrayChargeG(3)))),
      BatchInfo(BatchType.ChargeG, 3, JsArray(Seq(jsArrayChargeG(4), jsArrayChargeG(5)))),
      BatchInfo(BatchType.ChargeG, 4, JsArray(Seq(jsArrayChargeG(6)))))
  }

  def aftBatchedDataCacheRepository = new AftBatchedDataCacheRepository(rmc, batchService, mockAppConfig)
}

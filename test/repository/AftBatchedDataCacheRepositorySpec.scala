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

package repository

import com.mongodb.client.model.FindOneAndUpdateOptions
import config.AppConfig
import models.BatchedRepositorySampleData._
import models.{ChargeAndMember, ChargeType, LockDetail}
import org.mockito.ArgumentMatchers._
import org.mockito.Mockito._
import org.mockito.{ArgumentCaptor, ArgumentMatchers}
import org.mongodb.scala.bson.conversions.Bson
import org.mongodb.scala.model.Updates.set
import org.mongodb.scala.model.{Filters, Updates}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.must.Matchers
import org.scalatest.time.{Millis, Seconds, Span}
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.{BeforeAndAfter, BeforeAndAfterAll, BeforeAndAfterEach}
import org.scalatestplus.mockito.MockitoSugar
import play.api.libs.json.{JsArray, JsObject, JsValue, Json}
import repository.model.SessionData
import services.BatchService
import services.BatchService.BatchType.{ChargeC, ChargeD, ChargeE, ChargeG}
import services.BatchService.{BatchIdentifier, BatchInfo, BatchType}
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.Codecs

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}

class AftBatchedDataCacheRepositorySpec
  extends AnyWordSpec with MockitoSugar with Matchers with EmbeddedMongoDBSupport with BeforeAndAfter with
    BeforeAndAfterEach with BeforeAndAfterAll with ScalaFutures { // scalastyle:off magic.number

  override implicit val patienceConfig: PatienceConfig = PatienceConfig(Span(30, Seconds), Span(1, Millis))

  import AftBatchedDataCacheRepositorySpec._

  var aftBatchedDataCacheRepository: AftBatchedDataCacheRepository = _

  override def beforeAll(): Unit = {
    when(mockAppConfig.mongoDBAFTBatchesMaxTTL).thenReturn(43200)
    when(mockAppConfig.mongoDBAFTBatchesTTL).thenReturn(999999)
    when(mockAppConfig.mongoDBAFTBatchesCollectionName).thenReturn(collectionName)
    initMongoDExecutable()
    startMongoD()
    aftBatchedDataCacheRepository = buildRepository(mongoHost, mongoPort)
    super.beforeAll()
  }

  override def beforeEach(): Unit = {
    when(mockAppConfig.mongoDBAFTBatchesUserDataBatchSize).thenReturn(2)
    super.beforeEach()
  }

  override def afterAll(): Unit =
    stopMongoD()

  "save" must {
    "save de-reg charge batch correctly in Mongo collection where there is some session data" in {
      val fullPayload = payloadOther ++ payloadChargeTypeA
      when(batchService.batchIdentifierForChargeAndMember(any(), any()))
        .thenReturn(Some(BatchIdentifier(BatchType.Other, 1)))
      when(batchService.getOtherJsObject(ArgumentMatchers.eq(fullPayload))).thenReturn(dummyJson)

      val documentsInDB = for {
        _ <- aftBatchedDataCacheRepository.collection.drop().toFuture()
        _ <- mongoCollectionInsertSessionDataBatch(aftBatchedDataCacheRepository, id, sessionId, sessionData(sessionId))
        _ <- mongoCollectionInsertBatches(aftBatchedDataCacheRepository, id, sessionId, fullSetOfBatchesToSaveToMongo.toSeq)
        _ <- aftBatchedDataCacheRepository
          .save(id, sessionId, Option(ChargeAndMember(ChargeType.ChargeTypeDeRegistration, None)), fullPayload)
        documentsInDB <- aftBatchedDataCacheRepository.collection.find(filter = Filters.eq("uniqueAftId", uniqueAftId)).toFuture()
      } yield documentsInDB

      whenReady(documentsInDB) { documentsInDB =>
        documentsInDB.size mustBe 12
        val actualOtherBatch = dbDocumentsAsSeqBatchInfo(documentsInDB)
          .filter(filterOnBatchTypeAndNo(batchType = BatchType.Other, batchNo = 1))
        actualOtherBatch.nonEmpty mustBe true
        actualOtherBatch.foreach {
          _.jsValue mustBe dummyJson
        }
      }
    }

    "save member-based charge batch for a member correctly in Mongo collection where there is some session data" in {
      val fullPayload = payloadOther ++ payloadChargeTypeC(5)
      val amendedPayload = payloadChargeTypeCEmployer(numberOfItems = 5, employerName = "WIDGETS INC")
      val amendedBatchInfo = Some(BatchInfo(BatchType.ChargeC, 2, amendedPayload))
      when(batchService.batchIdentifierForChargeAndMember(any(), any()))
        .thenReturn(Some(BatchIdentifier(BatchType.ChargeC, 4)))
      when(batchService.getChargeTypeJsObjectForBatch(any(), any(), any(), any())).thenReturn(amendedBatchInfo)

      val documentsInDB = for {
        _ <- aftBatchedDataCacheRepository.collection.drop().toFuture()
        _ <- mongoCollectionInsertSessionDataBatch(aftBatchedDataCacheRepository, id, sessionId, sessionData(sessionId))
        _ <- mongoCollectionInsertBatches(aftBatchedDataCacheRepository, id, sessionId, fullSetOfBatchesToSaveToMongo.toSeq)
        _ <- aftBatchedDataCacheRepository
          .save(id, sessionId, Option(ChargeAndMember(ChargeType.ChargeTypeAuthSurplus, Some(4))), fullPayload)
        documentsInDB <- aftBatchedDataCacheRepository.collection.find(filter = Filters.eq("uniqueAftId", uniqueAftId)).toFuture()
      } yield documentsInDB

      whenReady(documentsInDB) { documentsInDB =>
        documentsInDB.size mustBe 12
        val actualOtherBatch = dbDocumentsAsSeqBatchInfo(documentsInDB)
          .filter(filterOnBatchTypeAndNo(batchType = BatchType.ChargeC, batchNo = 2))
        actualOtherBatch.nonEmpty mustBe true
        actualOtherBatch.foreach {
          _.jsValue.as[JsArray] mustBe amendedPayload
        }
      }
    }

    "save batch for a new member where member added is first in new batch (batch 3 where currently only 2 batches)" in {
      val amendedPayload = payloadChargeTypeCEmployer(numberOfItems = 1, employerName = "WIDGETS INC")
      val amendedBatchInfo = Some(BatchInfo(BatchType.ChargeC, 3, amendedPayload))
      when(batchService.batchIdentifierForChargeAndMember(any(), any()))
        .thenReturn(Some(BatchIdentifier(BatchType.ChargeC, 3)))
      when(batchService.getChargeTypeJsObjectForBatch(any(), any(), any(), any())).thenReturn(amendedBatchInfo)

      val documentsInDB = for {
        _ <- aftBatchedDataCacheRepository.collection.drop().toFuture()
        _ <- mongoCollectionInsertSessionDataBatch(aftBatchedDataCacheRepository, id, sessionId, sessionData(sessionId))
        _ <- mongoCollectionInsertBatches(aftBatchedDataCacheRepository, id, sessionId, setOfFourChargeCMembersInTwoBatches.toSeq)
        _ <- aftBatchedDataCacheRepository
          .save(id, sessionId, Option(ChargeAndMember(ChargeType.ChargeTypeAuthSurplus, Some(5))),
            payloadOther ++ payloadChargeTypeC(5))
        documentsInDB <- aftBatchedDataCacheRepository.collection.find(filter = Filters.eq("uniqueAftId", uniqueAftId)).toFuture()
      } yield documentsInDB

      whenReady(documentsInDB) { documentsInDB =>
        documentsInDB.size mustBe 5
        val actualOtherBatch = dbDocumentsAsSeqBatchInfo(documentsInDB)
          .filter(filterOnBatchTypeAndNo(batchType = BatchType.ChargeC, batchNo = 3))
        actualOtherBatch.nonEmpty mustBe true
        actualOtherBatch.foreach {
          _.jsValue.as[JsArray] mustBe amendedPayload
        }
      }
    }

    "re-size batches when batch size changed" in {

      val biCaptor: ArgumentCaptor[Seq[BatchInfo]] = ArgumentCaptor.forClass(classOf[Seq[BatchInfo]])
      when(mockAppConfig.mongoDBAFTBatchesUserDataBatchSize).thenReturn(4)
      when(batchService.createUserDataFullPayload(biCaptor.capture())).thenReturn(dummyJson)
      when(batchService.createBatches(any(), any())).thenReturn(fullSetOfBatchesToSaveToMongo)
      val fullPayload = payloadOther ++ payloadChargeTypeA
      when(batchService.lastBatchNo(any())).thenReturn(Set())
      when(batchService.batchIdentifierForChargeAndMember(any(), any()))
        .thenReturn(Some(BatchIdentifier(BatchType.Other, 1)))
      when(batchService.getOtherJsObject(ArgumentMatchers.eq(fullPayload))).thenReturn(dummyJson)

      val documentsInDB = for {
        _ <- aftBatchedDataCacheRepository.collection.drop().toFuture()
        _ <- mongoCollectionInsertSessionDataBatch(aftBatchedDataCacheRepository, id, sessionId, sessionData(sessionId))
        _ <- mongoCollectionInsertBatches(aftBatchedDataCacheRepository, id, sessionId, fullSetOfBatchesToSaveToMongo.toSeq)
        res <- aftBatchedDataCacheRepository
          .save(id, sessionId, Option(ChargeAndMember(ChargeType.ChargeTypeDeRegistration, None)), fullPayload)
      } yield res

      whenReady(documentsInDB) { _ =>
        biCaptor.getValue.size mustBe 11

        verify(batchService, times(1)).createUserDataFullPayload(any())
        verify(batchService, times(1)).createBatches(any(), any())
      }
    }

    "remove all documents if there is no session data batch (i.e. it has expired)" in {
      val documentsInDB = for {
        _ <- aftBatchedDataCacheRepository.collection.drop().toFuture()
        _ <- mongoCollectionInsertBatches(aftBatchedDataCacheRepository, id, sessionId, fullSetOfBatchesToSaveToMongo.toSeq)
        _ <- aftBatchedDataCacheRepository
          .save(id, sessionId, Option(ChargeAndMember(ChargeType.ChargeTypeDeRegistration, None)), payloadOther)
        res <- aftBatchedDataCacheRepository.collection.find(filter = Filters.eq("uniqueAftId", uniqueAftId)).toFuture()
      } yield res

      whenReady(documentsInDB) {
        _.isEmpty mustBe true
      }
    }
  }

  "setSessionData" must {
    "save all batches correctly in Mongo collection, lock return, and remove batches beyond last batch (charge G batch 3)" in {
      val jsObjectCaptor: ArgumentCaptor[JsObject] = ArgumentCaptor.forClass(classOf[JsObject])
      when(batchService.createBatches(jsObjectCaptor.capture(), any())).thenReturn(fullSetOfBatchesToSaveToMongo)
      when(batchService.lastBatchNo(any())).thenReturn(Set(BatchIdentifier(BatchType.ChargeG, 3)))

      val documentsInDB = for {
        _ <- aftBatchedDataCacheRepository.collection.drop().toFuture()
        _ <- aftBatchedDataCacheRepository
          .setSessionData(id, lockDetail, dummyJson, sessionId, version = version, accessMode = accessMode,
            areSubmittedVersionsAvailable = areSubmittedVersionsAvailable)
        res <- aftBatchedDataCacheRepository.collection.find(filter = Filters.eq("uniqueAftId", uniqueAftId)).toFuture()
      } yield res

      whenReady(documentsInDB) { documentsInDB =>
        jsObjectCaptor.getValue mustBe dummyJson
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

      val jsObjectCaptor: ArgumentCaptor[JsObject] = ArgumentCaptor.forClass(classOf[JsObject])
      when(batchService.createBatches(jsObjectCaptor.capture(), any()))
        .thenReturn(Set(batchInfoOtherInPayload, batchInfoChargeC))
      when(batchService.lastBatchNo(any())).thenReturn(Set(
        BatchIdentifier(ChargeC, 1),
        BatchIdentifier(ChargeD, 0),
        BatchIdentifier(ChargeE, 0),
        BatchIdentifier(ChargeG, 0)
      ))

      val documentsInDB = for {
        _ <- aftBatchedDataCacheRepository.collection.drop().toFuture()
        _ <- mongoCollectionInsertSessionDataBatch(aftBatchedDataCacheRepository, id, sessionId, sessionData(sessionId), batchSize = 5)
        _ <- mongoCollectionInsertBatches(aftBatchedDataCacheRepository, id, sessionId, batchesToInsertIntoDB.toSeq)
        _ <- aftBatchedDataCacheRepository
          .setSessionData(id, lockDetail, dummyJson, sessionId, version = version, accessMode = accessMode,
            areSubmittedVersionsAvailable = areSubmittedVersionsAvailable)
        res <- aftBatchedDataCacheRepository.collection.find(filter = Filters.eq("uniqueAftId", uniqueAftId)).toFuture()
      } yield res

      whenReady(documentsInDB) { documentsInDB =>
        documentsInDB.size mustBe 3
        val actualSetBatchInfo = dbDocumentsAsSeqBatchInfo(documentsInDB)
        actualSetBatchInfo.exists(_.batchType == BatchType.ChargeE) mustBe false
        actualSetBatchInfo.count(_.batchType == BatchType.ChargeC) mustBe 1
      }
    }

    "save all batches correctly in Mongo collection when there is no lock detail and not lock" in {
      val jsObjectCaptor: ArgumentCaptor[JsObject] = ArgumentCaptor.forClass(classOf[JsObject])
      when(batchService.createBatches(jsObjectCaptor.capture(), any())).thenReturn(fullSetOfBatchesToSaveToMongo)
      when(batchService.lastBatchNo(any())).thenReturn(Set())

      val documentsInDB = for {
        _ <- aftBatchedDataCacheRepository.collection.drop().toFuture()
        _ <- aftBatchedDataCacheRepository
          .setSessionData(id, None, dummyJson, sessionId, version = version, accessMode = accessMode,
            areSubmittedVersionsAvailable = areSubmittedVersionsAvailable)
        res <- aftBatchedDataCacheRepository.collection.find(filter = Filters.eq("uniqueAftId", uniqueAftId)).toFuture()
      } yield res

      whenReady(documentsInDB) { documentsInDB =>
        jsObjectCaptor.getValue mustBe dummyJson
        documentsInDB.size mustBe 12
        val expectedBatches =
          fullSetOfBatchesToSaveToMongo ++ sessionDataBatch(sessionId, None)
        dbDocumentsAsSeqBatchInfo(documentsInDB) mustBe expectedBatches
      }
    }
  }

  "get" must {
    "return previously entered items minus the session data batch" in {
      val biCaptor = ArgumentCaptor.forClass(classOf[Seq[BatchInfo]])
      when(batchService.createUserDataFullPayload(biCaptor.capture())).thenReturn(dummyJson)

      val documentsInDB = for {
        _ <- aftBatchedDataCacheRepository.collection.drop().toFuture()
        _ <- mongoCollectionInsertSessionDataBatch(aftBatchedDataCacheRepository, id, sessionId, sessionData(sessionId))
        _ <- mongoCollectionInsertBatches(aftBatchedDataCacheRepository, id, sessionId, fullSetOfBatchesToSaveToMongo.toSeq)
        res <- aftBatchedDataCacheRepository.get(id, sessionId)
      } yield res

      whenReady(documentsInDB) { result =>
        result mustBe Some(dummyJson)
        val capturedSeqBatchInfo: Seq[BatchInfo] = biCaptor.getValue
        capturedSeqBatchInfo.size mustBe 11
        capturedSeqBatchInfo.toSet mustBe fullSetOfBatchesToSaveToMongo
      }
    }

    "remove all documents and return None if there is no session data batch (i.e. it has expired)" in {
      val documentsInDB = for {
        _ <- aftBatchedDataCacheRepository.collection.drop().toFuture()
        _ <- mongoCollectionInsertBatches(aftBatchedDataCacheRepository, id, sessionId, fullSetOfBatchesToSaveToMongo.toSeq)
        res1 <- aftBatchedDataCacheRepository.get(id, sessionId)
        res2 <- aftBatchedDataCacheRepository.collection.find(filter = Filters.eq("uniqueAftId", uniqueAftId)).toFuture()
      } yield (res1, res2)

      whenReady(documentsInDB) { result =>
        result._1 mustBe None
        result._2.isEmpty mustBe true
      }
    }

    "return None if there are no batches but there is a session data batch" in {
      when(batchService.createUserDataFullPayload(any())).thenReturn(dummyJson)

      val documentsInDB = for {
        _ <- aftBatchedDataCacheRepository.collection.drop().toFuture()
        _ <- mongoCollectionInsertSessionDataBatch(aftBatchedDataCacheRepository, id, sessionId, sessionData(sessionId))
        res <- aftBatchedDataCacheRepository.get(id, sessionId)
      } yield res

      whenReady(documentsInDB) {
        _ mustBe None
      }
    }
  }

  "getSessionData" must {
    "return the session data batch with no lock info if not locked by another user" in {
      Await.result(aftBatchedDataCacheRepository.collection.drop().toFuture(), Duration.Inf)
      Await.result(mongoCollectionInsertSessionDataBatch(aftBatchedDataCacheRepository, id, sessionId, sessionData(sessionId, None)), Duration.Inf)
      Await.result(aftBatchedDataCacheRepository.getSessionData(sessionId, id), Duration.Inf) mustBe Some(sessionData(sessionId, None))
    }

    "return the session data batch with lock info if locked by another user" in {

      val documentsInDB = for {
        _ <- aftBatchedDataCacheRepository.collection.drop().toFuture()
        _ <- mongoCollectionInsertSessionDataBatch(aftBatchedDataCacheRepository, id, sessionId, sessionData(sessionId, None))
        _ <- mongoCollectionInsertSessionDataBatch(aftBatchedDataCacheRepository, id, anotherSessionId, sessionData(anotherSessionId))
        res <- aftBatchedDataCacheRepository.getSessionData(sessionId, id)
      } yield res

      whenReady(documentsInDB) { res =>
        res mustBe Some(sessionData(sessionId))
      }
    }

    "remove all documents and return None if there is no session data batch (i.e. it has expired)" in {
      val documentsInDB = for {
        _ <- aftBatchedDataCacheRepository.collection.drop().toFuture()
        _ <- mongoCollectionInsertBatches(aftBatchedDataCacheRepository, id, sessionId, fullSetOfBatchesToSaveToMongo.toSeq)
        res1 <- aftBatchedDataCacheRepository.getSessionData(sessionId, id)
        res2 <- aftBatchedDataCacheRepository.collection.find(filter = Filters.eq("uniqueAftId", uniqueAftId)).toFuture()
      } yield (res1, res2)

      whenReady(documentsInDB) { result =>
        result._1 mustBe None
        result._2.isEmpty mustBe true
      }
    }
  }

  "lockedBy" must {
    "return None if not locked by another user" in {
      val documentsInDB = for {
        _ <- aftBatchedDataCacheRepository.collection.drop().toFuture()
        res <- aftBatchedDataCacheRepository.lockedBy(sessionId, id)
      } yield res

      whenReady(documentsInDB) {
        _ mustBe None
      }
    }

    "return the lock info if same scheme is locked by another user" in {
      val documentsInDB = for {
        _ <- aftBatchedDataCacheRepository.collection.drop().toFuture()
        _ <- mongoCollectionInsertSessionDataBatch(aftBatchedDataCacheRepository, id, anotherSessionId, sessionData(anotherSessionId))
        res <- aftBatchedDataCacheRepository.lockedBy(sessionId, id)
      } yield res

      whenReady(documentsInDB) {
        _ mustBe lockDetail
      }
    }

    "return None if different scheme is locked by another user" in {
      val documentsInDB = for {
        _ <- aftBatchedDataCacheRepository.collection.drop().toFuture()
        _ <- mongoCollectionInsertSessionDataBatch(aftBatchedDataCacheRepository, id, anotherSessionId, sessionData(anotherSessionId))
        res <- aftBatchedDataCacheRepository.lockedBy(sessionId, anotherSchemeId)
      } yield res

      whenReady(documentsInDB) {
        _ mustBe None
      }
    }

    "return None if locked by current user" in {
      val documentsInDB = for {
        _ <- aftBatchedDataCacheRepository.collection.drop().toFuture()
        _ <- mongoCollectionInsertSessionDataBatch(aftBatchedDataCacheRepository, id, sessionId, sessionData(sessionId))
        res <- aftBatchedDataCacheRepository.lockedBy(sessionId, id)
      } yield res

      whenReady(documentsInDB) {
        _ mustBe None
      }
    }
  }

  "remove" must {
    "remove all documents for scheme if present" in {
      val documentsInDB = for {
        _ <- aftBatchedDataCacheRepository.collection.drop().toFuture()
        _ <- mongoCollectionInsertBatches(aftBatchedDataCacheRepository, id, sessionId, fullSetOfBatchesToSaveToMongo.toSeq)
        _ <- aftBatchedDataCacheRepository.remove(id, sessionId)
        res <- aftBatchedDataCacheRepository.collection.find(filter = Filters.eq("uniqueAftId", uniqueAftId)).toFuture()
      } yield res

      whenReady(documentsInDB) {
        _.isEmpty mustBe true
      }
    }
  }
}

object AftBatchedDataCacheRepositorySpec extends MockitoSugar {
  private val uniqueAftIdKey = "uniqueAftId"
  private val batchTypeKey = "batchType"
  private val batchNoKey = "batchNo"
  private val idKey = "id"

  import models.BatchedRepositorySampleData._

  private def mongoCollectionInsertBatches(aftBatchedDataCacheRepository2: AftBatchedDataCacheRepository, id: String,
                                           sessionId: String, seqBatchInfo: Seq[BatchInfo]): Future[Unit] = {

    def selector(batchType: BatchType, batchNo: Int): Bson = {
      Filters.and(
        Filters.eq(uniqueAftIdKey, id + sessionId),
        Filters.eq(batchTypeKey, batchType.toString),
        Filters.eq(batchNoKey, batchNo)
      )
    }

    val seqFutureUpdateWriteResult = seqBatchInfo.map { bi =>
      val modifier = Updates.combine(
        set(idKey, id),
        set("id", sessionId),
        set("data", Codecs.toBson(bi.jsValue))
      )

      val upsertOptions = new FindOneAndUpdateOptions().upsert(true)
      aftBatchedDataCacheRepository2.collection.findOneAndUpdate(
        filter = selector(bi.batchType, bi.batchNo),
        update = modifier,
        upsertOptions
      ).toFuture().map(_ => (): Unit)

    }
    Future.sequence(seqFutureUpdateWriteResult).map(_ => (): Unit)
  }

  private def mongoCollectionInsertSessionDataBatch(aftBatchedDataCacheRepository2: AftBatchedDataCacheRepository, id: String,
                                                    sessionId: String, sd: SessionData, batchSize: Int = 2): Future[Unit] = {
    val selector: Bson = {
      Filters.and(
        Filters.eq(uniqueAftIdKey, id + sessionId),
        Filters.eq(batchTypeKey, BatchType.SessionData.toString),
        Filters.eq(batchNoKey, 1)
      )
    }
    val modifier = Updates.combine(
      set(idKey, id),
      set("data", Codecs.toBson(Json.toJson(sd))),
      set("batchSize", batchSize)
    )

    val upsertOptions = new FindOneAndUpdateOptions().upsert(true)

    aftBatchedDataCacheRepository2.collection.findOneAndUpdate(
      filter = selector,
      update = modifier,
      upsertOptions
    ).toFuture().map(_ => (): Unit)
  }

  private val mockAppConfig = mock[AppConfig]

  private val dummyJson = Json.obj("dummy" -> "value")
  private val version = 1
  private val accessMode = "dummy"
  private val areSubmittedVersionsAvailable = false
  private val id = "S24000000152020-04-01"
  private val anotherSchemeId = "S24000000162020-04-01"
  private val sessionId = "session-1"
  private val anotherSessionId = "session-2"
  private val uniqueAftId = id + sessionId
  private val lockDetail = Some(LockDetail(name = "Billy Wiggins", "A123456"))
  private val batchService = mock[BatchService]
  private val collectionName = "aft-batches"

  private def sessionData(sessionId: String, lockDetail: Option[LockDetail] = lockDetail) = SessionData(
    sessionId = sessionId, lockDetail = lockDetail, version = version, accessMode = accessMode,
    areSubmittedVersionsAvailable = false)

  private def sessionDataBatch(sessionId: String, lockDetail: Option[LockDetail]): Set[BatchInfo] = {
    val json = Json.toJson(sessionData(sessionId, lockDetail))
    Set(BatchInfo(BatchType.SessionData, 1, json))
  }

  private def dbDocumentsAsSeqBatchInfo(s: Seq[JsValue]): Set[BatchInfo] = {
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

  private def filterOnBatchTypeAndNo(batchType: BatchType, batchNo: Int): BatchInfo => Boolean =
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

  private def buildRepository(mongoHost: String, mongoPort: Int): AftBatchedDataCacheRepository = {
    val databaseName = "pension-scheme-accounting-for-tax"
    val mongoUri = s"mongodb://$mongoHost:$mongoPort/$databaseName?heartbeatFrequencyMS=1000&rm.failover=default"
    new AftBatchedDataCacheRepository(MongoComponent(mongoUri), batchService, mockAppConfig)
  }
}

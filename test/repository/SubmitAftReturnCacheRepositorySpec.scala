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

import config.AppConfig
import org.mongodb.scala.model.Filters
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.must.Matchers
import org.scalatest.time.{Millis, Seconds, Span}
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.{BeforeAndAfter, BeforeAndAfterAll, BeforeAndAfterEach}
import org.scalatestplus.mockito.MockitoSugar
import uk.gov.hmrc.mongo.MongoComponent

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class SubmitAftReturnCacheRepositorySpec
  extends AnyWordSpec with MockitoSugar with Matchers with EmbeddedMongoDBSupport with BeforeAndAfter with
    BeforeAndAfterEach with BeforeAndAfterAll with ScalaFutures { // scalastyle:off magic.number

  override implicit val patienceConfig: PatienceConfig = PatienceConfig(Span(30, Seconds), Span(1, Millis))

  import SubmitAftReturnCacheRepositorySpec._

  var submitAftReturnCacheRepository: SubmitAftReturnCacheRepository = _

  override def beforeAll(): Unit = {
    //    when(mockAppConfig.mongoDBAFTBatchesMaxTTL).thenReturn(43200)
    //    when(mockAppConfig.mongoDBAFTBatchesTTL).thenReturn(999999)
    //    when(mockAppConfig.mongoDBAFTBatchesCollectionName).thenReturn(collectionName)
    initMongoDExecutable()
    startMongoD()
    submitAftReturnCacheRepository = buildRepository(mongoHost, mongoPort)
    super.beforeAll()
  }

  override def beforeEach(): Unit = {
    super.beforeEach()
  }

  override def afterAll(): Unit =
    stopMongoD()

  "save" must {
    "insert a row into mongo" in {

      val document = for {
        _ <- submitAftReturnCacheRepository.collection.drop().toFuture()
        _ <- submitAftReturnCacheRepository
         .insertLockData()
        countedDocuments <- submitAftReturnCacheRepository.collection.countDocuments().toFuture()
      } yield countedDocuments

      whenReady(document) { documentsInDB =>
        documentsInDB mustBe 1L
      }
    }
  }
}

object SubmitAftReturnCacheRepositorySpec extends MockitoSugar {

//  private def mongoCollectionInsertBatches(submitAftReturnCacheRepository2: SubmitAftReturnCacheRepository, id: String,
//                                           sessionId: String, seqBatchInfo: Seq[BatchInfo]): Future[Unit] = {
//
//    def selector(batchType: BatchType, batchNo: Int): Bson = {
//      Filters.and(
//        Filters.eq(uniqueAftIdKey, id + sessionId),
//        Filters.eq(batchTypeKey, batchType.toString),
//        Filters.eq(batchNoKey, batchNo)
//      )
//    }
//
//    val seqFutureUpdateWriteResult = seqBatchInfo.map { bi =>
//      val modifier = Updates.combine(
//        set(idKey, id),
//        set("id", sessionId),
//        set("data", Codecs.toBson(bi.jsValue))
//      )
//
//      val upsertOptions = new FindOneAndUpdateOptions().upsert(true)
//      submitAftReturnCacheRepository2.collection.findOneAndUpdate(
//        filter = selector(bi.batchType, bi.batchNo),
//        update = modifier,
//        upsertOptions
//      ).toFuture().map(_ => (): Unit)
//
//    }
//    Future.sequence(seqFutureUpdateWriteResult).map(_ => (): Unit)
//  }
//
//  private def mongoCollectionInsertSessionDataBatch(submitAftReturnCacheRepository2: SubmitAftReturnCacheRepository, id: String,
//                                                    sessionId: String, sd: SessionData, batchSize: Int = 2): Future[Unit] = {
//    val selector: Bson = {
//      Filters.and(
//        Filters.eq(uniqueAftIdKey, id + sessionId),
//        Filters.eq(batchTypeKey, BatchType.SessionData.toString),
//        Filters.eq(batchNoKey, 1)
//      )
//    }
//    val modifier = Updates.combine(
//      set(idKey, id),
//      set("data", Codecs.toBson(Json.toJson(sd))),
//      set("batchSize", batchSize)
//    )
//
//    val upsertOptions = new FindOneAndUpdateOptions().upsert(true)
//
//    submitAftReturnCacheRepository2.collection.findOneAndUpdate(
//      filter = selector,
//      update = modifier,
//      upsertOptions
//    ).toFuture().map(_ => (): Unit)
//  }
//
  private val mockAppConfig = mock[AppConfig]
//
//  private val dummyJson = Json.obj("dummy" -> "value")
//  private val version = 1
//  private val accessMode = "dummy"
//  private val areSubmittedVersionsAvailable = false
//  private val id = "S24000000152020-04-01"
//  private val anotherSchemeId = "S24000000162020-04-01"
//  private val sessionId = "session-1"
//  private val anotherSessionId = "session-2"
//  private val uniqueAftId = id + sessionId
//  private val lockDetail = Some(LockDetail(name = "Billy Wiggins", "A123456"))
//  private val batchService = mock[BatchService]
//  private val collectionName = "aft-batches"
//
//  private def sessionData(sessionId: String, lockDetail: Option[LockDetail] = lockDetail) = SessionData(
//    sessionId = sessionId, lockDetail = lockDetail, version = version, accessMode = accessMode,
//    areSubmittedVersionsAvailable = false)
//
//  private def sessionDataBatch(sessionId: String, lockDetail: Option[LockDetail]): Set[BatchInfo] = {
//    val json = Json.toJson(sessionData(sessionId, lockDetail))
//    Set(BatchInfo(BatchType.SessionData, 1, json))
//  }
//
//  private def dbDocumentsAsSeqBatchInfo(s: Seq[JsValue]): Set[BatchInfo] = {
//    s.map { jsValue =>
//      val batchType = BatchType.getBatchType((jsValue \ "batchType").as[String])
//        .getOrElse(throw new RuntimeException("Unknown batch type"))
//      val batchNo = (jsValue \ "batchNo").as[Int]
//      val jsData = {
//        val t = jsValue \ "data"
//        batchType match {
//          case BatchType.Other => t.as[JsObject]
//          case BatchType.SessionData => t.as[JsObject]
//          case _ => t.as[JsArray]
//        }
//      }
//      BatchInfo(batchType, batchNo, jsData)
//    }.toSet
//  }
//
//  private def filterOnBatchTypeAndNo(batchType: BatchType, batchNo: Int): BatchInfo => Boolean =
//    bi => bi.batchType == batchType && bi.batchNo == batchNo
//
//  private val setOfFourChargeCMembersInTwoBatches: Set[BatchInfo] = {
//    val payloadOtherBatch = payloadOther
//    val jsArrayChargeC = payloadChargeTypeCEmployer(numberOfItems = 4)
//    Set(
//      BatchInfo(BatchType.Other, 1, payloadOtherBatch),
//      BatchInfo(BatchType.ChargeC, 1, JsArray(Seq(jsArrayChargeC(0), jsArrayChargeC(1)))),
//      BatchInfo(BatchType.ChargeC, 2, JsArray(Seq(jsArrayChargeC(2), jsArrayChargeC(3))))
//    )
//  }
//
//  private val fullSetOfBatchesToSaveToMongo: Set[BatchInfo] = {
//    val jsArrayChargeC = payloadChargeTypeCEmployer(numberOfItems = 5)
//    val jsArrayChargeD = payloadChargeTypeDMember(numberOfItems = 4)
//    val jsArrayChargeE = payloadChargeTypeEMember(numberOfItems = 2)
//    val jsArrayChargeG = payloadChargeTypeGMember(numberOfItems = 7)
//    Set(BatchInfo(BatchType.Other, 1,
//      payloadOther ++ payloadChargeTypeA ++ payloadChargeTypeB ++ payloadChargeTypeF ++ concatenateNodes(
//        Seq(payloadChargeTypeCMinusEmployers(numberOfItems = 5)), nodeNameChargeC) ++ concatenateNodes(
//        Seq(payloadChargeTypeDMinusMembers(numberOfItems = 4)), nodeNameChargeD) ++ concatenateNodes(
//        Seq(payloadChargeTypeEMinusMembers(numberOfItems = 2)), nodeNameChargeE) ++ concatenateNodes(
//        Seq(payloadChargeTypeGMinusMembers(numberOfItems = 7)), nodeNameChargeG)),
//      BatchInfo(BatchType.ChargeC, 1, JsArray(Seq(jsArrayChargeC(0), jsArrayChargeC(1)))),
//      BatchInfo(BatchType.ChargeC, 2, JsArray(Seq(jsArrayChargeC(2), jsArrayChargeC(3)))),
//      BatchInfo(BatchType.ChargeC, 3, JsArray(Seq(jsArrayChargeC(4)))),
//      BatchInfo(BatchType.ChargeD, 1, JsArray(Seq(jsArrayChargeD(0), jsArrayChargeD(1)))),
//      BatchInfo(BatchType.ChargeD, 2, JsArray(Seq(jsArrayChargeD(2), jsArrayChargeD(3)))),
//      BatchInfo(BatchType.ChargeE, 1, JsArray(Seq(jsArrayChargeE(0), jsArrayChargeE(1)))),
//      BatchInfo(BatchType.ChargeG, 1, JsArray(Seq(jsArrayChargeG(0), jsArrayChargeG(1)))),
//      BatchInfo(BatchType.ChargeG, 2, JsArray(Seq(jsArrayChargeG(2), jsArrayChargeG(3)))),
//      BatchInfo(BatchType.ChargeG, 3, JsArray(Seq(jsArrayChargeG(4), jsArrayChargeG(5)))),
//      BatchInfo(BatchType.ChargeG, 4, JsArray(Seq(jsArrayChargeG(6)))))
//  }
//
  private def buildRepository(mongoHost: String, mongoPort: Int): SubmitAftReturnCacheRepository = {
    val databaseName = "pension-scheme-accounting-for-tax"
    val mongoUri = s"mongodb://$mongoHost:$mongoPort/$databaseName?heartbeatFrequencyMS=1000&rm.failover=default"
    new SubmitAftReturnCacheRepository(MongoComponent(mongoUri), mockAppConfig)
  }
}



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

package repository

import com.google.inject.Inject
import com.mongodb.client.model.FindOneAndUpdateOptions
import config.AppConfig
import crypto.DataEncryptor
import models.{ChargeAndMember, LockDetail}
import org.mongodb.scala.bson.conversions.Bson
import org.mongodb.scala.model.*
import org.mongodb.scala.model.Updates.set
import play.api.Logging
import play.api.libs.json.*
import repository.model.SessionData
import services.BatchService
import services.BatchService.BatchType.Other
import services.BatchService.{BatchIdentifier, BatchInfo, BatchType}
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.{Codecs, PlayMongoRepository}

import java.time.format.DateTimeFormatter
import java.time.{LocalDateTime, LocalTime, ZoneId}
import java.util.concurrent.TimeUnit
import javax.inject.Singleton
import scala.concurrent.{ExecutionContext, Future}

object AftBatchedDataCacheRepository {

  private val uniqueAftIdKey = "uniqueAftId"
  private val batchTypeKey = "batchType"
  private val batchNoKey = "batchNo"
  private val idKey = "id"
  private val expireAtKey = "expireAt"

  private val indexes = Seq(
    IndexModel(
      keys = Indexes.ascending(uniqueAftIdKey, batchTypeKey, batchNoKey),
      indexOptions = IndexOptions().name("unique-aft-batch").unique(true)
    ),
    IndexModel(
      keys = Indexes.ascending(uniqueAftIdKey),
      indexOptions = IndexOptions().name("unique_Aft_Id")
    ),
    IndexModel(
      keys = Indexes.ascending(uniqueAftIdKey, batchTypeKey),
      indexOptions = IndexOptions().name("uniqueAftId_1_batchType_1")
    ),
    IndexModel(
      keys = Indexes.ascending(idKey, batchTypeKey, batchNoKey),
      indexOptions = IndexOptions().name("id_1_batchType_1_batchNo_1")
    ),
    IndexModel(
      keys = Indexes.ascending(expireAtKey),
      indexOptions = IndexOptions().name("dataExpiry")
        .expireAfter(0, TimeUnit.SECONDS)
        .unique(false)
    )
  )
}

@Singleton
class AftBatchedDataCacheRepository @Inject()(
                                               mongoComponent: MongoComponent,
                                               batchService: BatchService,
                                               appConfig: AppConfig,
                                               cipher: DataEncryptor
                                             )(implicit val ec: ExecutionContext)
  extends PlayMongoRepository[JsValue](
    collectionName = appConfig.mongoDBAFTBatchesCollectionName,
    mongoComponent = mongoComponent,
    domainFormat = implicitly,
    indexes = AftBatchedDataCacheRepository.indexes
  ) with Logging {

  import AftBatchedDataCacheRepository.*

  private def expireInSeconds(batchType: BatchType): LocalDateTime = {
    val ttlConfigItem = batchType match {
      case BatchType.SessionData => appConfig.mongoDBAFTBatchesTTL
      case _ => appConfig.mongoDBAFTBatchesMaxTTL
    }
    LocalDateTime.now(ZoneId.of("UTC"))
      .plusSeconds(ttlConfigItem)
  }

  private def userDataBatchSize: Int = appConfig.mongoDBAFTBatchesUserDataBatchSize

  private def createSessionDataBatch(sd: SessionData): BatchInfo = BatchInfo(BatchType.SessionData, 1, Json.toJson(sd).as[JsObject])

  private def getSessionDataBatch(
                                   id: String,
                                   sessionId: String,
                                   sessionDataToSaveToRepository: Option[SessionData] = None
                                 ): Future[Option[BatchInfo]] = {
    getSessionDataBatchFromRepository(id, sessionId)
      .flatMap { sessionDataBatchRetrievedFromRepository =>
        (sessionDataToSaveToRepository, sessionDataBatchRetrievedFromRepository) match {
          case (Some(sdToSave), None) => remove(id, sessionId).map(_ => Some(createSessionDataBatch(sdToSave)))
          case (Some(sdToSave), Some(SessionBatchInfo(_, _))) => Future.successful(Some(createSessionDataBatch(sdToSave)))
          case (None, Some(SessionBatchInfo(bi, storedBatchSize))) =>
            if (storedBatchSize == userDataBatchSize) {
              Future.successful(Some(bi))
            } else {
              val m = s"Batch size of $storedBatchSize not equal to configured batch size " +
                s"of $userDataBatchSize so will attempt to reconfigure data"
              logWithTime(m)
              logger.warn(m)
              reconfigureBatches(id, sessionId, bi)
            }
          case _ => remove(id, sessionId).map(_ => None)
        }
      }
  }

  private def reconfigureBatches(id: String, sessionId: String, sessionDataBatchInfo: BatchInfo): Future[Option[BatchInfo]] = {
    getBatchesFromRepository(id, Some(sessionId), excludeSessionDataBatch = true).flatMap {
      case None => remove(id, sessionId).map(_ => None)
      case Some(seqBatchInfo) =>
        val userData = batchService.createUserDataFullPayload(seqBatchInfo)
        val sessionData = Some(sessionDataBatchInfo.jsValue.as[SessionData])
        remove(id, sessionId).flatMap { _ =>
          saveToRepository(id, sessionId, None, userData, sessionData).map { _ =>
            Some(sessionDataBatchInfo)
          }
        }
    }
  }

  private def now: String = "[time " + DateTimeFormatter.ISO_LOCAL_TIME.format(LocalTime.now()) + "]"

  private def logWithTime(s: String): Unit = {
    logger.info(s"\n$s $now")
  }

  private def saveToRepository(
                                id: String,
                                sessionId: String,
                                batchIdentifier: Option[BatchIdentifier],
                                userData: JsValue,
                                sessionData: Option[SessionData]
                              )(implicit ec: ExecutionContext): Future[Unit] = {
    require(batchIdentifier.forall(_.batchType != BatchType.SessionData))
    logWithTime(s"Calling SaveToRepository with batchIdentifier $batchIdentifier in AFTBatchedDataCacheRepository")

    getSessionDataBatch(id, sessionId, sessionData).flatMap {
      case Some(sessionDataBatchInfo) =>
        val batchesExcludingSessionData = batchIdentifier match {
          case None =>
            batchService.createBatches(userDataFullPayload = userData.as[JsObject], userDataBatchSize = userDataBatchSize)
          case Some(BatchIdentifier(Other, _)) =>
            Set(BatchInfo(Other, 1, batchService.getOtherJsObject(userData.as[JsObject])))
          case Some(BatchIdentifier(batchType, batchNo)) =>
            batchService.getChargeTypeJsObjectForBatch(userData.as[JsObject], userDataBatchSize, batchType, batchNo).toSet[BatchInfo]
        }

        val lastBatchNos = batchIdentifier.fold(batchService.lastBatchNo(batchesExcludingSessionData))(_ => Set())
        val batches = Set(sessionDataBatchInfo) ++ batchesExcludingSessionData

        def selector(batchType: BatchType, batchNo: Int): Bson = {
          Filters.and(
            Filters.eq(uniqueAftIdKey, id + sessionId),
            Filters.eq(batchTypeKey, batchType.toString),
            Filters.eq(batchNoKey, batchNo)
          )
        }

        logWithTime(s"Updating/inserting batch(es) in AFTBatchedDataCacheRepository")

        val upsertOptions = new FindOneAndUpdateOptions().upsert(true)
        val setFutures = batches.map { bi =>
          collection.findOneAndUpdate(
            filter = selector(bi.batchType, bi.batchNo),
            update = jsonPayloadToSave(id, bi),
            upsertOptions
          ).toFuture().map(_ => (): Unit)
        }

        Future.sequence(setFutures).flatMap { _ =>
          Future.sequence(lastBatchNos.map(removeBatchesAfterBatchIdentifier(id, sessionId, _))).map { _ =>
            logWithTime(s"Finished updating/inserting batch(es)  in AFTBatchedDataCacheRepository")
          }
        }
      case _ =>
        logWithTime("Unable to save to Mongo repository as no session data found in repository or payload")
        Future.successful((): Unit)
    }
  }

  private def jsonPayloadToSave(id: String, batchInfo: BatchInfo): Bson = {
    val userDataBatchSizeJson = if (batchInfo.batchType == BatchType.SessionData) {
      Seq(set("batchSize", userDataBatchSize))
    } else {
      Nil
    }

    val seqUpdates = Seq(
      set(idKey, id),
      set("data", Codecs.toBson(cipher.encrypt(id, batchInfo.jsValue))),
      set("lastUpdated", LocalDateTime.now(ZoneId.of("UTC"))),
      set(expireAtKey, expireInSeconds(batchInfo.batchType))
    ) ++ userDataBatchSizeJson

    Updates.combine(
      seqUpdates *
    )
  }

  def save(
            id: String,
            sessionId: String,
            chargeAndMember: Option[ChargeAndMember],
            userData: JsValue
          )(implicit ec: ExecutionContext): Future[Unit] = {
    logWithTime("Calling save in AFTBatchedDataCacheRepository")
    saveToRepository(
      id = id,
      sessionId = sessionId,
      batchIdentifier = batchService.batchIdentifierForChargeAndMember(chargeAndMember, userDataBatchSize),
      userData = userData,
      sessionData = None
    )
  }

  def setSessionData(
                      id: String,
                      lockDetail: Option[LockDetail],
                      userData: JsValue,
                      sessionId: String,
                      version: Int,
                      accessMode: String,
                      areSubmittedVersionsAvailable: Boolean
                    )(implicit ec: ExecutionContext): Future[Unit] = {
    logWithTime("Calling setSessionData in AFTBatchedDataCacheRepository")
    saveToRepository(
      id = id,
      sessionId = sessionId,
      batchIdentifier = None,
      userData = userData,
      sessionData = Some(SessionData(sessionId, lockDetail, version, accessMode, areSubmittedVersionsAvailable))
    )
  }

  def getSessionData(sessionId: String, id: String)(implicit ec: ExecutionContext): Future[Option[SessionData]] = {
    logWithTime("Calling getSessionData in AFTBatchedDataCacheRepository")
    getSessionDataBatch(id, sessionId).flatMap {
      case Some(batchInfo) =>
        lockedBy(sessionId, id).map { lockDetail =>
          val sessionData = batchInfo.jsValue.asOpt[SessionData]
          sessionData.map {
            _.copy (lockDetail = lockDetail)
          }
        }
      case _ =>
        Future.successful(None)
    }
  }

  def get(id: String, sessionId: String)(implicit ec: ExecutionContext): Future[Option[JsValue]] = {
    logWithTime("Calling get in AFTBatchedDataCacheRepository")
    getSessionDataBatch(id, sessionId).flatMap {
      case Some(_) =>
        getBatchesFromRepository(id, Some(sessionId), excludeSessionDataBatch = true).flatMap {
          case None => Future.successful(None)
          case Some(seqBatchInfo) =>
            logWithTime(s"Get in AFTBatchedDataCacheRepository: Started reconstructing full payload from ${seqBatchInfo.size} batches")
            val fullPayload = Some(batchService.createUserDataFullPayload(seqBatchInfo))
            logWithTime(s"Get in AFTBatchedDataCacheRepository: Finished reconstructing full payload from ${seqBatchInfo.size} batches")
            Future.successful(fullPayload)
        }
      case _ => Future.successful(None)
    }
  }

  def lockedBy(sessionId: String, id: String)(implicit ec: ExecutionContext): Future[Option[LockDetail]] = {
    logWithTime("Calling lockedBy in AFTBatchedDataCacheRepository")
    getBatchesFromRepository(id = id, batchIdentifier = Some(BatchIdentifier(BatchType.SessionData, 1))).map {
      case None =>
        None
      case Some(seqBatchInfo) =>
        seqBatchInfo
          .flatMap(bi => bi.jsValue.asOpt[SessionData].toSeq)
          .find(sd => sd.lockDetail.isDefined && sd.sessionId != sessionId)
          .flatMap(_.lockDetail)
    }
  }

  def remove(id: String, sessionId: String)(implicit ec: ExecutionContext): Future[Unit] = {
    logWithTime(s"Removing document(s) from collection aft batched data cache id:$id")
    val selector = Filters.eq(uniqueAftIdKey, id + sessionId)
    collection.deleteMany(
      filter = selector
    ).toFuture().map(_ => (): Unit)
  }

  private def removeBatchesAfterBatchIdentifier(id: String, sessionId: String, batchIdentifier: BatchIdentifier)(implicit
                                                                                                                 ec: ExecutionContext): Future[Unit] = {
    logWithTime(s"Removing document(s) for batch type ${batchIdentifier.batchType.toString} with batchNo greater " +
      s"than last batch no ${batchIdentifier.batchNo} from collection aft batched data cache id:$id")

    val selector = batchIdentifier match {
      case BatchIdentifier(batchType, 0) =>
        Filters.and(
          Filters.eq(uniqueAftIdKey, id + sessionId),
          Filters.eq(batchTypeKey, batchType.toString)
        )
      case BatchIdentifier(batchType, batchNo) =>
        Filters.and(
          Filters.eq(uniqueAftIdKey, id + sessionId),
          Filters.eq(batchTypeKey, batchType.toString),
          Filters.gt(batchNoKey, batchNo)
        )
    }

    collection.deleteMany(
      filter = selector
    ).toFuture().map(_ => (): Unit)
  }

  private def transformToBatchInfo(batchJsValue: JsValue): Option[BatchInfo] = {
    val optBatchType = (batchJsValue \ batchTypeKey).asOpt[String].flatMap(BatchType.getBatchType)
    val optBatchNo = (batchJsValue \ batchNoKey).asOpt[Int]
    val optJsValue = (batchJsValue \ "data").asOpt[JsValue]
    (optBatchType, optBatchNo, optJsValue) match {
      case (Some(batchType), Some(batchNo), Some(jsValue)) =>
        Some(BatchInfo(batchType, batchNo, jsValue))
      case _ => None
    }
  }

  private def transformToBatchSize(batchJsValue: JsValue): Option[Int] = (batchJsValue \ "batchSize").asOpt[Int]

  private def findBatches(
                           id: String,
                           optSessionId: Option[String],
                           batchIdentifier: Option[BatchIdentifier],
                           excludeSessionDataBatch: Boolean = false
                         )(implicit ec: ExecutionContext): Future[List[JsValue]] = {
    val selector = (optSessionId, batchIdentifier) match {
      case (Some(sessionId), Some(BatchIdentifier(bt, bn))) =>
        Filters.and(
          Filters.eq(uniqueAftIdKey, id + sessionId),
          Filters.eq(batchTypeKey, bt.toString),
          Filters.eq(batchNoKey, bn)
        )
      case (None, Some(BatchIdentifier(bt, bn))) =>
        Filters.and(
          Filters.eq(idKey, id),
          Filters.eq(batchTypeKey, bt.toString),
          Filters.eq(batchNoKey, bn)
        )
      case (Some(sessionId), None) if excludeSessionDataBatch =>
        Filters.and(
          Filters.eq(uniqueAftIdKey, id + sessionId),
          Filters.ne(batchTypeKey, BatchType.SessionData.toString)
        )
      case (Some(sessionId), None) =>
        Filters.eq(uniqueAftIdKey, id + sessionId)
      case (None, None) =>
        Filters.eq(idKey, id)
    }
    collection.find(
      filter = selector
    ).toFuture().map(values => {
      values.map { value =>
        val encryptedValue = (value \ "data").as[JsValue]
        val decryptedValue = cipher.decrypt(id, encryptedValue)
        value.as[JsObject] + ("data" -> decryptedValue)
      }.toList
    })
  }

  private def getBatchesFromRepository(
                                        id: String,
                                        optSessionId: Option[String] = None,
                                        batchIdentifier: Option[BatchIdentifier] = None,
                                        excludeSessionDataBatch: Boolean = false
                                      )(implicit ec: ExecutionContext): Future[Option[Seq[BatchInfo]]] = {
    findBatches(id, optSessionId, batchIdentifier, excludeSessionDataBatch).map {
      case batches if batches.isEmpty =>
        None
      case batches =>
        val transformedBatches = batches.map(batchJsValue =>
          transformToBatchInfo(batchJsValue)
            .getOrElse(throw new RuntimeException(s"Unable to parse json:$batchJsValue"))
        )
        Some(transformedBatches)
    }
  }

  private def getSessionDataBatchFromRepository(
                                                 id: String,
                                                 sessionId: String
                                               )(implicit ec: ExecutionContext): Future[Option[SessionBatchInfo]] = {
    findBatches(id, Some(sessionId), Some(BatchIdentifier(BatchType.SessionData, 1))).map {
      case batches if batches.isEmpty => None
      case batches =>
        batches.headOption.map(batchJsValue =>
          Tuple2(
            transformToBatchInfo(batchJsValue),
            transformToBatchSize(batchJsValue)
          )
        ).flatMap { case (batchInfo, batchSize) => batchInfo.map(SessionBatchInfo(_, batchSize.getOrElse(0))) }
    }
  }

  private case class SessionBatchInfo(batchInfo: BatchInfo, batchSize: Int)
}

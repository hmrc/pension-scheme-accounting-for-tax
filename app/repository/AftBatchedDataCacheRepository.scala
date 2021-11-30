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

package repository

import com.google.inject.Inject
import config.AppConfig
import models.{ChargeAndMember, LockDetail}
import org.joda.time.{DateTimeZone, DateTime}
import org.slf4j.{Logger, LoggerFactory}
import play.api.libs.json._
import play.modules.reactivemongo.ReactiveMongoComponent
import reactivemongo.api.commands.WriteResult
import reactivemongo.api.indexes.{Index, IndexType}
import reactivemongo.bson.{BSONDocument, BSONObjectID}
import reactivemongo.play.json.ImplicitBSONHandlers._
import repository.model.SessionData
import services.BatchService
import services.BatchService.BatchType.Other
import services.BatchService.{BatchIdentifier, BatchInfo, BatchType}
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats

import java.time.LocalTime
import java.time.format.DateTimeFormatter
import scala.concurrent.{ExecutionContext, Future}

class AftBatchedDataCacheRepository @Inject()(
                                        mongoComponent: ReactiveMongoComponent,
                                        batchService: BatchService,
                                        appConfig: AppConfig
                                      )(implicit val ec: ExecutionContext)
  extends ReactiveRepository[JsValue, BSONObjectID](
    appConfig.mongoDBAFTBatchesCollectionName,
    mongoComponent.mongoConnector.db,
    implicitly
  ) {

  override val logger: Logger = LoggerFactory.getLogger("AftBatchedDataCacheRepository")

  private implicit val dateFormat: Format[DateTime] = ReactiveMongoFormats.dateTimeFormats

  private def expireInSeconds(batchType:BatchType): DateTime = {
    val ttlConfigItem = batchType match {
      case BatchType.SessionData => appConfig.mongoDBAFTBatchesTTL
      case _ => appConfig.mongoDBAFTBatchesMaxTTL
    }
    DateTime.now(DateTimeZone.UTC).plusSeconds(ttlConfigItem)
  }

  val collectionIndexes = Seq(
    Index(
      Seq(
        ("uniqueAftId", IndexType.Ascending),
        ("batchType", IndexType.Ascending),
        ("batchNo", IndexType.Ascending)
      ), Some("unique-aft-batch"), unique = true, background = true),
    Index(Seq(("batchType", IndexType.Ascending), ("batchNo", IndexType.Ascending)), Some("aft-batch"), unique = false, background = true),
    Index(Seq(("uniqueAftId", IndexType.Ascending)), Some("unique_Aft_Id"), unique = false, background = true),
    Index(Seq(("id", IndexType.Ascending)), Some("srn_startDt_key"), background = true),
    Index(Seq(("expireAt", IndexType.Ascending)), Some("dataExpiry"), unique = false, options = BSONDocument("expireAfterSeconds" -> 0))
  )

  (for {
    _ <- createIndex(collectionIndexes)
  } yield {
    ()
  }) recoverWith {
    case t: Throwable => Future.successful(logger.error(s"Error creating indexes on collection ${collection.name}", t))
  } andThen {
    case _ => CollectionDiagnostics.logCollectionInfo(collection)
  }

  private def createIndex(indexes: Seq[Index]): Future[Seq[Boolean]] = {
    Future.sequence(
      indexes.map { index =>
        collection.indexesManager.ensure(index) map { result =>
          logger.debug(s"Index $index was created successfully and result is: $result")
          result
        } recover {
          case e: Exception => logger.error(s"Failed to create index $index", e)
            false
        }
      }
    )
  }

  private def userDataBatchSize:Int = appConfig.mongoDBAFTBatchesUserDataBatchSize

  private def getSessionDataBatch(
    id: String,
    sessionId: String,
    sessionDataToSaveToRepository: Option[SessionData] = None
  ):Future[Option[BatchInfo]] = {
    getSessionDataBatchFromRepository(id, sessionId)
      .flatMap { sessionDataBatchRetrievedFromRepository =>
      (sessionDataToSaveToRepository, sessionDataBatchRetrievedFromRepository) match {
        case (Some(sdToSave), None) =>
          remove(id, sessionId).map(_ => Some(BatchInfo(BatchType.SessionData, 1, Json.toJson(sdToSave).as[JsObject])))
        case (Some(sdToSave), Some(SessionBatchInfo(_, _))) =>
          Future.successful(Some(BatchInfo(BatchType.SessionData, 1, Json.toJson(sdToSave).as[JsObject])))
        case (None, Some(SessionBatchInfo(bi, storedBatchSize))) =>
          if (storedBatchSize == userDataBatchSize) {
            Future.successful(Some(bi))
          } else {
            logger.warn(s"Batch size of $storedBatchSize not equal to configured batch size " +
              s"of $userDataBatchSize so will attempt to reconfigure data")
            reconfigureBatches(id, sessionId, bi)
          }
        case _ => remove(id, sessionId).map(_ => None)
      }
    }
  }

  private def reconfigureBatches(id: String, sessionId: String, sessionDataBatchInfo:BatchInfo):Future[Option[BatchInfo]]= {
    getBatchesFromRepository(id, Some(sessionId), excludeSessionDataBatch = true).flatMap {
      case None => remove(id, sessionId).map(_ => None)
      case Some(seqBatchInfo) =>
        val userData = batchService.createUserDataFullPayload(seqBatchInfo)
        val sessionData = Some(sessionDataBatchInfo.jsValue.as[SessionData])
        remove(id, sessionId).flatMap{ _ =>
          saveToRepository(id, sessionId, None, userData, sessionData).map {
            case true => Some(sessionDataBatchInfo)
            case false => None
          }
        }
    }
  }

  private def now:String = "[time" + DateTimeFormatter.ISO_LOCAL_TIME.format(LocalTime.now()) + "]"

  private def log(s:String):Unit = println( s"\n$s $now")

  private def log[A](s:String, a:A):A = {
    println( s"\n$s $now")
    a
  }

  private def saveToRepository(
    id: String,
    sessionId: String,
    batchIdentifier: Option[BatchIdentifier],
    userData: JsValue,
    sessionData: Option[SessionData]
  )(implicit ec: ExecutionContext): Future[Boolean] = {
    require( batchIdentifier.forall(_.batchType != BatchType.SessionData) )
    log(s"Calling SaveToRepository with batchIdentifier $batchIdentifier in AFTBatchedDataCacheRepository")

    getSessionDataBatch(id, sessionId, sessionData).flatMap{
      case Some(sessionDataBatchInfo) =>
        val batchesExcludingSessionData = batchIdentifier match {
          case None =>
            batchService.createBatches(userDataFullPayload = userData.as[JsObject], userDataBatchSize = userDataBatchSize)
          case Some(BatchIdentifier(Other, _)) =>
            Set(BatchInfo(Other, 1, batchService.getOtherJsObject(userData.as[JsObject])))
          case Some(BatchIdentifier(batchType, batchNo)) =>
            batchService.getChargeTypeJsObjectForBatch(userData.as[JsObject], userDataBatchSize, batchType, batchNo).toSet[BatchInfo]
        }

        val lastBatchNos = batchIdentifier.fold(batchService.lastBatchNo(batchesExcludingSessionData))(_=>Set())

        val batches = Set(sessionDataBatchInfo) ++ batchesExcludingSessionData
        def selector(batchType: BatchType, batchNo: Int): BSONDocument =
          BSONDocument("uniqueAftId" -> (id + sessionId), "batchType" -> batchType.toString, "batchNo" -> batchNo)

        log( s"Updating/inserting batch(es) in AFTBatchedDataCacheRepository")
        val setFutures = batches.map{ bi =>
          val modifier = BSONDocument.apply("$set" -> jsonPayloadToSave(id, bi))
          collection.update.one(selector(bi.batchType, bi.batchNo), modifier, upsert = true)
        }
        val allFutures = setFutures ++ lastBatchNos.map(removeBatchesGT(id, sessionId, _))
        Future.sequence(allFutures).map(_.forall(_.ok)).map { b =>
          log( s"Finished updating/inserting batch(es)  in AFTBatchedDataCacheRepository", b)
        }
      case _ =>
        log( "UNABLE TO SAVE TO REPO AS NO SESSION DATA FOUND IN REPO OR TO SAVE in AFTBatchedDataCacheRepository")
        Future.successful(false)
    }
  }

  private def jsonPayloadToSave(id: String, batchInfo:BatchInfo):JsObject = {
    val userDataBatchSizeJson:JsObject = if (batchInfo.batchType == BatchType.SessionData) {
      Json.obj(
        "batchSize" -> userDataBatchSize
      )
    } else {
      Json.obj()
    }
    Json.obj(
      "id" -> id,
      "data" -> batchInfo.jsValue,
      "lastUpdated" -> DateTime.now(DateTimeZone.UTC),
      "expireAt" -> expireInSeconds(batchInfo.batchType)
    ) ++ userDataBatchSizeJson
  }

  def save(
    id: String,
    sessionId: String,
    chargeAndMember: Option[ChargeAndMember],
    userData: JsValue
  )(implicit ec: ExecutionContext): Future[Boolean] = {
    log("Calling save in AFTBatchedDataCacheRepository")
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
                    )(implicit ec: ExecutionContext): Future[Boolean] = {
    log("Calling setSessionData in AFTBatchedDataCacheRepository")
    saveToRepository(
      id = id,
      sessionId = sessionId,
      batchIdentifier = None,
      userData = userData,
      sessionData = Some(SessionData(sessionId, lockDetail, version, accessMode, areSubmittedVersionsAvailable))
    )
  }

  def getSessionData(sessionId: String, id: String)(implicit ec: ExecutionContext): Future[Option[SessionData]] = {
    log("Calling getSessionData in AFTBatchedDataCacheRepository")
    getSessionDataBatch(id, sessionId, None).flatMap {
      case Some(batchInfo) =>
        lockedBy(sessionId, id).map { lockDetail =>
          val sessionData = batchInfo.jsValue.asOpt[SessionData]
          sessionData.map{ _ copy(lockDetail = lockDetail)}
        }
      case _ =>
        Future.successful(None)
    }
  }

  def get(id: String, sessionId: String)(implicit ec: ExecutionContext): Future[Option[JsValue]] = {
    log("Calling get in AFTBatchedDataCacheRepository")
    getSessionDataBatch(id, sessionId).flatMap {
      case Some(_) =>
        getBatchesFromRepository(id, Some(sessionId), excludeSessionDataBatch = true).flatMap {
          case None => Future.successful(None)
          case Some(seqBatchInfo) =>
            log( s"Get: Started reconstructing full payload from batches")
            val fullPayload = Some(batchService.createUserDataFullPayload(seqBatchInfo))
            log( s"Get: Finished reconstructing full payload from batches")
            Future.successful(fullPayload)
        }
      case _ => Future.successful(None)
    }
  }

  def lockedBy(sessionId: String, id: String)(implicit ec: ExecutionContext): Future[Option[LockDetail]] = {
    log("Calling lockedBy in AFTBatchedDataCacheRepository")
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

  def remove(id: String, sessionId: String)(implicit ec: ExecutionContext): Future[Boolean] = {
    log(s"Removing document(s) from collection ${collection.name} id:$id")
    val selector = BSONDocument("uniqueAftId" -> (id + sessionId))
    collection.delete.one(selector).map(_.ok)
  }

  private def removeBatchesGT(id: String, sessionId: String, batchInfo: BatchIdentifier)(implicit ec: ExecutionContext): Future[WriteResult] = {
    log(s"Removing document(s) greater than last batch no from collection ${collection.name} id:$id")
    val selector = BSONDocument(
      "uniqueAftId" -> (id + sessionId),
      "batchType" -> batchInfo.batchType.toString,
      "batchNo" -> BSONDocument( "$gt" -> batchInfo.batchNo)
    )

    collection.delete.one(selector)
  }

  private def transformToBatchInfo(batchJsValue:JsValue):Option[BatchInfo] = {
    val optBatchType = (batchJsValue \ "batchType").asOpt[String].flatMap(BatchType.getBatchType)
    val optBatchNo = (batchJsValue \ "batchNo").asOpt[Int]
    val optJsValue = (batchJsValue \ "data").asOpt[JsValue]
    (optBatchType, optBatchNo, optJsValue) match {
      case (Some(batchType), Some(batchNo), Some(jsValue)) =>
        Some(BatchInfo(batchType, batchNo, jsValue))
      case _ => None
    }
  }

  private def transformToBatchSize(batchJsValue:JsValue):Option[Int] = (batchJsValue \ "batchSize").asOpt[Int]

  private def findBatches(
    id: String,
    optSessionId: Option[String],
    batchIdentifier: Option[BatchIdentifier],
    excludeSessionDataBatch: Boolean = false
  )(implicit ec: ExecutionContext):Future[List[JsValue]] = {
    (optSessionId, batchIdentifier) match {
      case (Some(sessionId), Some(BatchIdentifier(bt, bn))) =>
        find("uniqueAftId" -> (id + sessionId), "batchType" -> bt.toString, "batchNo" -> bn)
      case (None, Some(BatchIdentifier(bt, bn))) =>
        find("batchType" -> bt.toString, "batchNo" -> bn)
      case (Some(sessionId), None) if excludeSessionDataBatch =>
        find("uniqueAftId" -> (id + sessionId),
          "batchType"-> BSONDocument( "$ne" -> BatchType.SessionData.toString))
      case (Some(sessionId), None) => find("uniqueAftId" -> (id + sessionId))
      case (None, None) => find("id" -> id)
    }
  }

  private def getBatchesFromRepository(
    id: String,
    optSessionId: Option[String] = None,
    batchIdentifier: Option[BatchIdentifier] = None,
    excludeSessionDataBatch:Boolean = false
  )(implicit ec: ExecutionContext):Future[Option[Seq[BatchInfo]]] = {
    findBatches(id, optSessionId, batchIdentifier, excludeSessionDataBatch).map {
      case batches if batches.isEmpty => None
      case batches =>
        val transformedBatches = batches.map( batchJsValue =>
          transformToBatchInfo(batchJsValue)
            .getOrElse(throw new RuntimeException(s"Unable to parse json:$batchJsValue"))
        )
        Some(transformedBatches)
    }
  }

  private def getSessionDataBatchFromRepository(
    id: String,
    sessionId: String
  )(implicit ec: ExecutionContext):Future[Option[SessionBatchInfo]] = {
    findBatches(id, Some(sessionId), Some(BatchIdentifier(BatchType.SessionData, 1))).map {
      case batches if batches.isEmpty => None
      case batches =>
        batches.headOption.map(batchJsValue =>
          Tuple2(
            transformToBatchInfo(batchJsValue),
            transformToBatchSize(batchJsValue)
          )
        ).flatMap{ case (batchInfo, batchSize) => batchInfo.map(SessionBatchInfo(_, batchSize.getOrElse(0)))}
    }
  }

  private case class SessionBatchInfo(batchInfo:BatchInfo, batchSize:Int)
}

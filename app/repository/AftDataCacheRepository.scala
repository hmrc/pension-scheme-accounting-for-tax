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
import models.LockDetail
import org.joda.time.{DateTimeZone, DateTime}
import org.slf4j.{Logger, LoggerFactory}
import play.api.Configuration
import play.api.libs.json._
import play.modules.reactivemongo.ReactiveMongoComponent
import reactivemongo.api.{ReadPreference, Cursor}
import reactivemongo.api.indexes.{Index, IndexType}
import reactivemongo.bson.{BSONDocument, BSONObjectID}
import reactivemongo.play.json.ImplicitBSONHandlers._
import repository.model.{SessionData, AftDataCache}
import services.BatchService
import services.BatchService.{BatchType, BatchInfo}
import uk.gov.hmrc.mongo.ReactiveRepository

import scala.concurrent.{ExecutionContext, Future}

class AftDataCacheRepository @Inject()(
                                        mongoComponent: ReactiveMongoComponent,
                                        configuration: Configuration,
                                        batchService: BatchService
                                      )(implicit val ec: ExecutionContext)
  extends ReactiveRepository[JsValue, BSONObjectID](
    configuration.get[String](path = "mongodb.aft-cache.aft-journey.name"),
    mongoComponent.mongoConnector.db,
    implicitly
  ) {

  override val logger: Logger = LoggerFactory.getLogger("AftDataCacheRepository")

  private def expireInSeconds: DateTime = DateTime.now(DateTimeZone.UTC).
    plusSeconds(configuration.get[Int](path = "mongodb.aft-cache.aft-journey.timeToLiveInSeconds"))

  val collectionIndexes = Seq(
    Index(Seq(("uniqueAftId", IndexType.Ascending)), Some("unique_Aft_Id"), unique = false, background = true),
    Index(Seq(("id", IndexType.Ascending)), Some("srn_startDt_key"), background = true),
    Index(Seq(("expireAt", IndexType.Ascending)), Some("dataExpiry"), unique = true, options = BSONDocument("expireAfterSeconds" -> 0))
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

  private def saveToRepository(
    id: String,
    userData: JsValue,
    sessionId: String,
    sessionData: Option[SessionData]
  )(implicit ec: ExecutionContext): Future[Boolean] = {
    logger.debug("Calling saveToRepository in AFT Data Cache Repository")

    val batches = batchService.createBatches(
      userDataFullPayload = userData.as[JsObject],
      userDataBatchSize = 2,
      sessionDataPayload = sessionData.map{ sd =>
        Json.obj("data" -> Json.toJson(sd).as[JsObject])
      }
    )

    def selector(batchType: BatchType, batchNo: Int): BSONDocument = BSONDocument(
      "uniqueAftId" -> (id + sessionId),
      "batchType" -> batchType.toString,
      "batchNo" -> batchNo
    )

    val setFutures = batches.map{ bi =>
      val modifier = BSONDocument("$set" -> Json.obj("data" -> bi.jsValue))
      collection.update.one(selector(bi.batchType, bi.batchNo), modifier, upsert = true)
    }

    Future.sequence(setFutures).map(_.forall(_.ok))
  }

  def save(id: String, userData: JsValue, sessionId: String)(implicit ec: ExecutionContext): Future[Boolean] =
    saveToRepository(
      id = id,
      userData = userData,
      sessionId = sessionId,
      sessionData = None
    )

  def setSessionData(
                      id: String,
                      lockDetail: Option[LockDetail],
                      userData: JsValue,
                      sessionId: String,
                      version: Int,
                      accessMode: String,
                      areSubmittedVersionsAvailable: Boolean
                    )(implicit ec: ExecutionContext): Future[Boolean] = {
    logger.debug(s"Calling setSessionData in AFT Cache")

    saveToRepository(
      id = id,
      userData = userData,
      sessionId = sessionId,
      sessionData = Some(SessionData(sessionId, lockDetail, version, accessMode, areSubmittedVersionsAvailable))
    )
  }

  private def transformToBatchInfo(batchJsValue:JsValue):Option[BatchInfo] = {
    val optBatchType = (batchJsValue \ "batchType").asOpt[String].flatMap(BatchType.getBatchType)
    val optBatchNo = (batchJsValue \ "batchNo").asOpt[Int]
    val optJsValue = (batchJsValue \ "data").asOpt[JsValue]
    println(s"\nTransformToBatchInfo: batchJsValue = $batchJsValue")
    println(s"  ... parsed as: $optBatchType, $optBatchNo, $optJsValue")
    (optBatchType, optBatchNo, optJsValue) match {
      case (Some(batchType), Some(batchNo), Some(jsValue)) =>
        Some(BatchInfo(batchType, batchNo, jsValue))
      case _ => None
    }
  }

  private def getBatchesFromRepository(
    id: String,
    optSessionId: Option[String] = None,
    batchTypeAndNo: Option[(BatchType, Int)] = None
  )(implicit ec: ExecutionContext):Future[Option[Seq[BatchInfo]]] = {
    val findResults = (optSessionId, batchTypeAndNo) match {
      case (Some(sessionId), Some(Tuple2(bt, bn))) =>
        println("\nGetBatchesFromRepository: searching with batch type")
        find("uniqueAftId" -> (id + sessionId), "batchType" -> bt.toString, "batchNo" -> bn)
      case (Some(sessionId), None) =>
        println("\nGetBatchesFromRepository: searching without batch type")
        find("uniqueAftId" -> (id + sessionId))
      case (None, None) =>
        find("id" -> id)
    }
    findResults.map {
      case batches if batches.isEmpty =>
        println("\nGetBatchesFromRepository: no batches found")
        None
      case batches =>
        println("\nGetBatchesFromRepository: Batches found")
        val transformedBatches = batches.map{ batchJsValue =>
          transformToBatchInfo(batchJsValue).getOrElse(throw new RuntimeException(s"Unable to parse json:$batchJsValue")
        }
        println(s"  ... GetBatchesFromRepository: Batches found: $transformedBatches")
        Some(transformedBatches)
    }
  }

  def getSessionData(sessionId: String, id: String)(implicit ec: ExecutionContext): Future[Option[SessionData]] = {
    logger.debug("Calling getSessionData in AFT Cache")
    // TODO: Could do this with only 1 retrieval from Mongo instead of 2
    getBatchesFromRepository(id, Some(sessionId), Some(BatchType.SessionData, 1)).flatMap {
      case Some(Seq(batchInfo)) =>
        lockedBy(sessionId, id).map { lockDetail =>
          val sessionData = batchInfo.jsValue.asOpt[SessionData]
          sessionData.map{ _ copy(lockDetail = lockDetail)}
        }
      case _ => Future.successful(None)
    }
  }

  def get(id: String, sessionId: String)(implicit ec: ExecutionContext): Future[Option[JsValue]] = {
    logger.debug("Calling get in AFT Cache")
    getBatchesFromRepository(id, Some(sessionId)).map {
      case None => None
      case Some(seqBatchInfo) => Some(batchService.createUserDataFullPayload(seqBatchInfo))
    }
  }

  def lockedBy(sessionId: String, id: String)(implicit ec: ExecutionContext): Future[Option[LockDetail]] = {
    logger.debug("Calling lockedBy in AFT Cache")

    getBatchesFromRepository(
      id = id,
      batchTypeAndNo = Some(Tuple2(BatchType.SessionData, 1))
    ).map {
      case None => None
      case Some(seqBatchInfo) =>
        seqBatchInfo
          .flatMap(bi => (bi.jsValue \ "data").asOpt[SessionData].toSeq)
          .find(sd => sd.lockDetail.isDefined && sd.sessionId != sessionId)
          .flatMap(_.lockDetail)
    }
  }

  def remove(id: String, sessionId: String)(implicit ec: ExecutionContext): Future[Boolean] = {
    logger.warn(s"Removing document(s) from collection ${collection.name} id:$id")
    val selector = BSONDocument("uniqueAftId" -> (id + sessionId))
    collection.delete.one(selector).map(_.ok)
  }

  // TODO: Change this
  def removeWithSessionId(sessionId: String)(implicit ec: ExecutionContext): Future[Boolean] = {
    logger.warn(s"Removing all document(s) with session id:$sessionId")
    val selector = BSONDocument("lockedBy.sessionId" -> sessionId)
    collection.delete.one(selector).map(_.ok)
  }
}

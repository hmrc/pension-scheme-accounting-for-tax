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
import services.BatchService.BatchType
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
    Index(Seq(("uniqueAftId", IndexType.Ascending)), Some("unique_Aft_Id"), unique = true, background = true),
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
  //
  //private def saveToRepository(
  //  id: String,
  //  userData: JsValue,
  //  sessionId: String,
  //  sessionData: Option[SessionData]
  //)(implicit ec: ExecutionContext): Future[Boolean] = {
  //  logger.debug("Calling saveToRepository in AFT Data Cache Repository")
  //  val document: JsValue = Json.toJson(
  //    AftDataCache.applyDataCache(
  //      id = id,
  //      sessionData = sessionData,
  //      data = userData,
  //      expireAt = expireInSeconds
  //    )
  //  )
  //  val selector = BSONDocument("uniqueAftId" -> (id + sessionId))
  //  val modifier = BSONDocument("$set" -> document)
  //  collection.update.one(selector, modifier, upsert = true).map(_.ok)
  //  // TODO: Add lock/session data to Other batch
  //
  //  // TODO: Change to retrieve and join batches
  //
  //  Future.successful(false)
  //}

  private def saveToRepository(
    id: String,
    userData: JsValue,
    sessionId: String,
    sessionData: Option[SessionData]
  )(implicit ec: ExecutionContext): Future[Boolean] = {
    logger.debug("Calling saveToRepository in AFT Data Cache Repository")

    val batches = batchService.createBatches(
      userDataPayload = userData.as[JsObject],
      userDataBatchSize = 2,
      sessionData.map(Json.toJson(_).as[JsObject])
    )

    def selector(batchType: BatchType, batchNo: Int): BSONDocument = BSONDocument(
      "uniqueAftId" -> (id + sessionId),
      "batchType" -> batchType.toString,
      "batchNo" -> batchNo
    )

    Future.traverse(batches) { bi =>
      val modifier = BSONDocument("$set" -> bi.jsValue)
      collection.update.one(selector(bi.batchType, bi.batchNo), modifier, upsert = true)
    }.map(_.forall(_.ok))
    Future.successful(false)
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

  // TODO: This will need to get the session/lock data from the Other batch
  def getSessionData(sessionId: String, id: String)(implicit ec: ExecutionContext): Future[Option[SessionData]] = {
    logger.debug("Calling getSessionData in AFT Cache")

    collection.find(BSONDocument("uniqueAftId" -> (id + sessionId)), projection = Option.empty[JsObject]).one[AftDataCache]
      .flatMap {
        case None => Future.successful(None)
        case Some(dc) =>
          lockedBy(sessionId, id).map { lockDetail =>
            dc.sessionData.map { oo =>
              oo copy (lockDetail = lockDetail)
            }
          }
      }
  }

  // TODO: This will need to get the data from the batches and join them back together
  def get(id: String, sessionId: String)(implicit ec: ExecutionContext): Future[Option[JsValue]] = {
    logger.debug("Calling get in AFT Cache")
    collection.find(BSONDocument("uniqueAftId" -> (id + sessionId)), projection = Option.empty[JsObject]).one[AftDataCache].map {
      _.map {
        dataEntry =>
          dataEntry.data
      }
    }
  }

  // TODO: This will need to get the lock from the Other batch
  def lockedBy(sessionId: String, id: String)(implicit ec: ExecutionContext): Future[Option[LockDetail]] = {
    logger.debug("Calling lockedBy in AFT Cache")
    val documentsForReturnAndQuarter = collection
      .find(BSONDocument("id" -> id), projection = Option.empty[JsObject])
      .cursor[AftDataCache](ReadPreference.primary)
      .collect[List](-1, Cursor.FailOnError[List[AftDataCache]]())

    documentsForReturnAndQuarter.map {
      _.find(_.sessionData.exists(sd => sd.lockDetail.isDefined && sd.sessionId != sessionId))
        .flatMap {
          _.sessionData.flatMap(_.lockDetail)
        }
    }
  }

  // TODO: This will need to remove all batches
  def remove(id: String, sessionId: String)(implicit ec: ExecutionContext): Future[Boolean] = {
    logger.warn(s"Removing row from collection ${collection.name} id:$id")
    val selector = BSONDocument("uniqueAftId" -> (id + sessionId))
    collection.delete.one(selector).map(_.ok)
  }

  // TODO: This will need to use batches
  def removeWithSessionId(sessionId: String)(implicit ec: ExecutionContext): Future[Boolean] = {
    logger.warn(s"Removing all rows with session id:$sessionId")
    val selector = BSONDocument("lockedBy.sessionId" -> sessionId)
    collection.delete.one(selector).map(_.ok)
  }
}

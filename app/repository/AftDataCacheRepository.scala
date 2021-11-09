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
import uk.gov.hmrc.mongo.ReactiveRepository

import java.time.LocalDateTime
import java.time.format.{FormatStyle, DateTimeFormatter}
import scala.concurrent.{ExecutionContext, Future}

class AftDataCacheRepository @Inject()(
                                        mongoComponent: ReactiveMongoComponent,
                                        configuration: Configuration
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

  def save(id: String, userData: JsValue, sessionId: String)(implicit ec: ExecutionContext): Future[Boolean] = {
    logger.debug("Calling Save in AFT Cache")
    val document: JsValue = Json.toJson(AftDataCache.applyDataCache(
      id = id, None, data = userData, expireAt = expireInSeconds))
    val selector = BSONDocument("uniqueAftId" -> (id + sessionId))
    val modifier = BSONDocument("$set" -> document)
    collection.update.one(selector, modifier, upsert = true).map(_.ok)
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
    logger.debug(s"Calling setSessionData in AFT Cache")
    logger.warn(s"Calling setSessionData in AFT Cache: request body length is ${userData.toString.length}")
    val document: JsValue = Json.toJson(
      AftDataCache.applyDataCache(
        id = id,
        Some(SessionData(sessionId, lockDetail, version, accessMode, areSubmittedVersionsAvailable)),
        data = userData, expireAt = expireInSeconds
      )
    )

    //waitAWhile

    val selector = BSONDocument("uniqueAftId" -> (id + sessionId))
    println(s"\nSET SESSION DATA - before creation of modifier. Time = $now")
    val modifier = BSONDocument("$set" -> document)
    println(s"\nSET SESSION DATA - before update of Mongo collection. Time = $now")
    collection.update.one(selector, modifier, upsert = true).map{ result =>
      println(s"\nSET SESSION DATA - after update of Mongo collection. Time = $now and result = ${result.ok}")
      result.ok
    }
  }
  private def waitAWhile:Unit = {
      println(s"\nWaiting a while. Going to sleep at $now")
      Thread.sleep(45000)
      println(s"\nWoken up at $now")
  }
  private def now: String =
    LocalDateTime.now.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.MEDIUM))

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

  def get(id: String, sessionId: String)(implicit ec: ExecutionContext): Future[Option[JsValue]] = {
    logger.debug("Calling get in AFT Cache")
    collection.find(BSONDocument("uniqueAftId" -> (id + sessionId)), projection = Option.empty[JsObject]).one[AftDataCache].map {
      _.map {
        dataEntry =>
          dataEntry.data
      }
    }
  }

  def remove(id: String, sessionId: String)(implicit ec: ExecutionContext): Future[Boolean] = {
    logger.warn(s"Removing row from collection ${collection.name} id:$id")
    val selector = BSONDocument("uniqueAftId" -> (id + sessionId))
    collection.delete.one(selector).map(_.ok)
  }

  def removeWithSessionId(sessionId: String)(implicit ec: ExecutionContext): Future[Boolean] = {
    logger.warn(s"Removing all rows with session id:$sessionId")
    val selector = BSONDocument("lockedBy.sessionId" -> sessionId)
    collection.delete.one(selector).map(_.ok)
  }
}

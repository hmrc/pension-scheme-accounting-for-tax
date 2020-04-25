/*
 * Copyright 2020 HM Revenue & Customs
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
import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import play.api.libs.json._
import play.api.Configuration
import play.api.Logger
import play.modules.reactivemongo.ReactiveMongoComponent
import reactivemongo.api.Cursor
import reactivemongo.api.ReadPreference
import reactivemongo.api.indexes.Index
import reactivemongo.api.indexes.IndexType
import reactivemongo.bson.BSONDocument
import reactivemongo.bson.BSONObjectID
import reactivemongo.play.json.ImplicitBSONHandlers._
import repository.model.DataCache
import repository.model.SessionData
import uk.gov.hmrc.mongo.ReactiveRepository

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

class DataCacheRepository @Inject()(
                                     mongoComponent: ReactiveMongoComponent,
                                     configuration: Configuration
                                   )(implicit val ec: ExecutionContext) extends ReactiveRepository[JsValue, BSONObjectID](
  configuration.get[String](path = "mongodb.aft-cache.aft-journey.name"),
  mongoComponent.mongoConnector.db,
  implicitly
) {
  private def expireInSeconds: DateTime = DateTime.now(DateTimeZone.UTC).
    plusSeconds(configuration.get[Int](path = "mongodb.aft-cache.aft-journey.timeToLiveInSeconds"))

  val collectionIndexes = Seq(
    Index(key = Seq(("uniqueAftId", IndexType.Ascending)), name = Some("unique_Aft_Id"), background = true, unique = true),
    Index(key = Seq(("id", IndexType.Ascending)), name = Some("srn_startDt_key"), background = true),
    Index(key = Seq(("expireAt", IndexType.Ascending)), name = Some("dataExpiry"), background = true
      , options = BSONDocument("expireAfterSeconds" -> 0))
  )

  (for {
    _ <- createIndex(collectionIndexes)
  } yield {
    ()
  }) recoverWith {
    case t: Throwable => Future.successful(Logger.error(s"Error creating indexes on collection ${collection.name}", t))
  } andThen {
    case _ => CollectionDiagnostics.logCollectionInfo(collection)
  }


  private def createIndex(indexes: Seq[Index]): Future[Seq[Boolean]] = {
    Future.sequence(
      indexes.map { index =>
        collection.indexesManager.ensure(index) map { result =>
          Logger.debug(message = s"Index $index was created successfully and result is: $result")
          result
        } recover {
          case e: Exception => Logger.error(message = s"Failed to create index $index", e)
            false
        }
      }
    )
  }

  // TODO: Don't overwrite session data - it may not be - test it!!
  def save(id: String, userData: JsValue, sessionId: String)(implicit ec: ExecutionContext): Future[Boolean] = {
    Logger.debug("Calling Save in AFT Cache")
    val document: JsValue = Json.toJson(DataCache.applyDataCache(
      id = id, None, data = userData, expireAt = expireInSeconds))
    val selector = BSONDocument("uniqueAftId" -> (id + sessionId))
    val modifier = BSONDocument("$set" -> document)
    collection.update.one(selector, modifier, upsert = true).map(_.ok)
  }

  // TODO: if locking then check if already locked to someone else and if so throw exception
  def setSessionData(id: String, name: Option[String], userData: JsValue, sessionId: String,
                     version: Int, accessMode: String)(implicit ec: ExecutionContext): Future[Boolean] = {
    Logger.debug("Calling setSessionData in AFT Cache")
    val document: JsValue = Json.toJson(
      DataCache.applyDataCache(
        id = id,
        Some(SessionData(sessionId, name, version, accessMode)),
        data = userData, expireAt = expireInSeconds
      )
    )
    val selector = BSONDocument("uniqueAftId" -> (id + sessionId))
    val modifier = BSONDocument("$set" -> document)
    collection.update.one(selector, modifier, upsert = true).map(_.ok)
  }

  def getSessionData(sessionId: String, id: String)(implicit ec: ExecutionContext): Future[Option[SessionData]] = {
    Logger.debug("Calling getSessionData in AFT Cache")
    collection.find(BSONDocument("uniqueAftId" -> (id + sessionId)), projection = Option.empty[JsObject]).one[DataCache]
      .flatMap { optionDataCache =>
        lockedBy(sessionId, id).map { lockedBy =>
          optionDataCache.flatMap { dc =>
            dc.sessionData.map( _ copy (name = lockedBy))
          }
        }
      }
  }

   def lockedBy(sessionId: String, id: String)(implicit ec: ExecutionContext): Future[Option[String]] = {
    Logger.debug("Calling lockedBy in AFT Cache")
    val documentsForReturnAndQuarter = collection.find(BSONDocument("id" -> id), projection = Option.empty[JsObject]).
      cursor[DataCache](ReadPreference.primary).collect[List](-1, Cursor.FailOnError[List[DataCache]]())

    documentsForReturnAndQuarter.map {
      _.find(_.sessionData.exists(_.name.isDefined)) match {
        case None => None
        case Some(dc) =>
          println("\nsess:" + sessionId)
          println("\nsess2:" + dc.sessionData)
          dc.sessionData.flatMap {
            case sd if sd.sessionId != sessionId =>
              println("\nhhh")
              sd.name
            case _ => None
          }
      }
    }
  }

  def get(id: String, sessionId: String)(implicit ec: ExecutionContext): Future[Option[JsValue]] = {
    Logger.debug("Calling get in AFT Cache")
    collection.find(BSONDocument("uniqueAftId" -> (id + sessionId)), projection = Option.empty[JsObject]).one[DataCache].map {
      _.map {
        dataEntry =>
          dataEntry.data
      }
    }
  }

  def remove(id: String, sessionId: String)(implicit ec: ExecutionContext): Future[Boolean] = {
    Logger.warn(message = s"Removing row from collection ${collection.name} id:$id")
    val selector = BSONDocument("uniqueAftId" -> (id + sessionId))
    collection.delete.one(selector).map(_.ok)
  }
}

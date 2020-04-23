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
import org.joda.time.{DateTime, DateTimeZone}
import play.api.libs.json._
import play.api.{Configuration, Logger}
import play.modules.reactivemongo.ReactiveMongoComponent
import reactivemongo.api.{Cursor, ReadPreference}
import reactivemongo.api.indexes.{Index, IndexType}
import reactivemongo.bson.{BSONDocument, BSONObjectID}
import reactivemongo.play.json.ImplicitBSONHandlers._
import repository.model.{DataCache, LockedBy}
import uk.gov.hmrc.mongo.ReactiveRepository

import scala.concurrent.{ExecutionContext, Future}

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

  def save(id: String, userData: JsValue, sessionId: String)(implicit ec: ExecutionContext): Future[Boolean] = {
    Logger.debug("Calling Save in AFT Cache")
    val document: JsValue = Json.toJson(DataCache.applyDataCache(
      id = id, None, data = userData, expireAt = expireInSeconds))
    val selector = BSONDocument("uniqueAftId" -> (id + sessionId))
    val modifier = BSONDocument("$set" -> document)
    collection.update.one(selector, modifier, upsert = true).map(_.ok)
  }

  def setLock(id: String, name: String, userData: JsValue, sessionId: String, optionVersion: Option[String], optionAccessMode: Option[String])(implicit ec: ExecutionContext): Future[Boolean] = {
    Logger.debug("Calling setLock in AFT Cache")
    val document: JsValue = Json.toJson(DataCache.applyDataCache(
      id = id, Some(LockedBy(sessionId, name, optionVersion, optionAccessMode)),
      data = userData, expireAt = expireInSeconds))
    val selector = BSONDocument("uniqueAftId" -> (id + sessionId))
    val modifier = BSONDocument("$set" -> document)
    collection.update.one(selector, modifier, upsert = true).map(_.ok)
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

  def lockedBy(sessionId: String, id: String)(implicit ec: ExecutionContext): Future[Option[String]] = {
    Logger.debug("Calling lockedBy in AFT Cache")
    collection.find(BSONDocument("id" -> id), projection = Option.empty[JsObject]).
      cursor[DataCache](ReadPreference.primary).collect[List](-1, Cursor.FailOnError[List[DataCache]]()).map {
      _.find(_.lockedBy.nonEmpty).flatMap { dataCache =>
        Logger.debug(s"LockedBy : ${dataCache.lockedBy} for logged in session Id: ${sessionId}")
        dataCache.lockedBy match {
          case Some(lockedBy) if lockedBy.sessionId != sessionId => Some(lockedBy.name)
          case _ => None
        }
      }
    }
  }
}

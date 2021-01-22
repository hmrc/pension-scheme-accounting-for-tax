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
import org.joda.time.{DateTime, DateTimeZone}
import org.slf4j.{Logger, LoggerFactory}
import play.api.Configuration
import play.api.libs.json._
import play.modules.reactivemongo.ReactiveMongoComponent
import reactivemongo.api.indexes.{Index, IndexType}
import reactivemongo.bson.{BSONDocument, BSONObjectID}
import reactivemongo.play.json.ImplicitBSONHandlers._
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats

import scala.concurrent.{ExecutionContext, Future}

class FinancialInfoCacheRepository @Inject()(
                                              mongoComponent: ReactiveMongoComponent,
                                              configuration: Configuration
                                            )(implicit val ec: ExecutionContext)
  extends ReactiveRepository[JsValue, BSONObjectID](
    configuration.get[String](path = "mongodb.aft-cache.financial-info-cache.name"),
    mongoComponent.mongoConnector.db,
    implicitly
  ) {

  override val logger: Logger = LoggerFactory.getLogger("FinancialInfoCacheRepository")

  private def getExpireAt: DateTime = DateTime.now(DateTimeZone.UTC)
    .plusSeconds(configuration.get[Int]("mongodb.aft-cache.financial-info-cache.timeToLiveInSeconds"))

  val collectionIndexes = Seq(
    Index(key = Seq(("id", IndexType.Ascending)), name = Some("id"), background = true, unique = true),
    Index(key = Seq(("expireAt", IndexType.Ascending)), name = Some("dataExpiry"), background = true
      , options = BSONDocument("expireAfterSeconds" -> 0))
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

  def save(id: String, userData: JsValue)(implicit ec: ExecutionContext): Future[Boolean] = {
    logger.debug("Calling save in financial-info Cache")
    val document: JsValue = Json.toJson(DataCache.applyDataCache(
      id = id, data = userData, expireAt = getExpireAt))
    val selector = BSONDocument("id" -> id)
    val modifier = BSONDocument("$set" -> document)
    collection.update.one(selector, modifier, upsert = true).map(_.ok)
  }

  def get(id: String)(implicit ec: ExecutionContext): Future[Option[JsValue]] = {
    logger.debug("Calling get in financial-info Cache")
    collection.find(BSONDocument("id" -> id), projection = Option.empty[JsObject]).one[DataCache].map {
      _.map {
        dataEntry =>
          dataEntry.data
      }
    }
  }

  def remove(id: String)(implicit ec: ExecutionContext): Future[Boolean] = {
    logger.warn(s"Removing row from collection ${collection.name} id:$id")
    val selector = BSONDocument("id" -> id)
    collection.delete.one(selector).map(_.ok)
  }

  private case class DataCache(id: String, data: JsValue, lastUpdated: DateTime, expireAt: DateTime)

  private object DataCache {
    implicit val dateFormat: Format[DateTime] = ReactiveMongoFormats.dateTimeFormats
    implicit val format: Format[DataCache] = Json.format[DataCache]

    def applyDataCache(id: String,
                       data: JsValue,
                       lastUpdated: DateTime = DateTime.now(DateTimeZone.UTC),
                       expireAt: DateTime): DataCache = {
      DataCache(id, data, lastUpdated, expireAt)
    }
  }

}

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
import crypto.DataEncryptor
import org.mongodb.scala.gridfs.ObservableFuture
import org.mongodb.scala.model.Updates.set
import org.mongodb.scala.model._
import play.api.Logging
import play.api.libs.json._
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.formats.MongoJavatimeFormats
import uk.gov.hmrc.mongo.play.json.{Codecs, PlayMongoRepository}

import java.time.Instant
import java.util.concurrent.TimeUnit
import javax.inject.Singleton
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class CacheRepository @Inject()(collectionName: String,
                                expireInSeconds: Option[Int] = None,
                                expireInDays: Option[Int] = None,
                                mongoComponent: MongoComponent,
                                cipher: DataEncryptor
                               )(implicit val ec: ExecutionContext)
  extends PlayMongoRepository[JsValue](
    collectionName = collectionName,
    mongoComponent = mongoComponent,
    domainFormat = implicitly,
    indexes = CacheRepository.collectionIndexes
  ) with Logging {

  import CacheRepository._

  private def getExpireAt: Instant =
    (expireInSeconds, expireInDays) match {
      case (Some(seconds), None) =>
        Instant.now()
          .plusSeconds(seconds)
      case (None, Some(days)) =>
        val secondsInDay = 86400
        Instant.now()
          .plusSeconds(days * secondsInDay)
      case _ => throw new RuntimeException("Missing config item for expire in days/ seconds: one and only one should be present")
    }

  def save(id: String, userData: JsValue)(implicit ec: ExecutionContext): Future[Unit] = {
    logger.warn(s"Calling save in $collectionName Cache")
    val upsertOptions = new FindOneAndUpdateOptions().upsert(true)
    collection.findOneAndUpdate(
      filter = Filters.eq(idKey, id),
      update = Updates.combine(
        set(idKey, id),
        set(dataKey, Codecs.toBson(cipher.encrypt(id, userData))),
        set(lastUpdatedKey, Codecs.toBson(Instant.now())),
        set(expireAtKey, Codecs.toBson(getExpireAt))
      ),
      upsertOptions
    ).toFuture().map(_ => (): Unit)
  }

  def get(id: String)(implicit ec: ExecutionContext): Future[Option[JsValue]] = {
    logger.debug(s"Calling get in $collectionName Cache")
    collection.find(
      filter = Filters.eq(idKey, id)
    ).toFuture().map {
      _.headOption.map { jsValue =>
        val encryptedValue = (jsValue \ dataKey).as[JsValue]
        cipher.decrypt(id, encryptedValue)
      }
    }
  }

  def remove(id: String)(implicit ec: ExecutionContext): Future[Boolean] = {
    logger.warn(s"Removing row from collection from $collectionName Cache with id:$id")
    collection.deleteOne(
      filter = Filters.eq(idKey, id)
    ).toFuture().map(_ => true)
  }
}


object CacheRepository {
  private implicit val dateFormat: Format[Instant] = MongoJavatimeFormats.instantFormat
  private val idKey = "id"
  private val dataKey = "data"
  private val expireAtKey = "expireAt"
  private val lastUpdatedKey = "lastUpdated"

  private val collectionIndexes: Seq[IndexModel] = Seq(
    IndexModel(
      keys = Indexes.ascending(idKey),
      indexOptions = IndexOptions().name(idKey).unique(true).background(true)
    ),
    IndexModel(
      keys = Indexes.ascending(expireAtKey),
      indexOptions = IndexOptions().name("dataExpiry")
        .expireAfter(0, TimeUnit.SECONDS)
        .background(true)
    )
  )
}

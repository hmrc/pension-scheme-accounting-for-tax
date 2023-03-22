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

import com.google.inject.{Inject, Singleton}
import org.mongodb.scala.MongoWriteException
import play.api.libs.json.{JsObject, Json, Reads, Writes}
import uk.gov.hmrc.mongo.cache.{CacheIdType, CacheItem, MongoCacheRepository}
import uk.gov.hmrc.mongo.{MongoComponent, TimestampSupport}

import java.util.concurrent.TimeUnit
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration.Duration
@Singleton
class IdempotentRequestCacheRepository @Inject()(
                                   mongoComponent  : MongoComponent,
                                   timestampSupport: TimestampSupport
                                 )(implicit ec: ExecutionContext
                                 ) extends MongoCacheRepository(
  mongoComponent   = mongoComponent,
  collectionName   = "idempotentRequest",
  ttl              = Duration(1, TimeUnit.MINUTES),
  timestampSupport = timestampSupport,
  cacheIdType      = CacheIdType.SimpleCacheId
) {

  private lazy val documentExistsErrorCode = 11000
  def insert[T](requestId:String, key:String, data: T)(implicit writes:Writes[T]): Future[Boolean] = {
    collection.insertOne(
      CacheItem(
        requestId,
        JsObject.apply(Seq(
          "data" -> Json.toJson(Map(
            key -> data
          ))
        )),
        java.time.Instant.now(),
        java.time.Instant.now()
      )
    ).toFuture().map { _ => true }
      .recoverWith {
        case e: MongoWriteException if e.getCode == documentExistsErrorCode =>
          Future.successful(false)
      }
  }
}
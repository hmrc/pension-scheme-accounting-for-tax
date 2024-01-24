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
import config.AppConfig
import org.joda.time.{DateTime, DateTimeZone}
import org.mongodb.scala.MongoWriteException
import org.mongodb.scala.model._
import play.api.Logging
import play.api.libs.json.{Format, Json, OFormat}
import repository.SubmitAftReturnCacheRepository._
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.PlayMongoRepository
import uk.gov.hmrc.mongo.play.json.formats.MongoJodaFormats

import java.util.concurrent.TimeUnit
import javax.inject.Singleton
import scala.concurrent.{ExecutionContext, Future}

object SubmitAftReturnCacheRepository {
  private val pstrFieldName = "pstr"
  private val externalUserIdFieldName = "externalUserId"

  case class SubmitAftReturnCacheEntry(pstr: String, externalUserId: String, insertionTime: DateTime)
  implicit val dateFormats: Format[DateTime] = MongoJodaFormats.dateTimeFormat
  implicit val format: OFormat[SubmitAftReturnCacheEntry] = Json.format[SubmitAftReturnCacheEntry]
}

@Singleton
class SubmitAftReturnCacheRepository @Inject()(
                                                mongoComponent: MongoComponent,
                                                appConfig: AppConfig
                                              )(implicit val ec: ExecutionContext)
  extends PlayMongoRepository[SubmitAftReturnCacheEntry](
    collectionName = appConfig.mongoDBSubmitAftReturnCollectionName,
    mongoComponent = mongoComponent,
    domainFormat = implicitly,
    indexes = Seq(
      IndexModel(
        Indexes.ascending(pstrFieldName, externalUserIdFieldName),
        IndexOptions().name("primaryKey").unique(true)
      ),
      IndexModel(
        keys = Indexes.ascending("insertionTime"),
        indexOptions = IndexOptions().name("insertion").expireAfter(appConfig.mongoDBSubmitAftReturnTTL, TimeUnit.SECONDS)
      )
    )
  ) with Logging {

  private lazy val documentExistsErrorCode = 11000

  def insertLockData(pstr: String, externalUserId: String): Future[Boolean] = {
    collection.insertOne(SubmitAftReturnCacheEntry(pstr, externalUserId, DateTime.now(DateTimeZone.UTC))).toFuture().map { _ => true }
      .recoverWith {
        case e: MongoWriteException if e.getCode == documentExistsErrorCode =>
          Future.successful(false)
      }
  }
}

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
import org.mongodb.scala.model.*
import org.mongodb.scala.model.Updates.set
import play.api.libs.json.*
import play.api.{Configuration, Logging}
import repository.model.*
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.formats.MongoJavatimeFormats
import uk.gov.hmrc.mongo.play.json.{Codecs, PlayMongoRepository}

import java.time.{Instant, LocalDateTime, ZoneId}
import java.util.concurrent.TimeUnit
import javax.inject.Singleton
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class FileUploadReferenceCacheRepository @Inject()(
                                                    mongoComponent: MongoComponent,
                                                    configuration: Configuration,
                                                    cipher: DataEncryptor
                                                  )(implicit val ec: ExecutionContext)
  extends PlayMongoRepository[JsValue](
    collectionName = configuration.get[String](path = "mongodb.aft-cache.file-upload-response-cache.name"),
    mongoComponent = mongoComponent,
    domainFormat = implicitly,
    indexes = Seq(
      IndexModel(
        keys = Indexes.ascending("uploadId"),
        indexOptions = IndexOptions().name("uploadId").unique(true).background(true)
      ),
      IndexModel(
        keys = Indexes.ascending("reference"),
        indexOptions = IndexOptions().name("reference").unique(false).background(true)
      ),
      IndexModel(
        keys = Indexes.ascending("expireAt"),
        indexOptions = IndexOptions().name("dataExpiry").expireAfter(0, TimeUnit.SECONDS)
      )
    )
  ) with Logging {


  private def expireInSeconds: Instant = Instant.now().
    plusSeconds(configuration.get[Int](path = "mongodb.aft-cache.file-upload-response-cache.timeToLiveInSeconds"))

  private implicit val dateFormat: Format[Instant] = MongoJavatimeFormats.instantFormat

  private val uploadIdKey = "uploadId"
  private val referenceKey = "reference"
  private val statusKey = "status"
  private val createdKey = "created"
  private val lastUpdatedKey = "lastUpdated"
  private val expireAtKey = "expireAt"

  def requestUpload(uploadId: String, reference: String)(implicit ec: ExecutionContext): Future[Unit] = {
    logger.debug("Calling requestUpload in FileUploadReferenceCache")

    val upsertOptions = new FindOneAndUpdateOptions().upsert(true)

    collection.findOneAndUpdate(
      filter = Filters.eq(uploadIdKey, uploadId),
      update = Updates.combine(
        set(uploadIdKey, uploadId),
        set(referenceKey, Codecs.toBson(reference)),
        set(statusKey, Codecs.toBson(cipher.encrypt(reference, Json.toJson(FileUploadStatus("InProgress"))))),
        set(createdKey, Codecs.toBson(LocalDateTime.now(ZoneId.of("UTC")))),
        set(lastUpdatedKey, Codecs.toBson(LocalDateTime.now(ZoneId.of("UTC")))),
        set(expireAtKey, Codecs.toBson(expireInSeconds))
      ),
      upsertOptions
    ).toFuture().map(_ => (): Unit)
  }

  def getUploadResult(uploadId: String)(implicit ec: ExecutionContext): Future[Option[FileUploadDataCache]] = {
    logger.debug("Calling getUploadResult in  FileUploadReferenceCache")
    collection.find(
      filter = Filters.eq(uploadIdKey, uploadId)
    ).headOption()
      .map {
        _.map {
          _.as[JsObject]
        }
      }
      .map {
        _.map { data => {
          val referenceId = (data \ referenceKey).as[String]
          val encryptedStatus = (data \ statusKey).as[JsValue]
          val decryptedStatus = cipher.decrypt(referenceId, encryptedStatus)
          FileUploadDataCache.applyDataCache(
            uploadId = (data \ uploadIdKey).as[String],
            reference = (data \ referenceKey).as[String],
            status = decryptedStatus.as[FileUploadStatus],
            lastUpdated = expireInSeconds,
            expireAt = expireInSeconds
          )
        }
        }
      }
  }

  def updateStatus(reference: String, newStatus: FileUploadStatus): Future[Unit] = {
    logger.debug("Calling updateStatus in  FileUploadReferenceCache")
    val upsertOptions = new FindOneAndUpdateOptions().upsert(false)

    collection.findOneAndUpdate(
      filter = Filters.eq(referenceKey, reference),
      update = Updates.combine(
        set(statusKey, Codecs.toBson(cipher.encrypt(reference, Json.toJson(newStatus))))
      ),
      upsertOptions
    ).toFuture().map { _ => (): Unit }
  }
}

/*
 * Copyright 2022 HM Revenue & Customs
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
import org.mongodb.scala.model.Updates.set
import org.mongodb.scala.model._
import play.api.libs.json._
import play.api.{Configuration, Logging}
import repository.model._
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.formats.MongoJavatimeFormats
import uk.gov.hmrc.mongo.play.json.{Codecs, PlayMongoRepository}

import java.time.LocalDateTime
import java.util.concurrent.TimeUnit
import scala.concurrent.{ExecutionContext, Future}

class FileUploadReferenceCacheRepository @Inject()(
                                                    mongoComponent: MongoComponent,
                                                    configuration: Configuration
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
        indexOptions = IndexOptions().name("dataExpiry").unique(true).expireAfter(0, TimeUnit.SECONDS)
      )
    )
  ) with Logging {


  private def expireInSeconds: LocalDateTime = LocalDateTime.now.
    plusSeconds(configuration.get[Int](path = "mongodb.aft-cache.file-upload-response-cache.timeToLiveInSeconds"))

  private implicit val dateFormat: Format[LocalDateTime] = MongoJavatimeFormats.localDateTimeFormat

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
        set(statusKey, Codecs.toBson(FileUploadStatus("InProgress"))),
        set(createdKey, Codecs.toBson(LocalDateTime.now())),
        set(lastUpdatedKey, Codecs.toBson(LocalDateTime.now())),
        set(expireAtKey, Codecs.toBson(expireInSeconds))
      ),
      upsertOptions
    ).toFuture().map(_ => (): Unit )

    //    val document: JsValue = Json.toJson(FileUploadDataCache.applyDataCache(
    //      uploadId = uploadId, status = FileUploadStatus("InProgress"), reference = reference, expireAt = expireInSeconds))
    //    val selector = BSONDocument("uploadId" -> uploadId)
    //    val modifier = BSONDocument("$set" -> document)
    //    collection.update.one(selector, modifier, upsert = true).map(_.ok)
  }

  def getUploadResult(uploadId: String)(implicit ec: ExecutionContext): Future[Option[FileUploadDataCache]] = {
    logger.debug("Calling getUploadResult in  FileUploadReferenceCache")
    collection.find(
      filter = Filters.eq(uploadIdKey, uploadId)
    ).toFuture()
      .map(_.headOption)
      .map { _.map { _.as[JsObject] }
      }
      .map {
        _.map { data =>
          // TOOO: Not expiring for some reason
          FileUploadDataCache.applyDataCache(
            uploadId = (data \ uploadIdKey).as[String],
            reference = (data \ referenceKey).as[String],
            status = (data \ statusKey).as[FileUploadStatus],
            lastUpdated = (data \ lastUpdatedKey).as[LocalDateTime],
            expireAt = (data \ expireAtKey).as[LocalDateTime]
          )
        }
      }
    //    collection.find(BSONDocument("uploadId" -> uploadId), projection = Option.empty[JsObject]).one[FileUploadDataCache].map {
    //      _.map {
    //        dataEntry =>
    //          FileUploadDataCache.applyDataCache(
    //            uploadId = dataEntry.uploadId, reference = dataEntry.reference, status = dataEntry.status, lastUpdated=expireInSeconds, expireAt = expireInSeconds)
    //      }
    //    }
  }

  def updateStatus(reference: String, newStatus: FileUploadStatus): Future[Unit] = {
    logger.debug("Calling updateStatus in  FileUploadReferenceCache")
    val upsertOptions = new FindOneAndUpdateOptions().upsert(false)

    collection.findOneAndUpdate(
      filter = Filters.eq(referenceKey, reference),
      update = Updates.combine(
        set(statusKey, Codecs.toBson(newStatus))
      ),
      upsertOptions
    ).toFuture().map { _ => ():Unit }
    }
    //    val selector = BSONDocument("reference" -> reference)
    //    val modifier = BSONDocument("$set" -> BSONDocument("status" -> Json.toJson(newStatus)))
    //    collection.findAndUpdate[BSONDocument, BSONDocument](selector, modifier, fetchNewObject = true, upsert = false, None, None,
    //      bypassDocumentValidation = false, WriteConcern.Default, None, None, Seq.empty) map { response =>
    //      response.value.flatMap(fileUploadDataCache => Json.fromJson[FileUploadDataCache](fileUploadDataCache).asOpt)
    //    }

}

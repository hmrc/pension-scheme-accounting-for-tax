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
import org.joda.time.{DateTime, DateTimeZone}
import org.slf4j.{Logger, LoggerFactory}
import play.api.Configuration
import play.api.libs.json._
import play.modules.reactivemongo.ReactiveMongoComponent
import reactivemongo.api.commands.WriteConcern
import reactivemongo.api.indexes.{Index, IndexType}
import reactivemongo.bson.{BSONDocument, BSONObjectID}
import reactivemongo.play.json.ImplicitBSONHandlers._
import repository.model._
import uk.gov.hmrc.mongo.ReactiveRepository

import java.time.LocalDateTime
import scala.concurrent.{ExecutionContext, Future}

class FileUploadReferenceCacheRepository @Inject()(
                                                    mongoComponent: ReactiveMongoComponent,
                                                    configuration: Configuration
                                                  )(implicit val ec: ExecutionContext)
  extends ReactiveRepository[JsValue, BSONObjectID](
    configuration.get[String](path = "mongodb.aft-cache.file-upload-response-cache.name"),
    mongoComponent.mongoConnector.db,
    implicitly
  ) {

  override val logger: Logger = LoggerFactory.getLogger("FileUploadReferenceCacheRepository")
  //private def expireInSeconds: LocalDateTime = LocalDateTime.now(DateTimeZone.UTC).
  private def expireInSeconds: LocalDateTime = LocalDateTime.now.
    plusSeconds(configuration.get[Int](path = "mongodb.aft-cache.file-upload-response-cache.timeToLiveInSeconds"))

  val collectionIndexes = Seq(
    Index(Seq(("uploadId", IndexType.Ascending)), Some("uploadId"), unique = true, background = true),
    Index(Seq(("reference", IndexType.Ascending)), Some("reference"), unique = false, background = true),
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

  def requestUpload(uploadId: String, reference: String)(implicit ec: ExecutionContext): Future[Boolean] = {
    logger.debug("Calling requestUpload in FileUploadReferenceCache")
    val document: JsValue = Json.toJson(FileUploadDataCache.applyDataCache(
      uploadId = uploadId, status = FileUploadStatus("InProgress"), reference = reference, expireAt = expireInSeconds))
    val selector = BSONDocument("uploadId" -> uploadId)
    val modifier = BSONDocument("$set" -> document)
    collection.update.one(selector, modifier, upsert = true).map(_.ok)
  }

  def getUploadResult(uploadId: String)(implicit ec: ExecutionContext): Future[Option[FileUploadDataCache]] = {
    logger.debug("Calling getUploadResult in  FileUploadReferenceCache")
    collection.find(BSONDocument("uploadId" -> uploadId), projection = Option.empty[JsObject]).one[FileUploadDataCache].map {
      _.map {
        dataEntry =>
          FileUploadDataCache.applyDataCache(
            uploadId = dataEntry.uploadId, reference = dataEntry.reference, status = dataEntry.status, expireAt = expireInSeconds)
      }
    }
  }

  def updateStatus(reference: String, newStatus: FileUploadStatus): Future[Option[FileUploadDataCache]] = {
    logger.debug("Calling updateStatus in  FileUploadReferenceCache")
    val selector = BSONDocument("reference" -> reference)
    val modifier = BSONDocument("$set" -> BSONDocument("status" -> Json.toJson(newStatus)))
    collection.findAndUpdate[BSONDocument, BSONDocument](selector, modifier, fetchNewObject = true, upsert = false, None, None,
      bypassDocumentValidation = false, WriteConcern.Default, None, None, Seq.empty) map { response =>
      response.value.flatMap(fileUploadDataCache => Json.fromJson[FileUploadDataCache](fileUploadDataCache).asOpt)
    }
  }
}

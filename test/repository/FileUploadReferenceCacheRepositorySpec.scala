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

import com.github.simplyscala.MongoEmbedDatabase
import org.mockito.{ArgumentMatchers, MockitoSugar}
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.{BeforeAndAfter, BeforeAndAfterEach}
import play.api.Configuration
import play.api.libs.json.Format
import repository.model.FileUploadStatus
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.formats.MongoJavatimeFormats

import java.time.LocalDateTime
import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration


class FileUploadReferenceCacheRepositorySpec extends AnyWordSpec with MockitoSugar with Matchers with MongoEmbedDatabase with BeforeAndAfter with
  BeforeAndAfterEach { // scalastyle:off magic.number

  import FileUploadReferenceCacheRepositorySpec._

  override def beforeEach: Unit = {
    super.beforeEach
    reset(mockConfiguration)
    when(mockConfiguration.get[String](ArgumentMatchers.eq("mongodb.aft-cache.file-upload-response-cache.name"))(ArgumentMatchers.any()))
      .thenReturn("file-upload-response")
    when(mockConfiguration.get[Int](ArgumentMatchers.eq("mongodb.aft-cache.file-upload-response-cache.timeToLiveInSeconds"))(ArgumentMatchers.any()))
      .thenReturn(604800)
  }

  withEmbedMongoFixture(port = 24680) { _ =>

    "updateStatus" must {
      "update status correctly" in {
        mongoCollectionDrop()
        val result = for {
          _ <- repository.requestUpload(id1, id2)
          _ <- repository.updateStatus(id2, fileUploadStatusNotInProgress)
          status <- repository.getUploadResult(id1)
        } yield {
          status
        }

        Await.result(result, Duration.Inf) match {
          case status =>
            status.map(_.status) mustBe Some(fileUploadStatusNotInProgress)
        }
      }
    }

    "getUploadResult" must {
      "get result correctly when present" in {
        mongoCollectionDrop()
        val result = Await.result(
          for {
            _ <- repository.requestUpload(id1, id2)
            status <- repository.getUploadResult(id1)
          } yield {
            status
          },
          Duration.Inf
        )
        result.map(_.status) mustBe Some(fileUploadStatusInProgress)
      }

      "get none when not present" in {
        mongoCollectionDrop()
        val result = Await.result(
          for {
            _ <- repository.requestUpload(id1, id2)
            status <- repository.getUploadResult(id2)
          } yield {
            status
          },
          Duration.Inf
        )
        result.map(_.status) mustBe None
      }
    }

    "requestUpload" must {
      "upsert result correctly" in {
        mongoCollectionDrop()
        val result = Await.result(
          for {
            _ <- repository.requestUpload(id1, id2)
            status <- repository.getUploadResult(id1)
          } yield {
            status
          },
          Duration.Inf
        )
        result.map(_.status) mustBe Some(fileUploadStatusInProgress)
        result.map(_.reference) mustBe Some(id2)
      }
    }
  }
}

object FileUploadReferenceCacheRepositorySpec extends AnyWordSpec with MockitoSugar {
  private implicit val dateFormat: Format[LocalDateTime] = MongoJavatimeFormats.localDateTimeFormat

  import scala.concurrent.ExecutionContext.Implicits._

  private val mockConfiguration = mock[Configuration]
  private val databaseName = "pension-scheme-accounting-for-tax"
  private val mongoUri: String = s"mongodb://127.0.0.1:27017/$databaseName?heartbeatFrequencyMS=1000&rm.failover=default"
  private val mongoComponent = MongoComponent(mongoUri)

  private def mongoCollectionDrop(): Void = Await
    .result(repository.collection.drop().toFuture(), Duration.Inf)

  private def repository = new FileUploadReferenceCacheRepository(mongoComponent, mockConfiguration)

  private val id1 = "id1"
  private val id2 = "id2"

  private val fileUploadStatusNotInProgress = FileUploadStatus("NotInProgress")
  private val fileUploadStatusInProgress = FileUploadStatus("InProgress")

}

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


import base.MongoConfig
import crypto.DataEncryptor
import org.mockito.ArgumentMatchers
import org.mockito.Mockito._
import org.mongodb.scala.{ObservableFuture, SingleObservableFuture}
import org.mongodb.scala.bson.{BsonDocument, BsonString}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.must.Matchers
import org.scalatest.time.{Millis, Seconds, Span}
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.{BeforeAndAfter, BeforeAndAfterAll}
import org.scalatestplus.mockito.MockitoSugar
import play.api.Configuration
import play.api.inject.bind
import play.api.inject.guice.{GuiceApplicationBuilder, GuiceableModule}
import repository.model.FileUploadStatus
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.mongo.MongoComponent
import scala.compiletime.uninitialized

import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration


class FileUploadReferenceCacheRepositorySpec extends AnyWordSpec with MockitoSugar with Matchers with MongoConfig with BeforeAndAfter with BeforeAndAfterAll with ScalaFutures { // scalastyle:off magic.number

  override implicit val patienceConfig: PatienceConfig = PatienceConfig(Span(30, Seconds), Span(1, Millis))

  import FileUploadReferenceCacheRepositorySpec._

  var fileUploadReferenceCacheRepository: FileUploadReferenceCacheRepository = uninitialized

  private val modules: Seq[GuiceableModule] = Seq(
    bind[AuthConnector].toInstance(mock[AuthConnector])
  )

  private val app = new GuiceApplicationBuilder()
    .configure(
      conf = "auditing.enabled" -> false,
      "metrics.enabled" -> false,
      "metrics.jvm" -> false,
      "run.mode" -> "Test"
    ).overrides(modules *).build()

  private val cipher = app.injector.instanceOf[DataEncryptor]

  private def buildFormRepository(mongoHost: String, mongoPort: Int): FileUploadReferenceCacheRepository = {
    val databaseName = "pension-scheme-accounting-for-tax"
    val mongoUri = s"mongodb://$mongoHost:$mongoPort/$databaseName?heartbeatFrequencyMS=1000&rm.failover=default"

    new FileUploadReferenceCacheRepository(MongoComponent(mongoUri), mockConfiguration, cipher)
  }

  override def beforeAll(): Unit = {
    when(mockConfiguration.get[String](ArgumentMatchers.eq("mongodb.aft-cache.file-upload-response-cache.name"))(ArgumentMatchers.any()))
      .thenReturn("file-upload-response")
    when(mockConfiguration.get[Int](ArgumentMatchers.eq("mongodb.aft-cache.file-upload-response-cache.timeToLiveInSeconds"))(ArgumentMatchers.any()))
      .thenReturn(604800)
    fileUploadReferenceCacheRepository = buildFormRepository(mongoHost, mongoPort)
    super.beforeAll()
    reset(mockConfiguration)
  }

  "updateStatus" must {
    "update status correctly" in {

      val result = for {
        _ <- fileUploadReferenceCacheRepository.collection.drop().toFuture()
        _ <- fileUploadReferenceCacheRepository.requestUpload(id1, id2)
        _ <- fileUploadReferenceCacheRepository.updateStatus(id2, fileUploadStatusNotInProgress)
        status <- fileUploadReferenceCacheRepository.getUploadResult(id1)
      } yield {
        status
      }

      Await.result(result, Duration.Inf) match {
        case status =>
          status.map(_.status).mustBe(Some(fileUploadStatusNotInProgress))
      }
    }
  }

  "getUploadResult" must {
    "get result correctly when present" in {

      val result = Await.result(
        for {
          _ <- fileUploadReferenceCacheRepository.collection.drop().toFuture()
          _ <- fileUploadReferenceCacheRepository.requestUpload(id1, id2)
          status <- fileUploadReferenceCacheRepository.getUploadResult(id1)
        } yield {
          status
        },
        Duration.Inf
      )
      result.map(_.status).mustBe(Some(fileUploadStatusInProgress))
    }

    "get none when not present" in {

      val result = Await.result(
        for {
          _ <- fileUploadReferenceCacheRepository.collection.drop().toFuture()
          _ <- fileUploadReferenceCacheRepository.requestUpload(id1, id2)
          status <- fileUploadReferenceCacheRepository.getUploadResult(id2)
        } yield {
          status
        },
        Duration.Inf
      )
      result.map(_.status).mustBe(None)
    }
  }

  "requestUpload" must {
    "upsert result correctly" in {

      val result = Await.result(
        for {
          _ <- fileUploadReferenceCacheRepository.collection.drop().toFuture()
          _ <- fileUploadReferenceCacheRepository.requestUpload(id1, id2)
          status <- fileUploadReferenceCacheRepository.getUploadResult(id1)
        } yield {
          status
        },
        Duration.Inf
      )
      result.map(_.status).mustBe(Some(fileUploadStatusInProgress))
      result.map(_.reference).mustBe(Some(id2))
    }
    "save expireAt value as a date" in {
      when(mockConfiguration.getOptional[Boolean](path= "encrypted")).thenReturn(Some(false))

      val ftr = fileUploadReferenceCacheRepository.collection.drop().toFuture().flatMap { _ =>
        fileUploadReferenceCacheRepository.requestUpload("id", "ref").flatMap { _ =>
          for {
            stringResults <- fileUploadReferenceCacheRepository.collection.find(
              BsonDocument("expireAt" -> BsonDocument("$type" -> BsonString("string")))
            ).toFuture()
            dateResults <- fileUploadReferenceCacheRepository.collection.find(
              BsonDocument("expireAt" -> BsonDocument("$type" -> BsonString("date")))
            ).toFuture()
          } yield stringResults -> dateResults
        }
      }

      whenReady(ftr) { case (stringResults, dateResults) =>
        stringResults.length.mustBe(0)
        dateResults.length.mustBe(1)
      }
    }
  }
}

object FileUploadReferenceCacheRepositorySpec extends MockitoSugar {
  private val mockConfiguration = mock[Configuration]

  private val id1 = "id1"
  private val id2 = "id2"

  private val fileUploadStatusNotInProgress = FileUploadStatus("NotInProgress")
  private val fileUploadStatusInProgress = FileUploadStatus("InProgress")


}

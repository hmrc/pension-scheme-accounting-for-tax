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
import config.AppConfig
import org.mockito.Mockito.when
import org.mongodb.scala.{ObservableFuture, SingleObservableFuture}
import org.mongodb.scala.bson.{BsonDocument, BsonString}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.must.Matchers
import org.scalatest.time.{Millis, Seconds, Span}
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.{BeforeAndAfter, BeforeAndAfterAll, BeforeAndAfterEach}
import org.scalatestplus.mockito.MockitoSugar
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.{Application, Configuration}
import uk.gov.hmrc.mongo.MongoComponent
import org.scalactic.Prettifier.default

import java.time.Instant
import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.DurationInt

class SubmitAftReturnCacheRepositorySpec
  extends AnyWordSpec with MockitoSugar with Matchers with MongoConfig with BeforeAndAfter
    with BeforeAndAfterEach with BeforeAndAfterAll with ScalaFutures { // scalastyle:off magic.number

  override implicit val patienceConfig: PatienceConfig = PatienceConfig(Span(30, Seconds), Span(1, Millis))

  var submitAftReturnCacheRepository: SubmitAftReturnCacheRepository = _

  private val application: Application = new GuiceApplicationBuilder()
    .configure(conf = "auditing.enabled" -> false, "metrics.enabled" -> false, "metrics.jvm" -> false).build()

  val aftCacheEntry: SubmitAftReturnCacheEntry = SubmitAftReturnCacheEntry("123", "testUser", Instant.now(), None)
  private val mockAppConfig = mock[AppConfig]
  private val collectionName = "submit-aft-return"
  private val databaseName = "pension-scheme-accounting-for-tax"
  private val mongoUri = s"mongodb://$mongoHost:$mongoPort/$databaseName?heartbeatFrequencyMS=1000&rm.failover=default"
  private val mongoComponent = MongoComponent(mongoUri)
  private def buildRepository = new SubmitAftReturnCacheRepository(mongoComponent, application.injector.instanceOf[AppConfig])
  private val mockConfiguration = mock[Configuration]
  override def beforeAll(): Unit = {
    when(mockAppConfig.mongoDBSubmitAftReturnCollectionName).thenReturn(collectionName)
    submitAftReturnCacheRepository = buildRepository
    super.beforeAll()
  }

  override def afterAll(): Unit = {
    Await.result(application.stop(), 10.seconds)
  }

  override def beforeEach(): Unit = {
    super.beforeEach()
  }

  "save" must {
    "insert a row into mongo" in {
      val document = for {
        _ <- submitAftReturnCacheRepository.collection.drop().toFuture()
        _ <- submitAftReturnCacheRepository.insertLockData(aftCacheEntry.pstr, aftCacheEntry.externalUserId, None)
        countedDocuments <- submitAftReturnCacheRepository.collection.countDocuments().toFuture()
      } yield countedDocuments

      whenReady(document) { documentsInDB =>
        documentsInDB.mustBe(1L)
      }
    }
    "save insertionTime value as a date" in {
      when(mockConfiguration.getOptional[Boolean](path= "encrypted")).thenReturn(Some(false))

      val ftr = buildRepository.collection.drop().toFuture().flatMap { _ =>
        buildRepository.insertLockData("pstr", "user-id", None).flatMap { _ =>
          for {
            stringResults <- buildRepository.collection.find(
              BsonDocument("insertionTime" -> BsonDocument("$type" -> BsonString("string")))
            ).toFuture()
            dateResults <- buildRepository.collection.find(
              BsonDocument("insertionTime" -> BsonDocument("$type" -> BsonString("date")))
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

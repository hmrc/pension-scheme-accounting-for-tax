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
import org.mockito.Mockito._
import org.mongodb.scala.{ObservableFuture, SingleObservableFuture}
import org.mongodb.scala.bson.{BsonDocument, BsonString}
import org.scalactic.Prettifier.default
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.must.Matchers
import org.scalatest.time.{Millis, Seconds, Span}
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.{BeforeAndAfter, BeforeAndAfterAll}
import org.scalatestplus.mockito.MockitoSugar
import play.api.Configuration
import play.api.inject.bind
import play.api.inject.guice.{GuiceApplicationBuilder, GuiceableModule}
import play.api.libs.json.Json
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.mongo.MongoComponent

import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration

class CacheRepositorySpec extends AnyWordSpec with MockitoSugar with Matchers with MongoConfig
  with BeforeAndAfter with BeforeAndAfterAll with ScalaFutures { // scalastyle:off magic.number

  override implicit val patienceConfig: PatienceConfig = PatienceConfig(Span(30, Seconds), Span(1, Millis))

  import CacheRepositorySpec._

  var cacheRepository: CacheRepository = _

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

  private def buildFormRepository(mongoHost: String, mongoPort: Int): CacheRepository = {
    val databaseName = "pension-scheme-accounting-for-tax"
    val mongoUri = s"mongodb://$mongoHost:$mongoPort/$databaseName?heartbeatFrequencyMS=1000&rm.failover=default"
    new CacheRepository(collectionName, Some(60), None, MongoComponent(mongoUri), cipher)
  }

  override def beforeAll(): Unit = {
    cacheRepository = buildFormRepository(mongoHost, mongoPort)
    super.beforeAll()
    reset(mockConfiguration)
  }


  "remove" must {
    "remove item" in {

      val result = Await.result(
        for {
          _ <- cacheRepository.collection.drop().toFuture()
          _ <- cacheRepository.save(id1, dummyData)
          _ <- cacheRepository.remove(id1)
          status <- cacheRepository.get(id1)
        } yield {
          status
        },
        Duration.Inf
      )
      result.mustBe(None)
    }
  }

  "get" must {
    "return none when nothing present" in {

      val result = Await.result(
        for {
          _ <- cacheRepository.collection.drop().toFuture()
          status <- cacheRepository.get(id1)
        } yield {
          status
        },
        Duration.Inf
      )
      result.mustBe(None)
    }
  }

  "save and get" must {
    "save and get data correctly and have the correct collection name" in {

      val result = Await.result(
        for {
          _ <- cacheRepository.collection.drop().toFuture()
          _ <- cacheRepository.save(id1, dummyData)
          status <- cacheRepository.get(id1)
        } yield {
          status
        },
        Duration.Inf
      )
      result.mustBe(Some(dummyData))
      cacheRepository.collectionName.mustBe(collectionName)
    }
    "save expireAt value as a date" in {
      when(mockConfiguration.getOptional[Boolean](path= "encrypted")).thenReturn(Some(false))
      val cacheRepository = buildFormRepository(mongoHost, mongoPort)

      val ftr = cacheRepository.collection.drop().toFuture().flatMap { _ =>
        cacheRepository.save("id", Json.parse("{}")).flatMap { _ =>
          for {
            stringResults <- cacheRepository.collection.find(
              BsonDocument("expireAt" -> BsonDocument("$type" -> BsonString("string")))
            ).toFuture()
            dateResults <- cacheRepository.collection.find(
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

object CacheRepositorySpec extends MockitoSugar {
  private val mockConfiguration = mock[Configuration]

  private val collectionName = "test"

  private val id1 = "id1"

  private val dummyData = Json.obj(
    "test" -> "test"
  )

}

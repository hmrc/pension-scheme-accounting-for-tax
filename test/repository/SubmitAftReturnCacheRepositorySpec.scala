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

import config.AppConfig
import org.mockito.Mockito.when
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.must.Matchers
import org.scalatest.time.{Millis, Seconds, Span}
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.{BeforeAndAfter, BeforeAndAfterAll, BeforeAndAfterEach}
import org.scalatestplus.mockito.MockitoSugar
import repository.SubmitAftReturnCacheRepository.SubmitAftReturnCacheEntry
import uk.gov.hmrc.mongo.MongoComponent

import scala.concurrent.ExecutionContext.Implicits.global

class SubmitAftReturnCacheRepositorySpec
  extends AnyWordSpec with MockitoSugar with Matchers with EmbeddedMongoDBSupport with BeforeAndAfter with
    BeforeAndAfterEach with BeforeAndAfterAll with ScalaFutures { // scalastyle:off magic.number

  override implicit val patienceConfig: PatienceConfig = PatienceConfig(Span(30, Seconds), Span(1, Millis))

  import SubmitAftReturnCacheRepositorySpec._

  var submitAftReturnCacheRepository: SubmitAftReturnCacheRepository = _

  override def beforeAll(): Unit = {
    when(mockAppConfig.mongoDBSubmitAftReturnCollectionName).thenReturn(collectionName)
    initMongoDExecutable()
    startMongoD()
    submitAftReturnCacheRepository = buildRepository(mongoHost, mongoPort)
    super.beforeAll()
  }

  override def beforeEach(): Unit = {
    super.beforeEach()
  }

  override def afterAll(): Unit =
    stopMongoD()

  "save" must {
    "insert a row into mongo" in {

      val document = for {
        _ <- submitAftReturnCacheRepository.collection.drop().toFuture()
        _ <- submitAftReturnCacheRepository.insertLockData(aftCacheEntry.pstr, aftCacheEntry.externalUserId)
        countedDocuments <- submitAftReturnCacheRepository.collection.countDocuments().toFuture()
      } yield countedDocuments

      whenReady(document) { documentsInDB =>
        documentsInDB mustBe 1L
      }
    }
  }
}

object SubmitAftReturnCacheRepositorySpec extends MockitoSugar {

  private val mockAppConfig = mock[AppConfig]
  private val collectionName = "submit-aft-return"
  val aftCacheEntry: SubmitAftReturnCacheEntry = SubmitAftReturnCacheEntry("123", "testUser")

  private def buildRepository(mongoHost: String, mongoPort: Int): SubmitAftReturnCacheRepository = {
    val databaseName = "pension-scheme-accounting-for-tax"
    val mongoUri = s"mongodb://$mongoHost:$mongoPort/$databaseName?heartbeatFrequencyMS=1000&rm.failover=default"
    new SubmitAftReturnCacheRepository(MongoComponent(mongoUri), mockAppConfig)
  }
}



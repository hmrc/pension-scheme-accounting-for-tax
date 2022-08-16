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
import org.mockito.MockitoSugar
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.{BeforeAndAfter, BeforeAndAfterEach}
import play.api.Configuration
import play.api.libs.json.{Format, Json}
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.formats.MongoJavatimeFormats

import java.time.LocalDateTime
import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration

class CacheRepositorySpec extends AnyWordSpec with MockitoSugar with Matchers with MongoEmbedDatabase with BeforeAndAfter with
  BeforeAndAfterEach { // scalastyle:off magic.number

  import CacheRepositorySpec._

  override def beforeEach: Unit = {
    super.beforeEach
    reset(mockConfiguration)
  }

  withEmbedMongoFixture(port = 24680) { _ =>

    "remove" must {
      "remove item" in {
        mongoCollectionDrop()
        val result = Await.result(
          for {
            _ <- repository.save(id1, dummyData)
            _ <- repository.remove(id1)
            status <- repository.get(id1)
          } yield {
            status
          },
          Duration.Inf
        )
        result mustBe None
      }
    }

    "get" must {
      "return none when nothing present" in {
        mongoCollectionDrop()
        val result = Await.result(
          for {
            status <- repository.get(id1)
          } yield {
            status
          },
          Duration.Inf
        )
        result mustBe None
      }
    }

    "save and get" must {
      "save and get data correctly and have the correct collection name" in {
        mongoCollectionDrop()
        val result = Await.result(
          for {
            _ <- repository.save(id1, dummyData)
            status <- repository.get(id1)
          } yield {
            status
          },
          Duration.Inf
        )
        result mustBe Some(dummyData)
        repository.collectionName mustBe "test"
      }
    }
  }
}

object CacheRepositorySpec extends AnyWordSpec with MockitoSugar {
  private implicit val dateFormat: Format[LocalDateTime] = MongoJavatimeFormats.localDateTimeFormat

  import scala.concurrent.ExecutionContext.Implicits._

  private val mockConfiguration = mock[Configuration]
  private val databaseName = "pension-scheme-accounting-for-tax"
  private val mongoUri: String = s"mongodb://127.0.0.1:27017/$databaseName?heartbeatFrequencyMS=1000&rm.failover=default"
  private val mongoComponent = MongoComponent(mongoUri)

  private def mongoCollectionDrop(): Void = Await
    .result(repository.collection.drop().toFuture(), Duration.Inf)

  private def repository = new CacheRepository(
    collectionName = "test",
    expireInSeconds = Some(60),
    expireInDays = None,
    mongoComponent = mongoComponent
  )

  private val id1 = "id1"

  private val dummyData = Json.obj(
    "test" -> "test"
  )
}

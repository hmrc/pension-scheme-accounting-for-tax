/*
 * Copyright 2021 HM Revenue & Customs
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

import play.api.Configuration
import play.modules.reactivemongo.ReactiveMongoComponent
import reactivemongo.bson.BSONDocument
import reactivemongo.play.json.ImplicitBSONHandlers._
import services.BatchService
import services.BatchService.BatchType
import com.github.simplyscala.{MongoEmbedDatabase, MongodProps}
import org.mockito.MockitoSugar
import org.scalatest.BeforeAndAfter
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.Json
import uk.gov.hmrc.mongo.MongoConnector
import org.scalatest.concurrent.ScalaFutures.whenReady

import scala.concurrent.ExecutionContext.Implicits.global

class AftBatchedDataCacheRepositorySpec extends AnyWordSpec with MockitoSugar with Matchers with MongoEmbedDatabase with BeforeAndAfter {

  import AftBatchedDataCacheRepositorySpec._

  var mongoProps: MongodProps = null

  before { mongoProps = mongoStart() }

  after { mongoStop(mongoProps) }

  "remove" must {
    "remove a document if present" in {
      val aftBatchedDataCacheRepository = new AftBatchedDataCacheRepository(rmc, configuration, batchService )

      val modifier = BSONDocument("$set" -> Json.obj("uniqueAftId" -> uniqueAftId ))

      whenReady(aftBatchedDataCacheRepository.collection.update.one(selectorByUniqueAftId(id, sessionId), modifier, upsert = true)) { _ =>
        whenReady(aftBatchedDataCacheRepository.remove(id, sessionId)) { result =>
          result mustBe true
          whenReady(aftBatchedDataCacheRepository.find("uniqueAftId" -> uniqueAftId)) { result =>
            result.isEmpty mustBe true
          }
        }
      }
    }
  }
}

object AftBatchedDataCacheRepositorySpec {
  private val id = "aa"
  private val sessionId = "bb"
  private val uniqueAftId = id + sessionId
  private val app = new GuiceApplicationBuilder()
    .overrides(
      //bind[AftBatchedDataCacheRepository].toInstance(mockDataCacheRepository)
    )
    .build()
  private val configuration = app.injector.instanceOf[Configuration]
  private val batchService = new BatchService()
  private val databaseName = "aft-batches"
  private val mongoUri: String = s"mongodb://127.0.0.1:27017/$databaseName?heartbeatFrequencyMS=1000&rm.failover=default"
  private val mongoConnectorForTest = MongoConnector(mongoUri)
  private val rmc = new ReactiveMongoComponent {
    override def mongoConnector: MongoConnector = mongoConnectorForTest
  }
  private def selector(batchType: BatchType, batchNo: Int): BSONDocument =
    BSONDocument("uniqueAftId" -> (id + sessionId), "batchType" -> batchType.toString, "batchNo" -> batchNo)

    private def selectorByUniqueAftId(id:String, sessionId:String): BSONDocument =
    BSONDocument("uniqueAftId" -> (id + sessionId))
}

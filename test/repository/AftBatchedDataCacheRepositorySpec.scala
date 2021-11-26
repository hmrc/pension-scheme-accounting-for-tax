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
import services.BatchService.{BatchInfo, BatchType}
import com.github.simplyscala.{MongoEmbedDatabase, MongodProps}
import de.flapdoodle.embed.process.runtime.Network
import org.mockito.MockitoSugar
import org.scalatest.BeforeAndAfter
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.{JsObject, JsArray, Json}
import uk.gov.hmrc.mongo.MongoConnector

import scala.concurrent.ExecutionContext.Implicits.global

class AftBatchedDataCacheRepositorySpec extends AnyWordSpec with MockitoSugar with Matchers with MongoEmbedDatabase with BeforeAndAfter {


  var mongoProps: MongodProps = null

  before {
    //mongoProps = mongoStart()   // by default port = 12345 & version = Version.3.3.1

  }                               // add your own port & version parameters in mongoStart method if you need it

  after { mongoStop(mongoProps) }

  "aa" must {
    "aa" in {
      mongoProps = mongoStart()
      val mockConfig = mock[Configuration]
      val batchService = new BatchService()
      val a = Network.getLocalHost
      val b = Network.getFreeServerPort
      val c = a.getHostAddress + ":" + b.toString
      println("\n>>" + c)

      //ava.lang.Exception: Invalid mongodb.uri '127.0.1.1:33659'

      val xx: MongoConnector = MongoConnector(c)

      val a1 = new ReactiveMongoComponent {
        override def mongoConnector = xx
      }
      val repository = new AftBatchedDataCacheRepository(a1, mockConfig, batchService )

      val id = "aa"
      val sessionId = "bb"
      val bi = BatchInfo(BatchType.ChargeG, 1, JsArray())

      def selector(batchType: BatchType, batchNo: Int): BSONDocument =
        BSONDocument("uniqueAftId" -> (id + sessionId), "batchType" -> batchType.toString, "batchNo" -> batchNo)

      val modifier = BSONDocument("$set" -> jsonPayloadToSave(id, bi))

      repository.collection.update.one(selector(bi.batchType, bi.batchNo), modifier, upsert = true)

      repository.remove(id, sessionId).map { result =>
        result mustBe true
      }
    }
  }

  def jsonPayloadToSave(id: String, batchInfo:BatchInfo):JsObject = {
    val userDataBatchSizeJson:JsObject = if (batchInfo.batchType == BatchType.SessionData) {
      Json.obj(
        "batchSize" -> 5
      )
    } else {
      Json.obj()
    }
    Json.obj(
      "id" -> id,
      "data" -> batchInfo.jsValue,
      "expireAt" -> 1000
    ) ++ userDataBatchSizeJson
  }

  //test("some test with mongo") {
  //  ...
  //}

  //test("test with fixture") {
  //  //add your own port & version parameters in withEmbedMongoFixture method if you need it
  //  withEmbedMongoFixture() { mongodProps =>
  //    mongodProps
  //
  //
  //    //val person = MongoDBObject("name"->"Manish")
  //    //val queryResult = mongoCRUD.insertPerson(person)
  //    ////assert if the document was inserted into database
  //    //println(mongoCRUD.findPerson(person).toList)
  //    //assert(mongoCRUD.findPerson(person).count === 1)
  //
  //    // do some mongo database operations
  //    // in this fixture the dabatase is started
  //    // at the end of this fixture the database is stopped
  //  }
  //}

}

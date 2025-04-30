/*
 * Copyright 2025 HM Revenue & Customs
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

///*
// * Copyright 2025 HM Revenue & Customs
// *
// * Licensed under the Apache License, Version 2.0 (the "License");
// * you may not use this file except in compliance with the License.
// * You may obtain a copy of the License at
// *
// *     http://www.apache.org/licenses/LICENSE-2.0
// *
// * Unless required by applicable law or agreed to in writing, software
// * distributed under the License is distributed on an "AS IS" BASIS,
// * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// * See the License for the specific language governing permissions and
// * limitations under the License.
// */
//
//package services
//
//import org.mockito.ArgumentMatchers.any
//import org.mockito.Mockito._
//import org.mongodb.scala._
//import org.mongodb.scala.bson.collection.immutable.Document
//import org.mongodb.scala.bson.conversions.Bson
//import org.scalatest.BeforeAndAfterEach
//import org.scalatest.concurrent.Eventually.eventually
//import org.scalatest.concurrent.ScalaFutures
//import org.scalatest.matchers.should.Matchers
//import org.scalatest.wordspec.AsyncWordSpec
//import org.scalatestplus.mockito.MockitoSugar
//import uk.gov.hmrc.mongo.MongoComponent
//import uk.gov.hmrc.mongo.lock.MongoLockRepository
//
//import scala.concurrent.duration.Duration
//import scala.concurrent.{ExecutionContext, Future}
//
//class MigrationServiceSpec
//  extends AsyncWordSpec
//    with Matchers
//    with MockitoSugar
//    with ScalaFutures
//    with BeforeAndAfterEach {
//
//  implicit val ec: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global
//
//  private val mockLockRepo = mock[MongoLockRepository]
//  private val mockMongoComponent = mock[MongoComponent]
//  private val mockDatabase = mock[MongoDatabase]
//  private val mockCollection = mock[MongoCollection[Document]]
//  private val mockFindObservable = mock[FindObservable[Document]]
//  private val mockUpdateObservable = mock[SingleObservable[Document]]
//
//  override def beforeEach(): Unit = {
//    reset(mockLockRepo, mockMongoComponent, mockDatabase, mockCollection, mockFindObservable, mockUpdateObservable)
//  }
//
//  "MigrationService" should {
//    "run migration logic in constructor and update documents" in {
//      val testDocument = Document("_id" -> new org.bson.types.ObjectId(), "expireAt" -> "someString")
//
//      when(mockMongoComponent.database).thenReturn(mockDatabase)
//      when(mockDatabase.getCollection("file-upload-response")).thenReturn(mockCollection)
//      when(mockCollection.find(any[Bson])).thenReturn(mockFindObservable)
//      when(mockFindObservable.toFuture()).thenReturn(Future.successful(Seq(testDocument)))
//      when(mockCollection.findOneAndUpdate(any[Bson], any[Bson])).thenReturn(mockUpdateObservable)
//      when(mockUpdateObservable.toFuture()).thenReturn(Future.successful(Document()))
//
//      when(mockLockRepo.takeLock(any[String], any[String], any[Duration]))
//        .thenReturn(Future.successful(true))
//
//      when(mockLockRepo.releaseLock(any[String], any[String]))
//        .thenReturn(Future.successful(()))
//
//      new MigrationService(mockLockRepo, mockMongoComponent) {}
//
//      eventually {
//        verify(mockCollection).findOneAndUpdate(any[Bson], any[Bson])
//      }
//
//      succeed
//    }
//  }
//
//}

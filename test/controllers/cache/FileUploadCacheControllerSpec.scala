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

package controllers.cache

import controllers.actions.PsaPspEnrolmentAuthAction
import org.mockito.ArgumentMatchers.{eq => eqTo, _}
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterEach
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.mockito.MockitoSugar
import play.api.Application
import play.api.inject.bind
import play.api.inject.guice.{GuiceApplicationBuilder, GuiceableModule}
import play.api.libs.json.{JsObject, Json}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import repository._
import repository.model.{FileUploadDataCache, FileUploadStatus}
import utils.AuthUtils.FakePsaPspEnrolmentAuthAction

import java.time.Instant
import scala.concurrent.Future
import scala.util.Random
import org.apache.pekko.util.ByteString

class FileUploadCacheControllerSpec extends AnyWordSpec with Matchers with MockitoSugar with BeforeAndAfterEach {

  private val repo = mock[FileUploadReferenceCacheRepository]
  private val uploadId = "uploadId"
  private val referenceId = "reference"
  private val fakeRequestReference = FakeRequest().withHeaders("reference" -> referenceId)
  private val fakeRequest = FakeRequest().withHeaders("uploadId" -> uploadId)
  private val fakePostRequest = FakeRequest("POST", "/").withHeaders("uploadId" -> uploadId)
  private def randomString: ByteString = ByteString(Random.alphanumeric.dropWhile(_.isDigit).take(20).mkString)
  private val modules: Seq[GuiceableModule] = Seq(
    bind[FileUploadReferenceCacheRepository].toInstance(repo),
    bind[AftBatchedDataCacheRepository].toInstance(mock[AftBatchedDataCacheRepository]),
    bind[AftOverviewCacheRepository].toInstance(mock[AftOverviewCacheRepository]),
    bind[FileUploadOutcomeRepository].toInstance(mock[FileUploadOutcomeRepository]),
    bind[FinancialInfoCacheRepository].toInstance(mock[FinancialInfoCacheRepository]),
    bind[FinancialInfoCreditAccessRepository].toInstance(mock[FinancialInfoCreditAccessRepository]),
    bind[PsaPspEnrolmentAuthAction].toInstance(new FakePsaPspEnrolmentAuthAction)
  )

  val app: Application = new GuiceApplicationBuilder()
    .configure(conf = "auditing.enabled" -> false, "metrics.enabled" -> false, "metrics.jvm" -> false, "run.mode" -> "Test")
    .overrides(modules *).build()
  val controller: FileUploadCacheController = app.injector.instanceOf[FileUploadCacheController]

  override protected def beforeEach(): Unit = {
    reset(repo)
    super.beforeEach()
  }


  "FileUploadCacheController" when {
    "calling requestUpload" must {
      "return OK with the data" in {
        when(repo.requestUpload(eqTo(uploadId), eqTo(referenceId))(any())).thenReturn(Future.successful((): Unit))

        val result = controller.requestUpload(fakePostRequest.withJsonBody(Json.obj("reference" -> referenceId)))
        status(result).mustEqual(OK)
      }

      "return BAD REQUEST when the request body cannot be parsed" in {
        when(repo.requestUpload(any(), any())(any())).thenReturn(Future.successful((): Unit))

        val result = controller.requestUpload(fakePostRequest.withRawBody(randomString))
        status(result).mustEqual(BAD_REQUEST)
      }
    }

    "calling getUploadResult" must {
      "return OK with the data" in {
        val dateTimeNow = Instant.now()
        val fileUploadDataCache = FileUploadDataCache(uploadId, referenceId, FileUploadStatus("InProgress"), dateTimeNow, dateTimeNow, dateTimeNow)
        when(repo.getUploadResult(eqTo(uploadId))(any())).thenReturn(Future.successful(Some(fileUploadDataCache)))
        val result = controller.getUploadResult(fakeRequest)
        status(result) mustEqual OK
        val res = contentAsJson(result).as[JsObject]
        (res \ "uploadId").as[String] must include("uploadId")
        (res \ "reference").as[String] must include("reference")
        (res \ "status" \ "_type").as[String] must include("InProgress")
      }
      "return NOT FOUND when the data doesn't exist" in {
        when(repo.getUploadResult(eqTo(uploadId))(any())).thenReturn(Future.successful(None))

        val result = controller.getUploadResult(fakeRequest)
        status(result) mustEqual NOT_FOUND
      }

      "throw an exception when the repository call fails" in {
        when(repo.getUploadResult(eqTo(uploadId))(any())).thenReturn(Future.failed(new Exception()))
        val result = controller.getUploadResult(fakeRequest)
        an[Exception] must be thrownBy status(result)
        }
    }

    "calling registerUploadResult" must {
      "return OK with the data" in {
        val uploadStatus = FileUploadStatus("Success", None, None, Some("www.test.com"), Some("text/csv"), Some("test.csv"), Some("100".toLong))
        when(repo.updateStatus(any(), any())).thenReturn(Future.successful((): Unit))

        val result = controller.registerUploadResult(fakeRequestReference.withJsonBody(Json.toJson(uploadStatus)))
        status(result) mustEqual OK
      }

      "return BAD REQUEST when the request body cannot be parsed" in {
        val uploadStatus = FileUploadStatus("Success", None, None, Some("www.test.com"), Some("text/csv"), Some("test.csv"), Some("100".toLong))
        val fileUploadDataCache = FileUploadDataCache(uploadId, referenceId, uploadStatus, Instant.now(), Instant.now(), Instant.now())
        when(repo.updateStatus(any(), any())).thenReturn(Future.successful(Some(fileUploadDataCache)))

        val result = controller.registerUploadResult(fakeRequestReference.withRawBody(randomString))
        status(result) mustEqual BAD_REQUEST
      }
    }
  }
}

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

package controllers.cache

import akka.util.ByteString
import controllers.cache.FinancialInfoCacheController.IdNotFoundFromAuth
import org.apache.commons.lang3.RandomUtils
import org.joda.time.{DateTime, DateTimeZone}
import org.mockito.ArgumentMatchers.{eq => eqTo, _}
import org.mockito.MockitoSugar
import org.scalatest.BeforeAndAfter
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.inject.bind
import play.api.inject.guice.{GuiceApplicationBuilder, GuiceableModule}
import play.api.libs.json.Json
import play.api.test.FakeRequest
import play.api.test.Helpers._
import repository.FileUploadReferenceCacheRepository
import repository.model.{FileUploadDataCache, FileUploadStatus}
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.Future

class FileUploadCacheControllerSpec extends AnyWordSpec with Matchers with MockitoSugar with BeforeAndAfter {

  implicit val hc: HeaderCarrier = HeaderCarrier()

  private val repo = mock[FileUploadReferenceCacheRepository]
  private val authConnector: AuthConnector = mock[AuthConnector]
  private val uploadId = "uploadId"
  private val referenceId = "reference"
  private val fakeRequestReference = FakeRequest().withHeaders("reference" -> referenceId)
  private val fakeRequest = FakeRequest().withHeaders("uploadId" -> uploadId)
  private val fakePostRequest = FakeRequest("POST", "/").withHeaders("uploadId" -> uploadId)

  private val modules: Seq[GuiceableModule] = Seq(
    bind[AuthConnector].toInstance(authConnector),
    bind[FileUploadReferenceCacheRepository].toInstance(repo)
  )

  before {
    reset(repo)
    reset(authConnector)
  }

  "FileUploadCacheController" when {
    "calling requestUpload" must {
      "return OK with the data" in {
        val app = new GuiceApplicationBuilder()
          .configure(conf = "auditing.enabled" -> false, "metrics.enabled" -> false, "metrics.jvm" -> false, "run.mode" -> "Test")
          .overrides(modules: _*).build()
        val controller = app.injector.instanceOf[FileUploadCacheController]
        when(repo.requestUpload(eqTo(uploadId), eqTo(referenceId))(any())) thenReturn Future.successful(true)
        when(authConnector.authorise[Option[String]](any(), any())(any(), any())) thenReturn Future.successful(Some(uploadId))

        val result = controller.requestUpload(fakePostRequest.withJsonBody(Json.obj("reference" -> referenceId)))
        status(result) mustEqual OK
      }

      "return BAD REQUEST when the request body cannot be parsed" in {
        val app = new GuiceApplicationBuilder()
          .configure(conf = "auditing.enabled" -> false, "metrics.enabled" -> false, "metrics.jvm" -> false, "run.mode" -> "Test")
          .overrides(modules: _*).build()
        val controller = app.injector.instanceOf[FileUploadCacheController]
        when(repo.requestUpload(any(), any())(any())) thenReturn Future.successful(true)
        when(authConnector.authorise[Option[String]](any(), any())(any(), any())) thenReturn Future.successful(Some(uploadId))

        val result = controller.requestUpload(fakePostRequest.withRawBody(ByteString(RandomUtils.nextBytes("512001".toInt))))
        status(result) mustEqual BAD_REQUEST
      }

      "throw an exception when the call is not authorised" in {
        val app = new GuiceApplicationBuilder()
          .configure(conf = "auditing.enabled" -> false, "metrics.enabled" -> false, "metrics.jvm" -> false, "run.mode" -> "Test")
          .overrides(modules: _*).build()
        val controller = app.injector.instanceOf[FileUploadCacheController]
        when(authConnector.authorise[Option[String]](any(), any())(any(), any())) thenReturn Future.successful(None)

        val result = controller.requestUpload(fakePostRequest.withJsonBody(Json.obj(fields = "reference" -> "referenceId")))
        an[IdNotFoundFromAuth] must be thrownBy status(result)
      }
    }

    "calling getUploadResult" must {
      "return OK with the data" in {
        val app = new GuiceApplicationBuilder()
          .configure(conf = "auditing.enabled" -> false, "metrics.enabled" -> false, "metrics.jvm" -> false, "run.mode" -> "Test")
          .overrides(modules: _*).build()
        val controller = app.injector.instanceOf[FileUploadCacheController]
        val fileUploadDataCache=FileUploadDataCache(uploadId,referenceId,FileUploadStatus("InProgress")
          ,DateTime.now(DateTimeZone.UTC),DateTime.now(DateTimeZone.UTC))
        when(repo.getUploadResult(eqTo(uploadId))(any())) thenReturn Future.successful(Some(fileUploadDataCache))
        when(authConnector.authorise[Option[String]](any(), any())(any(), any())) thenReturn Future.successful(Some(uploadId))

        val result = controller.getUploadResult(fakeRequest)
        status(result) mustEqual OK
        contentAsJson(result) mustEqual Json.obj(fields = "_type" -> "InProgress")
      }

      "return NOT FOUND when the data doesn't exist" in {
        val app = new GuiceApplicationBuilder()
          .configure(conf = "auditing.enabled" -> false, "metrics.enabled" -> false, "metrics.jvm" -> false, "run.mode" -> "Test")
          .overrides(modules: _*).build()
        val controller = app.injector.instanceOf[FileUploadCacheController]
        when(repo.getUploadResult(eqTo(uploadId))(any())) thenReturn Future.successful(None)
        when(authConnector.authorise[Option[String]](any(), any())(any(), any())) thenReturn Future.successful(Some(uploadId))

        val result = controller.getUploadResult(fakeRequest)
        status(result) mustEqual NOT_FOUND
      }

      "throw an exception when the repository call fails" in {
        val app = new GuiceApplicationBuilder()
          .configure(conf = "auditing.enabled" -> false, "metrics.enabled" -> false, "metrics.jvm" -> false, "run.mode" -> "Test")
          .overrides(modules: _*).build()
        val controller = app.injector.instanceOf[FileUploadCacheController]
        when(repo.getUploadResult(eqTo(uploadId))(any())) thenReturn Future.failed(new Exception())
        when(authConnector.authorise[Option[String]](any(), any())(any(), any())) thenReturn Future.successful(Some(uploadId))
        val result = controller.getUploadResult(fakeRequest)
        an[Exception] must be thrownBy status(result)
      }

      "throw an exception when the call is not authorised" in {
        val app = new GuiceApplicationBuilder()
          .configure(conf = "auditing.enabled" -> false, "metrics.enabled" -> false, "metrics.jvm" -> false, "run.mode" -> "Test")
          .overrides(modules: _*).build()
        val controller = app.injector.instanceOf[FileUploadCacheController]
        when(authConnector.authorise[Option[String]](any(), any())(any(), any())) thenReturn Future.successful(None)

        val result = controller.getUploadResult(fakeRequest)
        an[IdNotFoundFromAuth] must be thrownBy status(result)
      }
    }

    "calling registerUploadResult" must {
      "return OK with the data" in {
        val app = new GuiceApplicationBuilder()
          .configure(conf = "auditing.enabled" -> false, "metrics.enabled" -> false, "metrics.jvm" -> false, "run.mode" -> "Test")
          .overrides(modules: _*).build()
        val controller = app.injector.instanceOf[FileUploadCacheController]
        val uploadStatus=FileUploadStatus("Success",Some("www.test.com"),Some("text/csv"),Some("test.csv"),Some("100".toLong))
        val fileUploadDataCache=FileUploadDataCache(uploadId,referenceId,uploadStatus,DateTime.now(DateTimeZone.UTC),DateTime.now(DateTimeZone.UTC))
        when(repo.updateStatus(any(), any())) thenReturn Future.successful(Some(fileUploadDataCache))
        when(authConnector.authorise[Option[String]](any(), any())(any(), any())) thenReturn Future.successful(Some(referenceId))

        val result = controller.registerUploadResult(fakeRequestReference.withJsonBody(Json.toJson(uploadStatus)))
        status(result) mustEqual OK
      }

      "return BAD REQUEST when the request body cannot be parsed" in {
        val app = new GuiceApplicationBuilder()
          .configure(conf = "auditing.enabled" -> false, "metrics.enabled" -> false, "metrics.jvm" -> false, "run.mode" -> "Test")
          .overrides(modules: _*).build()
        val controller = app.injector.instanceOf[FileUploadCacheController]
        val uploadStatus=FileUploadStatus("Success",Some("www.test.com"),Some("text/csv"),Some("test.csv"),Some("100".toLong))
        val fileUploadDataCache=FileUploadDataCache(uploadId,referenceId,uploadStatus,DateTime.now(DateTimeZone.UTC),DateTime.now(DateTimeZone.UTC))
        when(repo.updateStatus(any(), any())) thenReturn Future.successful(Some(fileUploadDataCache))
        when(authConnector.authorise[Option[String]](any(), any())(any(), any())) thenReturn Future.successful(Some(uploadId))

        val result = controller.registerUploadResult(fakeRequestReference.withRawBody(ByteString(RandomUtils.nextBytes("512001".toInt))))
        status(result) mustEqual BAD_REQUEST
      }
    }
  }
}
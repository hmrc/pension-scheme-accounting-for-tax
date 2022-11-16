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
import controllers.cache.FileUploadOutcomeController.IdNotFoundFromAuth
import org.apache.commons.lang3.RandomUtils
import org.mockito.ArgumentMatchers.{eq => eqTo, _}
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfter
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.mockito.MockitoSugar
import play.api.inject.bind
import play.api.inject.guice.{GuiceApplicationBuilder, GuiceableModule}
import play.api.libs.json.Json
import play.api.test.FakeRequest
import play.api.test.Helpers._
import repository.FileUploadOutcomeRepository
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.Future

class FileUploadOutcomeControllerSpec extends AnyWordSpec with Matchers with MockitoSugar with BeforeAndAfter {

  implicit val hc: HeaderCarrier = HeaderCarrier()

  private val repo = mock[FileUploadOutcomeRepository]
  private val authConnector: AuthConnector = mock[AuthConnector]
  private val id = "id"
  private val fakeRequest = FakeRequest()
  private val fakePostRequest = FakeRequest("POST", "/")

  private val modules: Seq[GuiceableModule] = Seq(
    bind[AuthConnector].toInstance(authConnector),
    bind[FileUploadOutcomeRepository].toInstance(repo)
  )

  before {
    reset(repo)
    reset(authConnector)
  }

  "FileUploadOutcomeController" when {
    "calling get" must {
      "return OK with the data" in {
        val app = new GuiceApplicationBuilder()
          .configure(conf = "auditing.enabled" -> false, "metrics.enabled" -> false, "metrics.jvm" -> false, "run.mode" -> "Test")
          .overrides(modules: _*).build()
        val controller = app.injector.instanceOf[FileUploadOutcomeController]
        when(repo.get(eqTo(id))(any())) thenReturn Future.successful(Some(Json.obj("testId" -> "data")))
        when(authConnector.authorise[Option[String]](any(), any())(any(), any())) thenReturn Future.successful(Some(id))

        val result = controller.get(fakeRequest)
        status(result) mustEqual OK
        contentAsJson(result) mustEqual Json.obj(fields = "testId" -> "data")
      }

      "return NOT FOUND when the data doesn't exist" in {
        val app = new GuiceApplicationBuilder()
          .configure(conf = "auditing.enabled" -> false, "metrics.enabled" -> false, "metrics.jvm" -> false, "run.mode" -> "Test")
          .overrides(modules: _*).build()
        val controller = app.injector.instanceOf[FileUploadOutcomeController]
        when(repo.get(eqTo(id))(any())) thenReturn Future.successful(None)
        when(authConnector.authorise[Option[String]](any(), any())(any(), any())) thenReturn Future.successful(Some(id))

        val result = controller.get(fakeRequest)
        status(result) mustEqual NOT_FOUND
      }

      "throw an exception when the repository call fails" in {
        val app = new GuiceApplicationBuilder()
          .configure(conf = "auditing.enabled" -> false, "metrics.enabled" -> false, "metrics.jvm" -> false, "run.mode" -> "Test")
          .overrides(modules: _*).build()
        val controller = app.injector.instanceOf[FileUploadOutcomeController]
        when(repo.get(eqTo(id))(any())) thenReturn Future.failed(new Exception())
        when(authConnector.authorise[Option[String]](any(), any())(any(), any())) thenReturn Future.successful(Some(id))
        val result = controller.get(fakeRequest)
        an[Exception] must be thrownBy status(result)
      }

      "throw an exception when the call is not authorised" in {
        val app = new GuiceApplicationBuilder()
          .configure(conf = "auditing.enabled" -> false, "metrics.enabled" -> false, "metrics.jvm" -> false, "run.mode" -> "Test")
          .overrides(modules: _*).build()
        val controller = app.injector.instanceOf[FileUploadOutcomeController]
        when(authConnector.authorise[Option[String]](any(), any())(any(), any())) thenReturn Future.successful(None)

        val result = controller.get(fakeRequest)
        an[IdNotFoundFromAuth] must be thrownBy status(result)
      }

    }

    "calling save" must {

      "return OK when the data is saved successfully" in {
        val app = new GuiceApplicationBuilder()
          .configure(conf = "auditing.enabled" -> false, "metrics.enabled" -> false, "metrics.jvm" -> false, "run.mode" -> "Test")
          .overrides(modules: _*).build()
        val controller = app.injector.instanceOf[FileUploadOutcomeController]
        when(repo.save(any(), any())(any())) thenReturn Future.successful(true)
        when(authConnector.authorise[Option[String]](any(), any())(any(), any())) thenReturn Future.successful(Some(id))

        val result = controller.post(fakePostRequest.withJsonBody(Json.obj("value" -> "data")))
        status(result) mustEqual CREATED
      }

      "return BAD REQUEST when the request body cannot be parsed" in {
        val app = new GuiceApplicationBuilder()
          .configure(conf = "auditing.enabled" -> false, "metrics.enabled" -> false, "metrics.jvm" -> false, "run.mode" -> "Test")
          .overrides(modules: _*).build()
        val controller = app.injector.instanceOf[FileUploadOutcomeController]
        when(repo.save(any(), any())(any())) thenReturn Future.successful(true)
        when(authConnector.authorise[Option[String]](any(), any())(any(), any())) thenReturn Future.successful(Some(id))

        val result = controller.post(fakePostRequest.withRawBody(ByteString(RandomUtils.nextBytes(512001))))
        status(result) mustEqual BAD_REQUEST
      }

      "throw an exception when the call is not authorised" in {
        val app = new GuiceApplicationBuilder()
          .configure(conf = "auditing.enabled" -> false, "metrics.enabled" -> false, "metrics.jvm" -> false, "run.mode" -> "Test")
          .overrides(modules: _*).build()
        val controller = app.injector.instanceOf[FileUploadOutcomeController]
        when(authConnector.authorise[Option[String]](any(), any())(any(), any())) thenReturn Future.successful(None)

        val result = controller.post(fakePostRequest.withJsonBody(Json.obj(fields = "value" -> "data")))
        an[IdNotFoundFromAuth] must be thrownBy status(result)
      }
    }

    "calling delete" must {
      "return OK when the data is removed successfully" in {
        val app = new GuiceApplicationBuilder()
          .configure(conf = "auditing.enabled" -> false, "metrics.enabled" -> false, "metrics.jvm" -> false, "run.mode" -> "Test")
          .overrides(modules: _*).build()
        val controller = app.injector.instanceOf[FileUploadOutcomeController]
        when(repo.remove(eqTo(id))(any())) thenReturn Future.successful(true)
        when(authConnector.authorise[Option[String]](any(), any())(any(), any())) thenReturn Future.successful(Some(id))

        val result = controller.delete(fakeRequest)
        status(result) mustEqual OK
      }

      "throw an exception when the call is not authorised" in {
        val app = new GuiceApplicationBuilder()
          .configure(conf = "auditing.enabled" -> false, "metrics.enabled" -> false, "metrics.jvm" -> false, "run.mode" -> "Test")
          .overrides(modules: _*).build()
        val controller = app.injector.instanceOf[FileUploadOutcomeController]
        when(authConnector.authorise[Option[String]](any(), any())(any(), any())) thenReturn Future.successful(None)

        val result = controller.delete(fakeRequest)
        an[IdNotFoundFromAuth] must be thrownBy status(result)
      }
    }
  }
}

/*
 * Copyright 2020 HM Revenue & Customs
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
import org.apache.commons.lang3.RandomUtils
import org.mockito.Matchers
import org.mockito.Matchers.{eq => eqTo, _}
import org.mockito.Mockito.{reset, when}
import org.scalatest.{BeforeAndAfter, MustMatchers, WordSpec}
import org.scalatestplus.mockito.MockitoSugar
import play.api.inject.bind
import play.api.inject.guice.{GuiceApplicationBuilder, GuiceableModule}
import play.api.libs.json.Json
import play.api.test.FakeRequest
import play.api.test.Helpers._
import repository.DataCacheRepository
import repository.model.SessionData
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.auth.core.retrieve.Name
import uk.gov.hmrc.http.BadRequestException

import scala.concurrent.Future

class DataCacheControllerSpec extends WordSpec with MustMatchers with MockitoSugar with BeforeAndAfter {

  import DataCacheController._

  private val repo = mock[DataCacheRepository]
  private val authConnector: AuthConnector = mock[AuthConnector]
  private val id = "id"
  private val sessionId = "sessionId"
  private val fakeRequest = FakeRequest().withHeaders("X-Session-ID" -> sessionId, "id" -> id)
  private val fakePostRequest = FakeRequest("POST", "/").withHeaders("X-Session-ID" -> sessionId, "id" -> id)

  private val modules: Seq[GuiceableModule] = Seq(
    bind[AuthConnector].toInstance(authConnector),
    bind[DataCacheRepository].toInstance(repo)
  )

  before {
    reset(repo)
    reset(authConnector)
  }

  "DataCacheController" when {
    "calling get" must {
      "return OK with the data" in {
        val app = new GuiceApplicationBuilder()
          .configure(conf = "auditing.enabled" -> false, "metrics.enabled" -> false, "metrics.jvm" -> false, "run.mode" -> "Test")
          .overrides(modules: _*).build()
        val controller = app.injector.instanceOf[DataCacheController]
        when(repo.get(eqTo(id), eqTo(sessionId))(any())) thenReturn Future.successful(Some(Json.obj("testId" -> "data")))
        when(authConnector.authorise[Option[Name]](any(), any())(any(), any())) thenReturn Future.successful(Some(Name(Some("test"), Some("name"))))

        val result = controller.get(fakeRequest)
        status(result) mustEqual OK
        contentAsJson(result) mustEqual Json.obj(fields = "testId" -> "data")
      }

      "return NOT FOUND when the data doesn't exist" in {
        val app = new GuiceApplicationBuilder()
          .configure(conf = "auditing.enabled" -> false, "metrics.enabled" -> false, "metrics.jvm" -> false, "run.mode" -> "Test")
          .overrides(modules: _*).build()
        val controller = app.injector.instanceOf[DataCacheController]
        when(repo.get(eqTo(id), eqTo(sessionId))(any())) thenReturn Future.successful(None)
        when(authConnector.authorise[Option[Name]](any(), any())(any(), any())) thenReturn Future.successful(Some(Name(Some("test"), Some("name"))))

        val result = controller.get(fakeRequest)
        status(result) mustEqual NOT_FOUND
      }

      "throw an exception when the repository call fails" in {
        val app = new GuiceApplicationBuilder()
          .configure(conf = "auditing.enabled" -> false, "metrics.enabled" -> false, "metrics.jvm" -> false, "run.mode" -> "Test")
          .overrides(modules: _*).build()
        val controller = app.injector.instanceOf[DataCacheController]
        when(repo.get(eqTo(id), eqTo(sessionId))(any())) thenReturn Future.failed(new Exception())
        when(authConnector.authorise[Option[Name]](any(), any())(any(), any())) thenReturn Future.successful(Some(Name(Some("test"), Some("name"))))

        val result = controller.get(fakeRequest)
        an[Exception] must be thrownBy status(result)
      }

      "throw an exception when the call is not authorised" in {
        val app = new GuiceApplicationBuilder()
          .configure(conf = "auditing.enabled" -> false, "metrics.enabled" -> false, "metrics.jvm" -> false, "run.mode" -> "Test")
          .overrides(modules: _*).build()
        val controller = app.injector.instanceOf[DataCacheController]
        when(authConnector.authorise[Option[Name]](any(), any())(any(), any())) thenReturn Future.successful(None)

        val result = controller.get(fakeRequest)
        an[CredNameNotFoundFromAuth] must be thrownBy status(result)
      }

    }

    "calling save" must {

      "return OK when the data is saved successfully" in {
        val app = new GuiceApplicationBuilder()
          .configure(conf = "auditing.enabled" -> false, "metrics.enabled" -> false, "metrics.jvm" -> false, "run.mode" -> "Test")
          .overrides(modules: _*).build()
        val controller = app.injector.instanceOf[DataCacheController]
        when(repo.save(any(), any(), any())(any())) thenReturn Future.successful(true)
        when(authConnector.authorise[Option[Name]](any(), any())(any(), any())) thenReturn Future.successful(Some(Name(Some("test"), Some("name"))))

        val result = controller.save(fakePostRequest.withJsonBody(Json.obj("value" -> "data")))
        status(result) mustEqual CREATED
      }

      "return BAD REQUEST when the request body cannot be parsed" in {
        val app = new GuiceApplicationBuilder()
          .configure(conf = "auditing.enabled" -> false, "metrics.enabled" -> false, "metrics.jvm" -> false, "run.mode" -> "Test")
          .overrides(modules: _*).build()
        val controller = app.injector.instanceOf[DataCacheController]
        when(repo.save(any(), any(), any())(any())) thenReturn Future.successful(true)
        when(authConnector.authorise[Option[Name]](any(), any())(any(), any())) thenReturn Future.successful(Some(Name(Some("test"), Some("name"))))

        val result = controller.save(fakePostRequest.withRawBody(ByteString(RandomUtils.nextBytes(512001))))
        status(result) mustEqual BAD_REQUEST
      }

      "throw an exception when the call is not authorised" in {
        val app = new GuiceApplicationBuilder()
          .configure(conf = "auditing.enabled" -> false, "metrics.enabled" -> false, "metrics.jvm" -> false, "run.mode" -> "Test")
          .overrides(modules: _*).build()
        val controller = app.injector.instanceOf[DataCacheController]
        when(authConnector.authorise[Option[Name]](any(), any())(any(), any())) thenReturn Future.successful(None)

        val result = controller.save(fakePostRequest.withJsonBody(Json.obj(fields = "value" -> "data")))
        an[CredNameNotFoundFromAuth] must be thrownBy status(result)
      }
    }

    "calling remove" must {
      "return OK when the data is removed successfully" in {
        val app = new GuiceApplicationBuilder()
          .configure(conf = "auditing.enabled" -> false, "metrics.enabled" -> false, "metrics.jvm" -> false, "run.mode" -> "Test")
          .overrides(modules: _*).build()
        val controller = app.injector.instanceOf[DataCacheController]
        when(repo.remove(eqTo(id), eqTo(sessionId))(any())) thenReturn Future.successful(true)
        when(authConnector.authorise[Option[Name]](any(), any())(any(), any())) thenReturn Future.successful(Some(Name(Some("test"), Some("name"))))

        val result = controller.remove(fakeRequest)
        status(result) mustEqual OK
      }

      "throw an exception when the call is not authorised" in {
        val app = new GuiceApplicationBuilder()
          .configure(conf = "auditing.enabled" -> false, "metrics.enabled" -> false, "metrics.jvm" -> false, "run.mode" -> "Test")
          .overrides(modules: _*).build()
        val controller = app.injector.instanceOf[DataCacheController]
        when(authConnector.authorise[Option[Name]](any(), any())(any(), any())) thenReturn Future.successful(None)

        val result = controller.remove(fakeRequest)
        an[CredNameNotFoundFromAuth] must be thrownBy status(result)
      }
    }

    "calling getSessionData" must {
      "return OK with locked by user name" in {
        val app = new GuiceApplicationBuilder()
          .configure(conf = "auditing.enabled" -> false, "metrics.enabled" -> false, "run.mode" -> "Test")
          .overrides(modules: _*).build()

        val sd = SessionData("id", Some("test name"), 1, "")

        val controller = app.injector.instanceOf[DataCacheController]
        when(repo.getSessionData(any(), any())(any())) thenReturn Future.successful(Some(sd))
        when(authConnector.authorise[Option[Name]](any(), any())(any(), any())) thenReturn Future.successful(Some(Name(Some("test"), Some("name"))))

        val result = controller.getSessionData(fakeRequest)
        status(result) mustEqual OK
        contentAsJson(result) mustEqual Json.toJson(sd)
      }

      "return Not Found when it is not locked" in {
        val app = new GuiceApplicationBuilder()
          .configure(conf = "auditing.enabled" -> false, "metrics.enabled" -> false, "run.mode" -> "Test")
          .overrides(modules: _*).build()
        val controller = app.injector.instanceOf[DataCacheController]
        when(repo.getSessionData(any(), any())(any())) thenReturn Future.successful(None)
        when(authConnector.authorise[Option[Name]](any(), any())(any(), any())) thenReturn Future.successful(Some(Name(Some("test"), Some("name"))))

        val result = controller.getSessionData(fakeRequest)
        status(result) mustEqual NOT_FOUND
      }
    }
  }

  "calling setSessionData" must {
    "return OK when the data is saved successfully" in {
      val accessMode = "compile"
      val version = 1
      val app = new GuiceApplicationBuilder()
        .configure(conf = "auditing.enabled" -> false, "metrics.enabled" -> false, "metrics.jvm" -> false, "run.mode" -> "Test")
        .overrides(modules: _*).build()
      val controller = app.injector.instanceOf[DataCacheController]
      when(repo.setSessionData(Matchers.eq(id),
        Matchers.eq(Some("test name")), any(), any(),
        Matchers.eq(version), Matchers.eq(accessMode))(any())) thenReturn Future.successful(true)
      when(authConnector.authorise[Option[Name]](any(), any())(any(), any())) thenReturn Future.successful(Some(Name(Some("test"), Some("name"))))

      val result = controller.setSessionData(true)(fakePostRequest
        .withJsonBody(Json.obj("value" -> "data"))
          .withHeaders(
            "version" -> version.toString,
            "accessMode" -> accessMode
          )
      )
      status(result) mustEqual CREATED
    }

    "return BAD REQUEST when header data is missing" in {
      val accessMode = "compile"
      val version = 1
      val app = new GuiceApplicationBuilder()
        .configure(conf = "auditing.enabled" -> false, "metrics.enabled" -> false, "metrics.jvm" -> false, "run.mode" -> "Test")
        .overrides(modules: _*).build()
      val controller = app.injector.instanceOf[DataCacheController]
      when(repo.setSessionData(Matchers.eq(id),
        Matchers.eq(Some("test name")), any(), any(),
        Matchers.eq(version), Matchers.eq(accessMode))(any())) thenReturn Future.successful(true)
      when(authConnector.authorise[Option[Name]](any(), any())(any(), any())) thenReturn Future.successful(Some(Name(Some("test"), Some("name"))))

      val result = controller.setSessionData(true)(fakePostRequest
        .withJsonBody(Json.obj("value" -> "data"))
      )
      status(result) mustEqual BAD_REQUEST
    }

    "return BAD REQUEST when the request body cannot be parsed" in {
      val app = new GuiceApplicationBuilder()
        .configure(conf = "auditing.enabled" -> false, "metrics.enabled" -> false, "metrics.jvm" -> false, "run.mode" -> "Test")
        .overrides(modules: _*).build()
      val controller = app.injector.instanceOf[DataCacheController]
      when(repo.setSessionData(any(), any(), any(), any(), any(), any())(any())) thenReturn Future.successful(true)
      when(authConnector.authorise[Option[Name]](any(), any())(any(), any())) thenReturn Future.successful(Some(Name(Some("test"), Some("name"))))

      val result = controller
        .setSessionData(true)(fakePostRequest.withRawBody(ByteString(RandomUtils.nextBytes(512001))))
      status(result) mustEqual BAD_REQUEST
    }
  }




  "calling lockedBy" must {
    "return OK when the data is retrieved" in {
      val lockedByUser = "bob"
      val app = new GuiceApplicationBuilder()
        .configure(conf = "auditing.enabled" -> false, "metrics.enabled" -> false, "metrics.jvm" -> false, "run.mode" -> "Test")
        .overrides(modules: _*).build()
      val controller = app.injector.instanceOf[DataCacheController]
      when(repo.lockedBy(any(), any())(any())).thenReturn(Future.successful(Some(lockedByUser)))
      when(authConnector.authorise[Option[Name]](any(), any())(any(), any())) thenReturn Future.successful(Some(Name(Some("test"), Some("name"))))

      val result = controller.lockedBy()(fakeRequest)
      status(result) mustEqual OK
      contentAsString(result) mustBe lockedByUser
    }

    "return NOT FOUND when not locked" in {
      val app = new GuiceApplicationBuilder()
        .configure(conf = "auditing.enabled" -> false, "metrics.enabled" -> false, "metrics.jvm" -> false, "run.mode" -> "Test")
        .overrides(modules: _*).build()
      val controller = app.injector.instanceOf[DataCacheController]
      when(repo.lockedBy(any(), any())(any())).thenReturn(Future.successful(None))
      when(authConnector.authorise[Option[Name]](any(), any())(any(), any())) thenReturn Future.successful(Some(Name(Some("test"), Some("name"))))

      val result = controller.lockedBy()(fakeRequest)
      status(result) mustEqual NOT_FOUND
    }

  }

}

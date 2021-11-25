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

package controllers.cache

import akka.util.ByteString
import models.FeatureToggleName.BatchedRepositoryAFT
import models.{FeatureToggle, LockDetail}
import org.apache.commons.lang3.RandomUtils
import org.mockito.ArgumentMatchers.{eq => eqTo, _}
import org.scalatest.BeforeAndAfter
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.mockito.{MockitoSugar, ArgumentMatchers}
import play.api.inject.bind
import play.api.inject.guice.{GuiceApplicationBuilder, GuiceableModule}
import play.api.libs.json.Json
import play.api.test.FakeRequest
import play.api.test.Helpers._
import repository.{AftDataCacheRepository, AftBatchedDataCacheRepository}
import repository.model.SessionData
import services.FeatureToggleService
import uk.gov.hmrc.auth.core.{Enrolments, EnrolmentIdentifier, Enrolment, AuthConnector}
import uk.gov.hmrc.auth.core.retrieve.{~, Name}
import uk.gov.hmrc.auth.core.syntax.retrieved.authSyntaxForRetrieved
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.Future

class AftDataCacheControllerSpec extends AnyWordSpec with Matchers with MockitoSugar with BeforeAndAfter {

  import AftDataCacheController._

  implicit val hc: HeaderCarrier = HeaderCarrier()

  private val repo = mock[AftBatchedDataCacheRepository]
  private val repoUnbatched = mock[AftDataCacheRepository]
  private val authConnector: AuthConnector = mock[AuthConnector]
  private val id = "id"
  private val sessionId = "sessionId"
  private val fakeRequest = FakeRequest().withHeaders("X-Session-ID" -> sessionId, "id" -> id)
  private val fakePostRequest = FakeRequest("POST", "/").withHeaders("X-Session-ID" -> sessionId, "id" -> id)
  private val psaId = "A2222222"
  private val pspId = "22222222"
  private val mockFeatureToggleService = mock[FeatureToggleService]

  private val modules: Seq[GuiceableModule] = Seq(
    bind[AuthConnector].toInstance(authConnector),
    bind[AftBatchedDataCacheRepository].toInstance(repo),
    bind[AftDataCacheRepository].toInstance(repoUnbatched),
    bind[FeatureToggleService].toInstance(mockFeatureToggleService)
  )

  before {
    reset(repo, repoUnbatched)
    reset(authConnector)
    when(mockFeatureToggleService.get(any())).thenReturn(Future.successful(FeatureToggle.Enabled(BatchedRepositoryAFT)))
  }

  "DataCacheController" when {
    "calling get" must {
      "return OK with the data" in {
        val app = new GuiceApplicationBuilder()
          .configure(conf = "auditing.enabled" -> false, "metrics.enabled" -> false, "metrics.jvm" -> false, "run.mode" -> "Test")
          .overrides(modules: _*).build()
        val controller = app.injector.instanceOf[AftDataCacheController]
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
        val controller = app.injector.instanceOf[AftDataCacheController]
        when(repo.get(eqTo(id), eqTo(sessionId))(any())) thenReturn Future.successful(None)
        when(authConnector.authorise[Option[Name]](any(), any())(any(), any())) thenReturn Future.successful(Some(Name(Some("test"), Some("name"))))

        val result = controller.get(fakeRequest)
        status(result) mustEqual NOT_FOUND
      }

      "throw an exception when the repository call fails" in {
        val app = new GuiceApplicationBuilder()
          .configure(conf = "auditing.enabled" -> false, "metrics.enabled" -> false, "metrics.jvm" -> false, "run.mode" -> "Test")
          .overrides(modules: _*).build()
        val controller = app.injector.instanceOf[AftDataCacheController]
        when(repo.get(eqTo(id), eqTo(sessionId))(any())) thenReturn Future.failed(new Exception())
        when(authConnector.authorise[Option[Name]](any(), any())(any(), any())) thenReturn Future.successful(Some(Name(Some("test"), Some("name"))))

        val result = controller.get(fakeRequest)
        an[Exception] must be thrownBy status(result)
      }

      "throw an exception when the call is not authorised" in {
        val app = new GuiceApplicationBuilder()
          .configure(conf = "auditing.enabled" -> false, "metrics.enabled" -> false, "metrics.jvm" -> false, "run.mode" -> "Test")
          .overrides(modules: _*).build()
        val controller = app.injector.instanceOf[AftDataCacheController]
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
        val controller = app.injector.instanceOf[AftDataCacheController]
        when(repo.save(any(), any(), any(), any())(any())) thenReturn Future.successful(true)
        when(authConnector.authorise[Option[Name]](any(), any())(any(), any())) thenReturn Future.successful(Some(Name(Some("test"), Some("name"))))

        val result = controller.save(fakePostRequest.withJsonBody(Json.obj("value" -> "data")))
        status(result) mustEqual CREATED
      }

      "return BAD REQUEST when the request body cannot be parsed" in {
        val app = new GuiceApplicationBuilder()
          .configure(conf = "auditing.enabled" -> false, "metrics.enabled" -> false, "metrics.jvm" -> false, "run.mode" -> "Test")
          .overrides(modules: _*).build()
        val controller = app.injector.instanceOf[AftDataCacheController]
        when(repo.save(any(), any(), any(), any())(any())) thenReturn Future.successful(true)
        when(authConnector.authorise[Option[Name]](any(), any())(any(), any())) thenReturn Future.successful(Some(Name(Some("test"), Some("name"))))

        val result = controller.save(fakePostRequest.withRawBody(ByteString(RandomUtils.nextBytes(512001))))
        status(result) mustEqual BAD_REQUEST
      }

      "throw an exception when the call is not authorised" in {
        val app = new GuiceApplicationBuilder()
          .configure(conf = "auditing.enabled" -> false, "metrics.enabled" -> false, "metrics.jvm" -> false, "run.mode" -> "Test")
          .overrides(modules: _*).build()
        val controller = app.injector.instanceOf[AftDataCacheController]
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
        val controller = app.injector.instanceOf[AftDataCacheController]
        when(repo.remove(eqTo(id), eqTo(sessionId))(any())) thenReturn Future.successful(true)
        when(authConnector.authorise[Option[Name]](any(), any())(any(), any())) thenReturn Future.successful(Some(Name(Some("test"), Some("name"))))

        val result = controller.remove(fakeRequest)
        status(result) mustEqual OK
      }

      "throw an exception when the call is not authorised" in {
        val app = new GuiceApplicationBuilder()
          .configure(conf = "auditing.enabled" -> false, "metrics.enabled" -> false, "metrics.jvm" -> false, "run.mode" -> "Test")
          .overrides(modules: _*).build()
        val controller = app.injector.instanceOf[AftDataCacheController]
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

        val sd = SessionData("id", Some(LockDetail("test name", psaId)), 1, "", areSubmittedVersionsAvailable = false)

        val controller = app.injector.instanceOf[AftDataCacheController]
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
        val controller = app.injector.instanceOf[AftDataCacheController]
        when(repo.getSessionData(any(), any())(any())) thenReturn Future.successful(None)
        when(authConnector.authorise[Option[Name]](any(), any())(any(), any())) thenReturn Future.successful(Some(Name(Some("test"), Some("name"))))

        val result = controller.getSessionData(fakeRequest)
        status(result) mustEqual NOT_FOUND
      }
    }
  }

  private def expectedAuthorisations(id:String) = {
    val (enrolmentId, idType) = if (id.startsWith("A")) {
      ("HMRC-PODS-ORG", "PSAID")
    } else {
      ("HMRC-PODSPP-ORG", "PSPID")
    }

    Option(Name(Some("test"), Some("name"))) and
      Enrolments(
        Set(
          Enrolment(enrolmentId, Seq(EnrolmentIdentifier(idType, id)), "Activated", None)
        )
      )
  }

  "calling setSessionData" must {
    "return OK when the data is saved successfully for a PSA ID" in {
      val accessMode = "compile"
      val version = 1
      val app = new GuiceApplicationBuilder()
        .configure(conf = "auditing.enabled" -> false, "metrics.enabled" -> false, "metrics.jvm" -> false, "run.mode" -> "Test")
        .overrides(modules: _*).build()
      val controller = app.injector.instanceOf[AftDataCacheController]
      when(repo.setSessionData(
        ArgumentMatchers.eq(id),
        ArgumentMatchers.eq(Some(LockDetail("test name", psaId))), any(), any(),
        ArgumentMatchers.eq(version),
        ArgumentMatchers.eq(accessMode),
        ArgumentMatchers.eq(true)
      )(any())) thenReturn Future.successful(true)

      when(authConnector.authorise[Option[Name] ~ Enrolments](any(), any())(any(), any()))
        .thenReturn(Future.successful(expectedAuthorisations(psaId)))

      val result = controller.setSessionData(true)(fakePostRequest
        .withJsonBody(Json.obj("value" -> "data"))
          .withHeaders(
            "version" -> version.toString,
            "accessMode" -> accessMode,
            "areSubmittedVersionsAvailable" -> "true"
          )
      )
      status(result) mustEqual CREATED
    }

    "return OK when the data is saved successfully for a PSP ID" in {
      val accessMode = "compile"
      val version = 1
      val app = new GuiceApplicationBuilder()
        .configure(conf = "auditing.enabled" -> false, "metrics.enabled" -> false, "metrics.jvm" -> false, "run.mode" -> "Test")
        .overrides(modules: _*).build()
      val controller = app.injector.instanceOf[AftDataCacheController]
      when(repo.setSessionData(
        ArgumentMatchers.eq(id),
        ArgumentMatchers.eq(Some(LockDetail("test name", pspId))), any(), any(),
        ArgumentMatchers.eq(version),
        ArgumentMatchers.eq(accessMode),
        ArgumentMatchers.eq(true)
      )(any())) thenReturn Future.successful(true)

      when(authConnector.authorise[Option[Name] ~ Enrolments](any(), any())(any(), any()))
        .thenReturn(Future.successful(expectedAuthorisations(pspId)))

      val result = controller.setSessionData(true)(fakePostRequest
        .withJsonBody(Json.obj("value" -> "data"))
        .withHeaders(
          "version" -> version.toString,
          "accessMode" -> accessMode,
          "areSubmittedVersionsAvailable" -> "true"
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
      val controller = app.injector.instanceOf[AftDataCacheController]
      when(repo.setSessionData(ArgumentMatchers.eq(id),
        ArgumentMatchers.eq(Some(LockDetail("test name", psaId))), any(), any(),
        ArgumentMatchers.eq(version), ArgumentMatchers.eq(accessMode), any())(any())) thenReturn Future.successful(true)
      when(authConnector.authorise[Option[Name] ~ Enrolments](any(), any())(any(), any()))
        .thenReturn(Future.successful(expectedAuthorisations(psaId)))
      val result = controller.setSessionData(true)(fakePostRequest
        .withJsonBody(Json.obj("value" -> "data"))
      )
      status(result) mustEqual BAD_REQUEST
    }

    "return BAD REQUEST when the request body cannot be parsed" in {
      val app = new GuiceApplicationBuilder()
        .configure(conf = "auditing.enabled" -> false, "metrics.enabled" -> false, "metrics.jvm" -> false, "run.mode" -> "Test")
        .overrides(modules: _*).build()
      val controller = app.injector.instanceOf[AftDataCacheController]
      when(repo.setSessionData(any(), any(), any(), any(), any(), any(), any())(any())) thenReturn Future.successful(true)
      when(authConnector.authorise[Option[Name] ~ Enrolments](any(), any())(any(), any()))
        .thenReturn(Future.successful(expectedAuthorisations(psaId)))

      val result = controller
        .setSessionData(true)(fakePostRequest.withRawBody(ByteString(RandomUtils.nextBytes(512001))))
      status(result) mustEqual BAD_REQUEST
    }
  }

  "calling lockedBy" must {
    "return OK when the data is retrieved" in {
      val lockedByUser = LockDetail("bob", psaId)
      val app = new GuiceApplicationBuilder()
        .configure(conf = "auditing.enabled" -> false, "metrics.enabled" -> false, "metrics.jvm" -> false, "run.mode" -> "Test")
        .overrides(modules: _*).build()
      val controller = app.injector.instanceOf[AftDataCacheController]
      when(repo.lockedBy(any(), any())(any())).thenReturn(Future.successful(Some(lockedByUser)))
      when(authConnector.authorise[Option[Name]](any(), any())(any(), any())) thenReturn Future.successful(Some(Name(Some("test"), Some("name"))))

      val result = controller.lockedBy()(fakeRequest)
      status(result) mustEqual OK
      contentAsString(result) mustBe Json.toJson(lockedByUser).toString
    }

    "return NOT FOUND when not locked" in {
      val app = new GuiceApplicationBuilder()
        .configure(conf = "auditing.enabled" -> false, "metrics.enabled" -> false, "metrics.jvm" -> false, "run.mode" -> "Test")
        .overrides(modules: _*).build()
      val controller = app.injector.instanceOf[AftDataCacheController]
      when(repo.lockedBy(any(), any())(any())).thenReturn(Future.successful(None))
      when(authConnector.authorise[Option[Name]](any(), any())(any(), any())) thenReturn Future.successful(Some(Name(Some("test"), Some("name"))))

      val result = controller.lockedBy()(fakeRequest)
      status(result) mustEqual NOT_FOUND
    }
  }
}

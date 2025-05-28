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

import connectors.MinimalDetailsConnector
import models.{IndividualDetails, LockDetail, MinimalDetails}
import org.apache.pekko.util.ByteString
import org.mockito.ArgumentMatchers
import org.mockito.ArgumentMatchers.{eq => eqTo, _}
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfter
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.mockito.MockitoSugar
import play.api.Application
import play.api.inject.bind
import play.api.inject.guice.{GuiceApplicationBuilder, GuiceableModule}
import play.api.libs.json.Json
import play.api.test.FakeRequest
import play.api.test.Helpers._
import repository._
import repository.model.SessionData
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.domain.{PsaId, PspId}
import uk.gov.hmrc.http.HeaderCarrier
import utils.AuthUtils
import utils.AuthUtils.FakePsaPspEnrolmentAuthAction

import scala.concurrent.Future
import scala.util.Random

class AftDataCacheControllerSpec extends AnyWordSpec with Matchers with MockitoSugar with BeforeAndAfter {


  implicit val hc: HeaderCarrier = HeaderCarrier()

  private val repo = mock[AftBatchedDataCacheRepository]
  private val authConnector: AuthConnector = mock[AuthConnector]
  private val mockMinimalDetailsConnector: MinimalDetailsConnector = mock[MinimalDetailsConnector]
  private val id = "id"
  private val sessionId = "sessionId"
  private val fakeRequest = FakeRequest().withHeaders("X-Session-ID" -> sessionId, "id" -> id)
  private val fakePostRequest = FakeRequest("POST", "/").withHeaders("X-Session-ID" -> sessionId, "id" -> id)
  private val psaId = AuthUtils.psaId
  private val pspId = AuthUtils.pspId
  private val individualDetails: IndividualDetails = IndividualDetails("firstName", None, "lastName")
  private val fakeAuthAction = new FakePsaPspEnrolmentAuthAction
  private def randomString = ByteString(Random.alphanumeric.dropWhile(_.isDigit).take(20).mkString)
  private val modules: Seq[GuiceableModule] = Seq(
    bind[AftBatchedDataCacheRepository].toInstance(repo),
    bind[AftOverviewCacheRepository].toInstance(mock[AftOverviewCacheRepository]),
    bind[FileUploadReferenceCacheRepository].toInstance(mock[FileUploadReferenceCacheRepository]),
    bind[FileUploadOutcomeRepository].toInstance(mock[FileUploadOutcomeRepository]),
    bind[FinancialInfoCacheRepository].toInstance(mock[FinancialInfoCacheRepository]),
    bind[FinancialInfoCreditAccessRepository].toInstance(mock[FinancialInfoCreditAccessRepository]),
    bind[controllers.actions.PsaPspEnrolmentAuthAction].toInstance(fakeAuthAction),
    bind[MinimalDetailsConnector].toInstance(mockMinimalDetailsConnector)
  )

  lazy val app: Application =
    new GuiceApplicationBuilder()
      .configure(
        "auditing.enabled" -> false,
        "metrics.enabled" -> false,
        "metrics.jvm" -> false,
        "run.mode" -> "Test"
      )
      .overrides(modules *)
      .build()

  val controller: AftDataCacheController = app.injector.instanceOf[AftDataCacheController]

  before {
    fakeAuthAction.mockPsaId = Some(PsaId(psaId))
    fakeAuthAction.mockPspId = Some(PspId(pspId))
    reset(repo)
    reset(authConnector)
  }

  "DataCacheController" when {
    "calling get" must {
      "return OK with the data" in {
        when(repo.get(eqTo(id), eqTo(sessionId))(any())).thenReturn(Future.successful(Some(Json.obj("testId" -> "data"))))

        val result = controller.get(fakeRequest)
        status(result) mustEqual OK
        contentAsJson(result) mustEqual Json.obj(fields = "testId" -> "data")
      }

      "return NOT FOUND when the data doesn't exist" in {
        when(repo.get(eqTo(id), eqTo(sessionId))(any())).thenReturn(Future.successful(None))

        val result = controller.get(fakeRequest)
        status(result) mustEqual NOT_FOUND
      }

      "throw an exception when the repository call fails" in {
        when(repo.get(eqTo(id), eqTo(sessionId))(any())).thenReturn(Future.failed(new Exception()))

        val result = controller.get(fakeRequest)
        an[Exception] must be thrownBy status(result)
      }
    }

    "calling save" must {

      "return OK when the data is saved successfully" in {
        when(repo.save(any(), any(), any(), any())(any())).thenReturn(Future.successful((): Unit))

        val result = controller.save(fakePostRequest.withJsonBody(Json.obj("value" -> "data")))
        status(result) mustEqual CREATED
      }

      "return BAD REQUEST when the request body cannot be parsed" in {
        when(repo.save(any(), any(), any(), any())(any())).thenReturn(Future.successful((): Unit))

        val result = controller.save(fakePostRequest.withRawBody(randomString))
        status(result) mustEqual BAD_REQUEST
      }
    }

    "calling remove" must {
      "return OK when the data is removed successfully" in {
        when(repo.remove(eqTo(id), eqTo(sessionId))(any())).thenReturn(Future.successful((): Unit))

        val result = controller.remove(fakeRequest)
        status(result) mustEqual OK
      }
    }

    "calling getSessionData" must {
      "return OK with locked by user name" in {

        val sd = SessionData("id", Some(LockDetail("test name", psaId)), 1, "", areSubmittedVersionsAvailable = false)

        when(repo.getSessionData(any(), any())(any())).thenReturn(Future.successful(Some(sd)))

        val result = controller.getSessionData(fakeRequest)
        status(result) mustEqual OK
        contentAsJson(result) mustEqual Json.toJson(sd)
      }

      "return Not Found when it is not locked" in {
        when(repo.getSessionData(any(), any())(any())).thenReturn(Future.successful(None))

        val result = controller.getSessionData(fakeRequest)
        status(result) mustEqual NOT_FOUND
      }
    }
  }

  "calling setSessionData" must {
    "return OK when the data is saved successfully for a PSA ID" in {
      when(mockMinimalDetailsConnector.getMinimalDetails(any(), any()))
        .thenReturn(Future.successful(MinimalDetails(None, Some(individualDetails))))

      fakeAuthAction.mockPspId = None
      val accessMode = "compile"
      val version = 1

      when(repo.setSessionData(
        ArgumentMatchers.eq(id),
        ArgumentMatchers.eq(Some(LockDetail(individualDetails.fullName, psaId))), any(), any(),
        ArgumentMatchers.eq(version),
        ArgumentMatchers.eq(accessMode),
        ArgumentMatchers.eq(true)
      )(any())).thenReturn(Future.successful((): Unit))

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
      when(mockMinimalDetailsConnector.getMinimalDetails(any(), any()))
        .thenReturn(Future.successful(MinimalDetails(None, Some(individualDetails))))

      fakeAuthAction.mockPsaId = None
      val accessMode = "compile"
      val version = 1

      when(repo.setSessionData(
        ArgumentMatchers.eq(id),
        ArgumentMatchers.eq(Some(LockDetail(individualDetails.fullName, pspId))), any(), any(),
        ArgumentMatchers.eq(version),
        ArgumentMatchers.eq(accessMode),
        ArgumentMatchers.eq(true)
      )(any())).thenReturn(Future.successful((): Unit))


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
      when(mockMinimalDetailsConnector.getMinimalDetails(any(), any()))
        .thenReturn(Future.successful(MinimalDetails(None, Some(individualDetails))))

      val accessMode = "compile"
      val version = 1

      when(repo.setSessionData(ArgumentMatchers.eq(id),
        ArgumentMatchers.eq(Some(LockDetail("", psaId))), any(), any(),
        ArgumentMatchers.eq(version), ArgumentMatchers.eq(accessMode), any())(any())).thenReturn(Future.successful((): Unit))
      val result = controller.setSessionData(true)(fakePostRequest
        .withJsonBody(Json.obj("value" -> "data"))
      )
      status(result) mustEqual BAD_REQUEST
    }

    "return BAD REQUEST when the request body cannot be parsed" in {

      when(repo.setSessionData(any(), any(), any(), any(), any(), any(), any())(any())).thenReturn(Future.successful((): Unit))

      val result = controller
        .setSessionData(true)(fakePostRequest.withRawBody(randomString))
      status(result) mustEqual BAD_REQUEST
    }
  }

  "calling lockedBy" must {
    "return OK when the data is retrieved" in {
      val lockedByUser = LockDetail("bob", psaId)

      when(repo.lockedBy(any(), any())(any())).thenReturn(Future.successful(Some(lockedByUser)))

      val result = controller.lockedBy()(fakeRequest)
      status(result) mustEqual OK
      contentAsString(result) `mustBe` Json.toJson(lockedByUser).toString
    }

    "return NOT FOUND when not locked" in {

      when(repo.lockedBy(any(), any())(any())).thenReturn(Future.successful(None))

      val result = controller.lockedBy()(fakeRequest)
      status(result) mustEqual NOT_FOUND
    }
  }
}

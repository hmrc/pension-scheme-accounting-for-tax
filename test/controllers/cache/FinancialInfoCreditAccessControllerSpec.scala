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

import models.enumeration.CreditAccessType.{AccessedByLoggedInPsaOrPsp, AccessedByOtherPsa, AccessedByOtherPsp}
import org.mockito.ArgumentMatchers._
import org.mockito.Mockito._
import org.mockito.{ArgumentCaptor, ArgumentMatchers}
import org.scalatest.BeforeAndAfterEach
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.mockito.MockitoSugar
import play.api.Application
import play.api.inject.bind
import play.api.inject.guice.{GuiceApplicationBuilder, GuiceableModule}
import play.api.libs.json.{JsString, JsValue, Json}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import repository._
import utils.AuthUtils
import utils.AuthUtils.{FakePsaEnrolmentAuthAction, FakePsaPspEnrolmentAuthAction, FakePsaPspSchemeAuthAction, FakePsaSchemeAuthAction}

import scala.concurrent.Future

class FinancialInfoCreditAccessControllerSpec extends AnyWordSpec with Matchers with MockitoSugar with BeforeAndAfterEach {

  private val repo = mock[FinancialInfoCreditAccessRepository]
  private val fakeRequest = FakeRequest()
  private val psaId = AuthUtils.psaId
  private val pspId = AuthUtils.pspId
  private val srn = AuthUtils.srn

  private val modules: Seq[GuiceableModule] = Seq(
    bind[FinancialInfoCreditAccessRepository].toInstance(repo),
    bind[FileUploadOutcomeRepository].toInstance(mock[FileUploadOutcomeRepository]),
    bind[AftBatchedDataCacheRepository].toInstance(mock[AftBatchedDataCacheRepository]),
    bind[AftOverviewCacheRepository].toInstance(mock[AftOverviewCacheRepository]),
    bind[FileUploadReferenceCacheRepository].toInstance(mock[FileUploadReferenceCacheRepository]),
    bind[FinancialInfoCacheRepository].toInstance(mock[FinancialInfoCacheRepository]),
    bind[controllers.actions.PsaEnrolmentAuthAction].toInstance(new FakePsaEnrolmentAuthAction),
    bind[controllers.actions.PsaPspEnrolmentAuthAction].toInstance(new FakePsaPspEnrolmentAuthAction),
    bind[controllers.actions.PsaSchemeAuthAction].toInstance(new FakePsaSchemeAuthAction),
    bind[controllers.actions.PsaPspSchemeAuthAction].toInstance(FakePsaPspSchemeAuthAction(mockPsaId = None))
  )

  val app: Application = new GuiceApplicationBuilder()
    .configure(conf = "auditing.enabled" -> false, "metrics.enabled" -> false, "metrics.jvm" -> false, "run.mode" -> "Test")
    .overrides(modules: _*).build()
  val controller: FinancialInfoCreditAccessController = app.injector.instanceOf[FinancialInfoCreditAccessController]

  override def beforeEach(): Unit = {
    reset(repo)
    super.beforeEach()
  }

  "FinancialInfoCreditAccessController" when {
    "calling getForSchemePsa" must {
      "when accessed by logged in PSA return OK with the accessedByCurrentPsa" in {
        when(repo.get(any())(any())) thenReturn Future.successful(Some(Json.obj("psaId" -> psaId)))

        val result = controller.getForSchemePsa(psaId, srn)(fakeRequest)
        status(result) mustEqual OK
        contentAsJson(result) mustEqual JsString(AccessedByLoggedInPsaOrPsp.toString)
      }

      "when accessed by different PSA return OK with the accessedByOtherPsa" in {
        when(repo.get(any())(any())) thenReturn Future.successful(Some(Json.obj("psaId" -> pspId)))

        val result = controller.getForSchemePsa(psaId, srn)(fakeRequest)
        status(result) mustEqual OK
        contentAsJson(result) mustEqual JsString(AccessedByOtherPsa.toString)
      }

      "when not accessed by any PSA or PSP return NOT_FOUND and update repo with psaId and srn" in {
        when(repo.get(any())(any())).thenReturn(Future.successful(None))
        when(repo.save(any(), any())(any())).thenReturn(Future.successful((): Unit))

        val result = controller.getForSchemePsa(psaId, srn)(fakeRequest)
        status(result) mustEqual NOT_FOUND
        val jsValueCaptor: ArgumentCaptor[JsValue] = ArgumentCaptor.forClass(classOf[JsValue])
        verify(repo, times(1)).save(ArgumentMatchers.eq(srn), jsValueCaptor.capture())(any())
        jsValueCaptor.getValue mustBe Json.obj(
          "psaId" -> psaId
        )
      }
    }

    "calling getForSchemePsp" must {
      "when accessed by logged in PSA return OK with the accessedByCurrentPsp" in {
        when(repo.get(any())(any())) thenReturn Future.successful(Some(Json.obj("pspId" -> pspId)))

        val result = controller.getForSchemePsp(pspId, srn)(fakeRequest)
        status(result) mustEqual OK
        contentAsJson(result) mustEqual JsString(AccessedByLoggedInPsaOrPsp.toString)
      }

      "when accessed by different PSA return OK with the accessedByOtherPsp" in {
        when(repo.get(any())(any())) thenReturn Future.successful(Some(Json.obj("pspId" -> psaId)))

        val result = controller.getForSchemePsp(pspId, srn)(fakeRequest)
        status(result) mustEqual OK
        contentAsJson(result) mustEqual JsString(AccessedByOtherPsp.toString)
      }

      "when not accessed by any PSA or PSP return NOT_FOUND and update repo with pspId and srn" in {
        when(repo.get(any())(any())) thenReturn Future.successful(None)
        when(repo.save(any(), any())(any())).thenReturn(Future.successful((): Unit))

        val result = controller.getForSchemePsp(pspId, srn)(fakeRequest)
        status(result) mustEqual NOT_FOUND
        val jsValueCaptor: ArgumentCaptor[JsValue] = ArgumentCaptor.forClass(classOf[JsValue])
        verify(repo, times(1)).save(ArgumentMatchers.eq(srn), jsValueCaptor.capture())(any())
        jsValueCaptor.getValue mustBe Json.obj(
          "pspId" -> pspId
        )
      }
    }

    "calling getForPsa" must {
      "when accessed by logged in PSA return OK with the accessedByCurrentPsa" in {
        when(repo.get(any())(any())) thenReturn Future.successful(Some(Json.obj("psaId" -> psaId)))

        val result = controller.getForPsa(psaId)(fakeRequest)
        status(result) mustEqual OK
        contentAsJson(result) mustEqual JsString(AccessedByLoggedInPsaOrPsp.toString)
      }

      "when not accessed by any PSA  return NOT_FOUND and update repo with psaId" in {
        when(repo.get(any())(any())) thenReturn Future.successful(None)
        when(repo.save(any(), any())(any())).thenReturn(Future.successful((): Unit))

        val result = controller.getForPsa(psaId)(fakeRequest)
        status(result) mustEqual NOT_FOUND
        val jsValueCaptor: ArgumentCaptor[JsValue] = ArgumentCaptor.forClass(classOf[JsValue])
        verify(repo, times(1)).save(ArgumentMatchers.eq(psaId), jsValueCaptor.capture())(any())
        jsValueCaptor.getValue mustBe Json.obj(
          "psaId" -> psaId
        )
      }
    }
  }
}

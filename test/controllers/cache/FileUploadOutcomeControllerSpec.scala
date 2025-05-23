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

import org.apache.pekko.util.ByteString
import org.mockito.ArgumentMatchers.{eq => eqTo, _}
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterEach
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AsyncWordSpec
import org.scalatestplus.mockito.MockitoSugar
import play.api.Application
import play.api.inject.bind
import play.api.inject.guice.{GuiceApplicationBuilder, GuiceableModule}
import play.api.libs.json.Json
import play.api.test.FakeRequest
import play.api.test.Helpers._
import repository._
import utils.AuthUtils
import utils.AuthUtils.FakePsaPspEnrolmentAuthAction

import scala.concurrent.Future
import scala.util.Random

class FileUploadOutcomeControllerSpec extends AsyncWordSpec with Matchers with MockitoSugar with BeforeAndAfterEach { // scalastyle:off magic.number

  private val repo = mock[FileUploadOutcomeRepository]
  private val id = AuthUtils.externalId
  private val fakePostRequest = FakeRequest("POST", "/")
  private val fakeRequest = FakeRequest()
  private def randomString = ByteString(Random.alphanumeric.dropWhile(_.isDigit).take(20).mkString)

  def modules: Seq[GuiceableModule] = {
    Seq(
      bind[FileUploadOutcomeRepository].toInstance(repo),
      bind[AftBatchedDataCacheRepository].toInstance(mock[AftBatchedDataCacheRepository]),
      bind[AftOverviewCacheRepository].toInstance(mock[AftOverviewCacheRepository]),
      bind[FileUploadReferenceCacheRepository].toInstance(mock[FileUploadReferenceCacheRepository]),
      bind[FinancialInfoCacheRepository].toInstance(mock[FinancialInfoCacheRepository]),
      bind[FinancialInfoCreditAccessRepository].toInstance(mock[FinancialInfoCreditAccessRepository]),
      bind[controllers.actions.PsaPspEnrolmentAuthAction].toInstance(new FakePsaPspEnrolmentAuthAction)
    )
  }

  private val application: Application = new GuiceApplicationBuilder()
    .configure(conf = "auditing.enabled" -> false, "metrics.enabled" -> false, "metrics.jvm" -> false).
    overrides(modules *).build()

  val controller: FileUploadOutcomeController = application.injector.instanceOf[FileUploadOutcomeController]

  override def beforeEach(): Unit = {
    reset(repo)
    super.beforeEach()
  }

  "FileUploadOutcomeController" when {
    "calling get" must {
      "return OK with the data" in {
        when(repo.get(eqTo(id))(any())).thenReturn(Future.successful(Some(Json.obj("testId" -> "data"))))

        val result = controller.get(fakeRequest)
        status(result) mustEqual OK
        contentAsJson(result) mustEqual Json.obj(fields = "testId" -> "data")
      }

      "return NOT FOUND when the data doesn't exist" in {
        when(repo.get(eqTo(id))(any())).thenReturn(Future.successful(None))

        val result = controller.get(fakeRequest)
        status(result) mustEqual NOT_FOUND
      }

      "throw an exception when the repository call fails" in {
        when(repo.get(eqTo(id))(any())).thenReturn(Future.failed(new Exception()))
        val result = controller.get(fakeRequest)
        an[Exception] must be thrownBy status(result)
      }
    }

    "calling save" must {

      "return OK when the data is saved successfully" in {
        when(repo.save(any(), any())(any())).thenReturn(Future.successful((): Unit))

        val result = controller.post(fakePostRequest.withJsonBody(Json.obj("value" -> "data")))
        status(result) mustEqual CREATED
      }

      "return BAD REQUEST when the request body cannot be parsed" in {
        when(repo.save(any(), any())(any())).thenReturn(Future.successful((): Unit))

        val result = controller.post(fakePostRequest.withRawBody(randomString))
        status(result) mustEqual BAD_REQUEST
      }
    }

    "calling delete" must {
      "return OK when the data is removed successfully" in {
        when(repo.remove(eqTo(id))(any())).thenReturn(Future.successful(true))

        val result = controller.delete(fakeRequest)
        status(result) mustEqual OK
      }
    }
  }
}

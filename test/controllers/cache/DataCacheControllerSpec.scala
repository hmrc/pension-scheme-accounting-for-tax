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

import akka.stream.Materializer
import akka.util.ByteString
import org.apache.commons.lang3.RandomUtils
import org.mockito.Matchers.{eq => eqTo, _}
import org.mockito.Mockito.when
import org.scalatest.{MustMatchers, WordSpec}
import org.scalatestplus.mockito.MockitoSugar
import play.api.inject.bind
import play.api.inject.guice.{GuiceApplicationBuilder, GuiceableModule}
import play.api.libs.json.Json
import play.api.test.FakeRequest
import play.api.test.Helpers._
import repository.DataCacheRepository
import uk.gov.hmrc.auth.core.AuthConnector

import scala.concurrent.Future

class DataCacheControllerSpec extends WordSpec with MustMatchers with MockitoSugar {

  import DataCacheController._

  implicit lazy val mat: Materializer = app.materializer
  private val app = new GuiceApplicationBuilder()
    .configure(conf = "auditing.enabled" -> false, "metrics.enabled" -> false, "run.mode" -> "Test").build()
  private val repo = mock[DataCacheRepository]
  private val authConnector: AuthConnector = mock[AuthConnector]

  private val modules: Seq[GuiceableModule] = Seq(
    bind[AuthConnector].toInstance(authConnector),
    bind[DataCacheRepository].toInstance(repo)
  )

  "DataCacheController" when {
    "calling get" must {
      "return OK with the data" in {
        running(_.overrides(modules: _*)) { app =>
          val controller = app.injector.instanceOf[DataCacheController]
          when(repo.get(eqTo("internalId"))(any())) thenReturn Future.successful(Some(Json.obj("testId" -> "data")))
          when(authConnector.authorise[Option[String]](any(), any())(any(), any())) thenReturn Future.successful(Some("internalId"))

          val result = controller.get(FakeRequest())
          status(result) mustEqual OK
          contentAsJson(result) mustEqual Json.obj(fields = "testId" -> "data")
        }
      }

      "return NOT FOUND when the data doesn't exist" in {
        running(_.overrides(modules: _*)) { app =>
          val controller = app.injector.instanceOf[DataCacheController]
          when(repo.get(eqTo("internalId"))(any())) thenReturn Future.successful(None)
          when(authConnector.authorise[Option[String]](any(), any())(any(), any())) thenReturn Future.successful(Some("internalId"))

          val result = controller.get(FakeRequest())
          status(result) mustEqual NOT_FOUND
        }
      }

      "throw an exception when the repository call fails" in {
        running(_.overrides(modules: _*)) { app =>
          val controller = app.injector.instanceOf[DataCacheController]
          when(repo.get(eqTo("internalId"))(any())) thenReturn Future.failed(new Exception())
          when(authConnector.authorise[Option[String]](any(), any())(any(), any())) thenReturn Future.successful(Some("internalId"))

          val result = controller.get(FakeRequest())
          an[Exception] must be thrownBy status(result)
        }
      }

      "throw an exception when the call is not authorised" in {
        running(_.overrides(modules: _*)) { app =>
          val controller = app.injector.instanceOf[DataCacheController]
          when(authConnector.authorise[Option[String]](any(), any())(any(), any())) thenReturn Future.successful(None)

          val result = controller.get(FakeRequest())
          an[InternalIdNotFoundFromAuth] must be thrownBy status(result)
        }
      }
    }

    "calling save" must {

      "return OK when the data is saved successfully" in {
        running(_.overrides(modules: _*)) { app =>
          val controller = app.injector.instanceOf[DataCacheController]
          when(repo.save(any(), any())(any())) thenReturn Future.successful(true)
          when(authConnector.authorise[Option[String]](any(), any())(any(), any())) thenReturn Future.successful(Some("internalId"))

          val result = controller.save(FakeRequest("POST", "/").withJsonBody(Json.obj("value" -> "data")))
          status(result) mustEqual CREATED
        }
      }

      "return BAD REQUEST when the request body cannot be parsed" in {
        running(_.overrides(modules: _*)) { app =>
          val controller = app.injector.instanceOf[DataCacheController]
          when(repo.save(any(), any())(any())) thenReturn Future.successful(true)
          when(authConnector.authorise[Option[String]](any(), any())(any(), any())) thenReturn Future.successful(Some("internalId"))

          val result = controller.save(FakeRequest().withRawBody(ByteString(RandomUtils.nextBytes(512001))))
          status(result) mustEqual BAD_REQUEST
        }
      }

      "throw an exception when the call is not authorised" in {
        running(_.overrides(modules: _*)) { app =>
          val controller = app.injector.instanceOf[DataCacheController]
          when(authConnector.authorise[Option[String]](any(), any())(any(), any())) thenReturn Future.successful(None)

          val result = controller.save(FakeRequest("POST", "/").withJsonBody(Json.obj(fields = "value" -> "data")))
          an[InternalIdNotFoundFromAuth] must be thrownBy status(result)
        }
      }
    }

    "calling remove" must {
      "return OK when the data is removed successfully" in {
        running(_.overrides(modules: _*)) { app =>
          val controller = app.injector.instanceOf[DataCacheController]
          when(repo.remove(eqTo("internalId"))(any())) thenReturn Future.successful(true)
          when(authConnector.authorise[Option[String]](any(), any())(any(), any())) thenReturn Future.successful(Some("internalId"))

          val result = controller.remove(FakeRequest())
          status(result) mustEqual OK
        }
      }

      "throw an exception when the call is not authorised" in {
        running(_.overrides(modules: _*)) { app =>
          val controller = app.injector.instanceOf[DataCacheController]
          when(authConnector.authorise[Option[String]](any(), any())(any(), any())) thenReturn Future.successful(None)

          val result = controller.remove(FakeRequest())
          an[InternalIdNotFoundFromAuth] must be thrownBy status(result)
        }
      }
    }
  }
}

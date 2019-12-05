/*
 * Copyright 2019 HM Revenue & Customs
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

package controllers

import akka.stream.Materializer
import config.AppConfig
import connectors.DesConnector
import org.mockito.Matchers.any
import org.mockito.Mockito._
import org.scalatest.{AsyncWordSpec, BeforeAndAfter, MustMatchers}
import org.scalatestplus.mockito.MockitoSugar
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.Json
import play.api.test.FakeRequest
import play.api.test.Helpers.{stubControllerComponents, _}
import transformations.userAnswersToETMP.{AFTReturnTransformer, ChargeATransformer, ChargeFTransformer}
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse, Upstream5xxResponse}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class AFTControllerSpec extends AsyncWordSpec with MustMatchers with MockitoSugar with BeforeAndAfter {

  "fileReturn" must {
    "return OK when valid response from DES" in {
      running(app) {
        when(mockDesConnector.fileAFTReturn(any(), any())(any(), any()))
          .thenReturn(Future.successful(HttpResponse(OK, Some(userAnswersRequestJson))))

        val result = controller.fileReturn()(fakeRequest.withJsonBody(userAnswersRequestJson).withHeaders(newHeaders = "pstr" -> pstr))
        status(result) mustBe OK
      }
    }

    "throw Upstream5XXResponse on Internal Server Error from DES" in {
      running(app) {
        when(mockDesConnector.fileAFTReturn(any(), any())(any(), any()))
          .thenReturn(Future.failed(Upstream5xxResponse(message = "Internal Server Error", INTERNAL_SERVER_ERROR, INTERNAL_SERVER_ERROR)))

        recoverToExceptionIf[Upstream5xxResponse] {
          controller.fileReturn()(fakeRequest.withJsonBody(userAnswersRequestJson).withHeaders(newHeaders = "pstr" -> pstr))
        } map {
          _.upstreamResponseCode mustBe INTERNAL_SERVER_ERROR
        }
      }
    }
  }

  implicit val hc: HeaderCarrier = HeaderCarrier()
  protected lazy val app: Application = new GuiceApplicationBuilder()
    .configure(conf = "auditing.enabled" -> false, "metrics.enabled" -> false).build()
  implicit lazy val mat: Materializer = app.materializer
  private val fakeRequest = FakeRequest("GET", "/")

  private def appConfig: AppConfig = app.injector.instanceOf[AppConfig]

  private val mockDesConnector = mock[DesConnector]
  val transformer = new AFTReturnTransformer(new ChargeATransformer, new ChargeFTransformer)

  private val controller = new AFTController(appConfig, stubControllerComponents(), mockDesConnector, transformer)
  private val json =
    """{
      |  "aftStatus": "Compiled",
      |  "quarter": {
      |       "startDate": "2019-01-01",
      |       "endDate": "2019-03-31"
      |  },
      |  "chargeFDetails": {
      |    "amountTaxDue": 200.02,
      |    "deRegistrationDate": "1980-02-29"
      |  }
      |}""".stripMargin
  private val userAnswersRequestJson = Json.parse(json)
  private val pstr = "12345678RD"

  before {
    reset(mockDesConnector)
  }
}

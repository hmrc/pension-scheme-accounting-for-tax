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
import org.scalatest.{BeforeAndAfter, Matchers, WordSpec}
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.http.Status
import play.api.libs.json.Json
import play.api.mvc.Results
import play.api.test.Helpers._
import play.api.test.{FakeRequest, Helpers}
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class CompileControllerSpec extends WordSpec with Matchers with GuiceOneAppPerSuite with MockitoSugar with Results with Status with BeforeAndAfter {
  implicit val hc: HeaderCarrier = HeaderCarrier()

  implicit lazy val mat: Materializer = app.materializer

  private val injector = app.injector
  private val fakeRequest = FakeRequest("GET", "/")

  private def appConfig: AppConfig = injector.instanceOf[AppConfig]

  private val mockDesConnector = mock[DesConnector]

  private val controller = new CompileController(appConfig, Helpers.stubControllerComponents(), mockDesConnector)

  private val pstr = "12345678RD"

  private val json = Json.obj("aaa"->"bbb")

  private def upstreamResponseMessage(actionName: String, status: Int, responseBody: String): String =
    s"$actionName' returned $status. Response body: '$responseBody'"

  before {
    reset(mockDesConnector)
  }

  "Compile" should {
    "return OK when valid response from DES" in {
      running(app) {
        when(mockDesConnector.compileFileReturn(any(), any())(any(), any()))
          .thenReturn(Future.successful(HttpResponse(OK, Some(json))))

        val result = controller.fileReturn()(fakeRequest.withJsonBody(json).withHeaders("pstr" -> pstr))
        status(result) shouldBe OK
      }
    }
  }
}

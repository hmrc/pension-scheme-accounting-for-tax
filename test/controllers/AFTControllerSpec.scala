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

package controllers

import akka.stream.Materializer
import config.AppConfig
import connectors.DesConnector
import org.mockito.Matchers
import org.mockito.Matchers.any
import org.mockito.Mockito._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{AsyncWordSpec, BeforeAndAfter, MustMatchers}
import org.scalatestplus.mockito.MockitoSugar
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.{JsValue, Json}
import play.api.mvc.AnyContentAsEmpty
import play.api.test.FakeRequest
import play.api.test.Helpers.{stubControllerComponents, _}
import transformations.userAnswersToETMP.{AFTReturnTransformer, ChargeATransformer, ChargeBTransformer, ChargeDTransformer, ChargeETransformer, ChargeFTransformer}
import uk.gov.hmrc.http.{BadRequestException, HeaderCarrier, HttpResponse, Upstream4xxResponse, Upstream5xxResponse}
import utils.JsonFileReader

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class AFTControllerSpec extends AsyncWordSpec with MustMatchers with MockitoSugar with BeforeAndAfter with JsonFileReader {

  implicit val hc: HeaderCarrier = HeaderCarrier()
  protected lazy val app: Application = new GuiceApplicationBuilder()
    .configure(conf = "auditing.enabled" -> false, "metrics.enabled" -> false).build()
  implicit lazy val mat: Materializer = app.materializer
  private val fakeRequest = FakeRequest("GET", "/")

  private def appConfig: AppConfig = app.injector.instanceOf[AppConfig]

  private val mockDesConnector = mock[DesConnector]
  val transformer = new AFTReturnTransformer(new ChargeATransformer, new ChargeBTransformer, new ChargeETransformer, new ChargeDTransformer, new ChargeFTransformer)

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
  private val startDt = "2020-01-01"
  private val aftVer = "99"
  private val fbNumber = "20"
  private val userAnswersResponse: JsValue = readJsonFromFile("/validGetAftDetailsResponse.json")
  private val fakeRequestForGetDetails = FakeRequest("GET", "/").withHeaders(("pstr", pstr))

  before {
    reset(mockDesConnector)
  }

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

  "getAftDetails" must {

    "return OK when the details are returned based on pstr only" in {

      def fakeRequest: FakeRequest[AnyContentAsEmpty.type] = FakeRequest("GET", "/").withHeaders(("pstr", pstr))
      val queryParams = pstr
      when(mockDesConnector.getAftDetails(Matchers.eq(queryParams))(any(), any())).thenReturn(
        Future.successful(Right(userAnswersResponse)))

      val result = controller.getDetails()(fakeRequest)

      status(result) mustBe OK
      contentAsJson(result) mustBe userAnswersResponse
    }

    "return OK when the details are returned based on pstr, start date and AFT version" in {

      def fakeRequest: FakeRequest[AnyContentAsEmpty.type] = FakeRequest("GET", "/")
        .withHeaders(("pstr", pstr), ("startDate", startDt), ("aftVersion", aftVer))
      val queryParams = s"$pstr?startDate=$startDt&aftVersion=$aftVer"
      when(mockDesConnector.getAftDetails(Matchers.eq(queryParams))(any(), any())).thenReturn(
        Future.successful(Right(userAnswersResponse)))

      val result = controller.getDetails()(fakeRequest)

      status(result) mustBe OK
      contentAsJson(result) mustBe userAnswersResponse
    }

    "return OK when the details are returned based on pstr and start date" in {

      def fakeRequest: FakeRequest[AnyContentAsEmpty.type] = FakeRequest("GET", "/")
        .withHeaders(("pstr", pstr), ("startDate", startDt))
      val queryParams = s"$pstr?startDate=$startDt"
      when(mockDesConnector.getAftDetails(Matchers.eq(queryParams))(any(), any())).thenReturn(
        Future.successful(Right(userAnswersResponse)))

      val result = controller.getDetails()(fakeRequest)

      status(result) mustBe OK
      contentAsJson(result) mustBe userAnswersResponse
    }

    "return OK when the details are returned based on pstr and form bundle number" in {

      def fakeRequest: FakeRequest[AnyContentAsEmpty.type] = FakeRequest("GET", "/")
        .withHeaders(("pstr", pstr), ("fbNumber", fbNumber))
      val queryParams = s"$pstr?fbNumber=$fbNumber"
      when(mockDesConnector.getAftDetails(Matchers.eq(queryParams))(any(), any())).thenReturn(
        Future.successful(Right(userAnswersResponse)))

      val result = controller.getDetails()(fakeRequest)

      status(result) mustBe OK
      contentAsJson(result) mustBe userAnswersResponse
    }

    "throw BadRequestException when PSTR is not present in the header" in {
      val result = controller.getDetails()(FakeRequest("GET", "/")
        .withHeaders(("startDate", startDt), ("aftVersion", aftVer)))
      ScalaFutures.whenReady(result.failed) { e =>
        e mustBe a[BadRequestException]
        e.getMessage mustBe "Bad Request with missing PSTR"
        //    verify(mockDesConnector, never()).getAftDetails(Matchers.any())(any(), any())
      }
    }


    "throw BadRequestException when bad request with INVALID_PSTR returned from Des" in {
      when(mockDesConnector.getAftDetails(Matchers.eq(pstr))(any(), any())).thenReturn(
        Future.failed(new BadRequestException(errorResponse("INVALID_PSTR"))))

      val result = controller.getDetails()(fakeRequestForGetDetails)
      ScalaFutures.whenReady(result.failed) { e =>
        e mustBe a[BadRequestException]
        e.getMessage mustBe errorResponse("INVALID_PSTR")
      }
    }

    "throw BadRequestException when bad request with INVALID_FORMBUNDLE_NUMBER returned from Des" in {

      when(mockDesConnector.getAftDetails(Matchers.eq(pstr))(any(), any())).thenReturn(
        Future.failed(new BadRequestException(errorResponse("INVALID_FORMBUNDLE_NUMBER"))))

      val result = controller.getDetails()(fakeRequestForGetDetails)
      ScalaFutures.whenReady(result.failed) { e =>
        e mustBe a[BadRequestException]
        e.getMessage mustBe errorResponse("INVALID_FORMBUNDLE_NUMBER")
      }
    }

    "throw BadRequestException when bad request with INVALID_START_DATE returned from Des" in {

      when(mockDesConnector.getAftDetails(Matchers.eq(pstr))(any(), any())).thenReturn(
        Future.failed(new BadRequestException(errorResponse("INVALID_START_DATE"))))

      val result = controller.getDetails()(fakeRequestForGetDetails)
      ScalaFutures.whenReady(result.failed) { e =>
        e mustBe a[BadRequestException]
        e.getMessage mustBe errorResponse("INVALID_START_DATE")
      }
    }

    "throw BadRequestException when bad request with INVALID_AFT_VERSION returned from Des" in {

      when(mockDesConnector.getAftDetails(Matchers.eq(pstr))(any(), any())).thenReturn(
        Future.failed(new BadRequestException(errorResponse("INVALID_AFT_VERSION"))))

      val result = controller.getDetails()(fakeRequestForGetDetails)
      ScalaFutures.whenReady(result.failed) { e =>
        e mustBe a[BadRequestException]
        e.getMessage mustBe errorResponse("INVALID_AFT_VERSION")
      }
    }

    "throw BadRequestException when bad request with INVALID_CORRELATIONID returned from Des" in {

      when(mockDesConnector.getAftDetails(Matchers.eq(pstr))(any(), any())).thenReturn(
        Future.failed(new BadRequestException(errorResponse("INVALID_CORRELATIONID"))))

      val result = controller.getDetails()(fakeRequestForGetDetails)
      ScalaFutures.whenReady(result.failed) { e =>
        e mustBe a[BadRequestException]
        e.getMessage mustBe errorResponse("INVALID_CORRELATIONID")
      }

    }

    "throw Upstream4xxResponse when UpStream4XXResponse returned from Des" in {

      when(mockDesConnector.getAftDetails(Matchers.eq(pstr))(any(), any())).thenReturn(
        Future.failed(Upstream4xxResponse(errorResponse("NOT_FOUND"), NOT_FOUND, NOT_FOUND)))

      val result = controller.getDetails()(fakeRequestForGetDetails)
      ScalaFutures.whenReady(result.failed) { e =>
        e mustBe a[Upstream4xxResponse]
        e.getMessage mustBe errorResponse("NOT_FOUND")
      }
    }

    "throw Upstream5xxResponse when UpStream5XXResponse with SERVICE_UNAVAILABLE returned from Des" in {

      when(mockDesConnector.getAftDetails(Matchers.eq(pstr))(any(), any())).thenReturn(
        Future.failed(Upstream5xxResponse(errorResponse("NOT_FOUND"), SERVICE_UNAVAILABLE, SERVICE_UNAVAILABLE)))

      val result = controller.getDetails()(fakeRequestForGetDetails)
      ScalaFutures.whenReady(result.failed) { e =>
        e mustBe a[Upstream5xxResponse]
        e.getMessage mustBe errorResponse("NOT_FOUND")
      }
    }

    "throw Upstream5xxResponse when UpStream5XXResponse with INTERNAL_SERVER_ERROR returned from Des" in {

      when(mockDesConnector.getAftDetails(Matchers.eq(pstr))(any(), any())).thenReturn(
        Future.failed(Upstream5xxResponse(errorResponse("NOT_FOUND"), INTERNAL_SERVER_ERROR, INTERNAL_SERVER_ERROR)))

      val result = controller.getDetails()(fakeRequestForGetDetails)
      ScalaFutures.whenReady(result.failed) { e =>
        e mustBe a[Upstream5xxResponse]
        e.getMessage mustBe errorResponse("NOT_FOUND")
      }
    }

    "throw generic exception when any other exception returned from Des" in {

      when(mockDesConnector.getAftDetails(Matchers.eq(pstr))(any(), any())).thenReturn(
        Future.failed(new Exception("Generic Exception")))

      val result = controller.getDetails()(fakeRequestForGetDetails)
      ScalaFutures.whenReady(result.failed) { e =>
        e mustBe a[Exception]
        e.getMessage mustBe "Generic Exception"
      }
    }
  }

  def errorResponse(code: String): String = {
    Json.stringify(
      Json.obj(
        "code" -> code,
        "reason" -> s"Reason for $code"
      )
    )
  }
}

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
import play.api.libs.json.{JsNull, JsValue, Json}
import play.api.test.FakeRequest
import play.api.test.Helpers.{stubControllerComponents, _}
import transformations.ETMPToUserAnswers.{AFTDetailsTransformer, ChargeFTransformer => GetChargeFTransformer}
import transformations.userAnswersToETMP._
import uk.gov.hmrc.http._
import utils.JsonFileReader

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class AFTControllerSpec extends AsyncWordSpec with MustMatchers with MockitoSugar with BeforeAndAfter with JsonFileReader {

  import AFTControllerSpec._

  implicit val hc: HeaderCarrier = HeaderCarrier()
  implicit lazy val mat: Materializer = app.materializer
  protected lazy val app: Application = new GuiceApplicationBuilder()
    .configure(conf = "auditing.enabled" -> false, "metrics.enabled" -> false).build()

  private val fakeRequest = FakeRequest("GET", "/")

  private def appConfig: AppConfig = app.injector.instanceOf[AppConfig]

  private val mockDesConnector = mock[DesConnector]

  private val aftReturnTransformer = new AFTReturnTransformer(new ChargeATransformer, new ChargeBTransformer, new ChargeETransformer,
    new ChargeDTransformer, new ChargeFTransformer, new ChargeGTransformer)
  private val getDetailsTransformer = new AFTDetailsTransformer(new GetChargeFTransformer)

  private val controller = new AFTController(appConfig, stubControllerComponents(),
    mockDesConnector, aftReturnTransformer, getDetailsTransformer)

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

    "return OK when the details are returned based on pstr, start date and AFT version" in {

      when(mockDesConnector.getAftDetails(Matchers.eq(queryParams))(any(), any())).thenReturn(
        Future.successful(etmpResponse))

      val result = controller.getDetails()(fakeRequestForGetDetails)

      status(result) mustBe OK
      contentAsJson(result) mustBe transformedUserAnswersJson
    }

    "throw BadRequestException when PSTR is not present in the header" in {
      val result = controller.getDetails()(FakeRequest("GET", "/")
        .withHeaders(("startDate", startDt), ("aftVersion", aftVer)))
      ScalaFutures.whenReady(result.failed) { e =>
        e mustBe a[BadRequestException]
        e.getMessage mustBe "Bad Request with missing PSTR"
      }
    }


    "throw BadRequestException when bad request with INVALID_PSTR returned from Des" in {
      when(mockDesConnector.getAftDetails(Matchers.eq(queryParams))(any(), any())).thenReturn(
        Future.failed(new BadRequestException(errorResponse("INVALID_PSTR"))))

      val result = controller.getDetails()(fakeRequestForGetDetails)
      ScalaFutures.whenReady(result.failed) { e =>
        e mustBe a[BadRequestException]
        e.getMessage mustBe errorResponse("INVALID_PSTR")
      }
    }

    "throw BadRequestException when bad request with INVALID_FORMBUNDLE_NUMBER returned from Des" in {

      when(mockDesConnector.getAftDetails(Matchers.eq(queryParams))(any(), any())).thenReturn(
        Future.failed(new BadRequestException(errorResponse("INVALID_FORMBUNDLE_NUMBER"))))

      val result = controller.getDetails()(fakeRequestForGetDetails)
      ScalaFutures.whenReady(result.failed) { e =>
        e mustBe a[BadRequestException]
        e.getMessage mustBe errorResponse("INVALID_FORMBUNDLE_NUMBER")
      }
    }

    "throw BadRequestException when bad request with INVALID_START_DATE returned from Des" in {

      when(mockDesConnector.getAftDetails(Matchers.eq(queryParams))(any(), any())).thenReturn(
        Future.failed(new BadRequestException(errorResponse("INVALID_START_DATE"))))

      val result = controller.getDetails()(fakeRequestForGetDetails)
      ScalaFutures.whenReady(result.failed) { e =>
        e mustBe a[BadRequestException]
        e.getMessage mustBe errorResponse("INVALID_START_DATE")
      }
    }

    "throw BadRequestException when bad request with INVALID_AFT_VERSION returned from Des" in {

      when(mockDesConnector.getAftDetails(Matchers.eq(queryParams))(any(), any())).thenReturn(
        Future.failed(new BadRequestException(errorResponse("INVALID_AFT_VERSION"))))

      val result = controller.getDetails()(fakeRequestForGetDetails)
      ScalaFutures.whenReady(result.failed) { e =>
        e mustBe a[BadRequestException]
        e.getMessage mustBe errorResponse("INVALID_AFT_VERSION")
      }
    }

    "throw BadRequestException when bad request with INVALID_CORRELATIONID returned from Des" in {

      when(mockDesConnector.getAftDetails(Matchers.eq(queryParams))(any(), any())).thenReturn(
        Future.failed(new BadRequestException(errorResponse("INVALID_CORRELATIONID"))))

      val result = controller.getDetails()(fakeRequestForGetDetails)
      ScalaFutures.whenReady(result.failed) { e =>
        e mustBe a[BadRequestException]
        e.getMessage mustBe errorResponse("INVALID_CORRELATIONID")
      }

    }

    "throw Upstream4xxResponse when UpStream4XXResponse returned from Des" in {

      when(mockDesConnector.getAftDetails(Matchers.eq(queryParams))(any(), any())).thenReturn(
        Future.failed(Upstream4xxResponse(errorResponse("NOT_FOUND"), NOT_FOUND, NOT_FOUND)))

      val result = controller.getDetails()(fakeRequestForGetDetails)
      ScalaFutures.whenReady(result.failed) { e =>
        e mustBe a[Upstream4xxResponse]
        e.getMessage mustBe errorResponse("NOT_FOUND")
      }
    }

    "throw Upstream5xxResponse when UpStream5XXResponse with SERVICE_UNAVAILABLE returned from Des" in {

      when(mockDesConnector.getAftDetails(Matchers.eq(queryParams))(any(), any())).thenReturn(
        Future.failed(Upstream5xxResponse(errorResponse("NOT_FOUND"), SERVICE_UNAVAILABLE, SERVICE_UNAVAILABLE)))

      val result = controller.getDetails()(fakeRequestForGetDetails)
      ScalaFutures.whenReady(result.failed) { e =>
        e mustBe a[Upstream5xxResponse]
        e.getMessage mustBe errorResponse("NOT_FOUND")
      }
    }

    "throw Upstream5xxResponse when UpStream5XXResponse with INTERNAL_SERVER_ERROR returned from Des" in {

      when(mockDesConnector.getAftDetails(Matchers.eq(queryParams))(any(), any())).thenReturn(
        Future.failed(Upstream5xxResponse(errorResponse("NOT_FOUND"), INTERNAL_SERVER_ERROR, INTERNAL_SERVER_ERROR)))

      val result = controller.getDetails()(fakeRequestForGetDetails)
      ScalaFutures.whenReady(result.failed) { e =>
        e mustBe a[Upstream5xxResponse]
        e.getMessage mustBe errorResponse("NOT_FOUND")
      }
    }

    "throw generic exception when any other exception returned from Des" in {

      when(mockDesConnector.getAftDetails(Matchers.eq(queryParams))(any(), any())).thenReturn(
        Future.failed(new Exception("Generic Exception")))

      val result = controller.getDetails()(fakeRequestForGetDetails)
      ScalaFutures.whenReady(result.failed) { e =>
        e mustBe a[Exception]
        e.getMessage mustBe "Generic Exception"
      }
    }
  }

  "getAftVersions" must {

    "return OK when the details are returned based on pstr, start date and AFT version" in {
      when(mockDesConnector.getAftVersions(Matchers.eq(pstr), Matchers.eq(startDt))(any(), any())).thenReturn(
        Future.successful(Seq(1)))

      val result = controller.getVersions()(fakeRequest.withHeaders(newHeaders = "pstr" -> pstr, "startDate" -> startDt))

      status(result) mustBe OK
      contentAsJson(result) mustBe Json.arr(1)
    }

    "return OK with empty sequence when NOT FOUND Exeception is thrown" in {
      when(mockDesConnector.getAftVersions(Matchers.eq(pstr), Matchers.eq(startDt))(any(), any())).thenReturn(
        Future.failed(new NotFoundException("No Versions Found")))

      val result = controller.getVersions()(fakeRequest.withHeaders(newHeaders = "pstr" -> pstr, "startDate" -> startDt))

      status(result) mustBe OK
      contentAsJson(result) mustBe JsNull
    }

    "throw BadRequestException when PSTR is not present in the header" in {
      recoverToExceptionIf[BadRequestException] {
        controller.getVersions()(fakeRequest.withHeaders(newHeaders = "startDate" -> startDt))
      } map { response =>
        response.responseCode mustBe BAD_REQUEST
        response.message must include("Bad Request with missing PSTR/Quarter Start Date")
      }
    }

    "throw Upstream5xxResponse when UpStream5XXResponse with INTERNAL_SERVER_ERROR returned from Des" in {
      when(mockDesConnector.getAftVersions(Matchers.eq(pstr), Matchers.eq(startDt))(any(), any())).thenReturn(
        Future.failed(Upstream5xxResponse(errorResponse("INTERNAL SERVER ERROR"), INTERNAL_SERVER_ERROR, INTERNAL_SERVER_ERROR)))

      recoverToExceptionIf[Upstream5xxResponse] {
        controller.getVersions()(fakeRequest.withHeaders(newHeaders = "startDate" -> startDt, "pstr" -> pstr))
      } map { response =>
        response.upstreamResponseCode mustBe INTERNAL_SERVER_ERROR
        response.getMessage must include("INTERNAL SERVER ERROR")
        response.reportAs mustBe INTERNAL_SERVER_ERROR
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

object AFTControllerSpec {
  private val pstr = "12345678RD"
  private val startDt = "2020-01-01"
  private val aftVer = "99"
  private val fbNumber = "20"
  private val etmpResponse: JsValue = Json.obj(
    "schemeDetails" -> Json.obj(
      "pstr" -> "12345678AB",
      "schemeName" -> "PSTR Scheme"
    ),
    "aftDetails" -> Json.obj(
      "aftStatus" -> "Compiled",
      "quarterStartDate" -> "2020-02-29",
      "quarterEndDate" -> "2020-05-29"
    )
  )

  private val transformedUserAnswersJson = Json.obj(
    "aftStatus" -> "Compiled",
    "quarter" -> Json.obj(
      "startDate" -> "2020-02-29",
      "endDate" -> "2020-05-29"
    ),
    "pstr" -> "12345678AB",
    "schemeName" -> "PSTR Scheme"
  )

  private val fakeRequestForGetDetails = FakeRequest("GET", "/").withHeaders(("pstr", pstr), ("startDate", startDt), ("aftVersion", aftVer))
  val queryParams = s"$pstr?startDate=$startDt&aftVersion=$aftVer"
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
}

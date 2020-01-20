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

package connectors

import com.github.tomakehurst.wiremock.client.WireMock._
import org.scalatest.{AsyncWordSpec, EitherValues, MustMatchers}
import play.api.http.Status._
import play.api.libs.json.{JsValue, Json}
import uk.gov.hmrc.http.{BadRequestException, HeaderCarrier, NotFoundException, Upstream4xxResponse, Upstream5xxResponse}
import utils.{JsonFileReader, WireMockHelper}

class DesConnectorSpec extends AsyncWordSpec with MustMatchers with WireMockHelper with JsonFileReader
  with EitherValues {
  import DesConnectorSpec._

  private implicit lazy val hc: HeaderCarrier = HeaderCarrier()

  override protected def portConfigKey: String = "microservice.services.des-hod.port"

  private lazy val connector: DesConnector = injector.instanceOf[DesConnector]
  private val pstr = "test-pstr"
  private val startDt = "2020-01-01"
  private val aftVersion = "1"
  private val aftSubmitUrl = s"/pension-online/pstr/$pstr/aft/return"
  private val getAftUrl = s"/pension-online/aft-return/$pstr?startDate=$startDt&aftVersion=$aftVersion"
  private val getAftVersionsUrl = s"/pension-online/reports/$pstr/AFT/versions/startDate=$startDt"

  "fileAFTReturn" must {

    "return successfully when ETMP has returned OK" in {
      val data = Json.obj(fields = "Id" -> "value")
      server.stubFor(
        post(urlEqualTo(aftSubmitUrl))
          .withRequestBody(equalTo(Json.stringify(data)))
          .willReturn(
            ok
          )
      )
      connector.fileAFTReturn(pstr, data) map {
        _.status mustBe OK
      }
    }

    "return BAD REQUEST when ETMP has returned BadRequestException" in {
      val data = Json.obj(fields = "Id" -> "value")
      server.stubFor(
        post(urlEqualTo(aftSubmitUrl))
          .withRequestBody(equalTo(Json.stringify(data)))
          .willReturn(
            badRequest()
          )
      )

      recoverToExceptionIf[BadRequestException] {
        connector.fileAFTReturn(pstr, data)
      } map {
        _.responseCode mustEqual BAD_REQUEST
      }
    }

    "return NOT FOUND when ETMP has returned NotFoundException" in {
      val data = Json.obj(fields = "Id" -> "value")
      server.stubFor(
        post(urlEqualTo(aftSubmitUrl))
          .withRequestBody(equalTo(Json.stringify(data)))
          .willReturn(
            notFound()
          )
      )

      recoverToExceptionIf[NotFoundException] {
        connector.fileAFTReturn(pstr, data)
      } map {
        _.responseCode mustEqual NOT_FOUND
      }
    }

    "return Upstream5xxResponse when ETMP has returned Internal Server Error" in {
      val data = Json.obj(fields = "Id" -> "value")
      server.stubFor(
        post(urlEqualTo(aftSubmitUrl))
          .withRequestBody(equalTo(Json.stringify(data)))
          .willReturn(
            serverError()
          )
      )
      recoverToExceptionIf[Upstream5xxResponse](connector.fileAFTReturn(pstr, data)) map {
        _.upstreamResponseCode mustBe INTERNAL_SERVER_ERROR
      }
    }
  }

  "getAftDetails" must {
    "return user answer json" in {
      server.stubFor(
        get(urlEqualTo(getAftUrl))
          .willReturn(
            ok
              .withHeader("Content-Type", "application/json")
              .withBody(etmpAFTDetailsResponse.toString())
          )
      )
      connector.getAftDetails(pstr, startDt, aftVersion).map { response =>
        response mustBe etmpAFTDetailsResponse
      }
    }

    "return a BadRequestException for a 400 INVALID_PSTR response" in {
      server.stubFor(
        get(urlEqualTo(getAftUrl))
          .willReturn(
            badRequest
              .withHeader("Content-Type", "application/json")
              .withBody(errorResponse("INVALID_PSTR"))
          )
      )

      recoverToExceptionIf[BadRequestException] {
        connector.getAftDetails(pstr, startDt, aftVersion)
      } map { errorResponse =>
        errorResponse.responseCode mustEqual BAD_REQUEST
        errorResponse.message must include("INVALID_PSTR")
      }
    }

    "return bad request - 400 if body contains INVALID_START_DATE" in {
      server.stubFor(
        get(urlEqualTo(getAftUrl))
          .willReturn(
            badRequest
              .withHeader("Content-Type", "application/json")
              .withBody(errorResponse("INVALID_START_DATE"))
          )
      )

      recoverToExceptionIf[BadRequestException] {
        connector.getAftDetails(pstr, startDt, aftVersion)
      } map { errorResponse =>
        errorResponse.responseCode mustEqual BAD_REQUEST
        errorResponse.message must include("INVALID_START_DATE")
      }
    }

    "return bad request - 400 if body contains INVALID_AFT_VERSION" in {
      server.stubFor(
        get(urlEqualTo(getAftUrl))
          .willReturn(
            badRequest
              .withHeader("Content-Type", "application/json")
              .withBody(errorResponse("INVALID_AFT_VERSION"))
          )
      )

      recoverToExceptionIf[BadRequestException] {
        connector.getAftDetails(pstr, startDt, aftVersion)
      } map { errorResponse =>
        errorResponse.responseCode mustEqual BAD_REQUEST
        errorResponse.message must include("INVALID_AFT_VERSION")
      }
    }

    "return Not Found - 404" in {
      server.stubFor(
        get(urlEqualTo(getAftUrl))
          .willReturn(
            notFound
              .withBody(errorResponse("NOT_FOUND"))
          )
      )

      recoverToExceptionIf[NotFoundException] {
        connector.getAftDetails(pstr, startDt, aftVersion)
      } map { errorResponse =>
        errorResponse.responseCode mustEqual NOT_FOUND
        errorResponse.message must include("NOT_FOUND")
      }
    }

    "throw Upstream4XX for server unavailable - 403" in {

      server.stubFor(
        get(urlEqualTo(getAftUrl))
          .willReturn(
            forbidden
              .withBody(errorResponse("FORBIDDEN"))
          )
      )
      recoverToExceptionIf[Upstream4xxResponse](connector.getAftDetails(pstr, startDt, aftVersion)) map {
        ex =>
          ex.upstreamResponseCode mustBe FORBIDDEN
          ex.message must include("FORBIDDEN")
      }
    }

    "throw Upstream5XX for internal server error - 500 and log the event as error" in {

      server.stubFor(
        get(urlEqualTo(getAftUrl))
          .willReturn(
            serverError
              .withBody(errorResponse("SERVER_ERROR"))
          )
      )

      recoverToExceptionIf[Upstream5xxResponse](connector.getAftDetails(pstr, startDt, aftVersion)) map {
        ex =>
          ex.upstreamResponseCode mustBe INTERNAL_SERVER_ERROR
          ex.message must include("SERVER_ERROR")
          ex.reportAs mustBe BAD_GATEWAY
      }
    }
  }

  "getAftVersions" must {
    "return the seq of version nos returned from the ETMP" in {
      val aftVersionsResponseJson = Json.arr(
        Json.obj(
          fields = "reportVersion" -> 1
        )
      )
      server.stubFor(
        get(urlEqualTo(getAftVersionsUrl))
          .willReturn(
            ok
              .withHeader("Content-Type", "application/json")
              .withBody(aftVersionsResponseJson.toString())
          )
      )
      connector.getAftVersions(pstr, startDt).map { response =>
        response mustBe Seq(1)
      }
    }

    "return a BadRequestException for BAD REQUEST - 400" in {
      server.stubFor(
        get(urlEqualTo(getAftVersionsUrl))
          .willReturn(
            badRequest
              .withHeader("Content-Type", "application/json")
              .withBody(errorResponse("INVALID_PSTR"))
          )
      )

      recoverToExceptionIf[BadRequestException] {
        connector.getAftVersions(pstr, startDt)
      } map { errorResponse =>
        errorResponse.responseCode mustEqual BAD_REQUEST
        errorResponse.message must include("INVALID_PSTR")
      }
    }

    "return a NotFoundException for NOT FOUND - 404" in {
      server.stubFor(
        get(urlEqualTo(getAftVersionsUrl))
          .willReturn(
            notFound
              .withBody(errorResponse("NOT_FOUND"))
          )
      )

      connector.getAftVersions(pstr, startDt).map { response =>
        response mustBe Nil
      }
    }

    "throw Upstream4XX for FORBIDDEN - 403" in {

      server.stubFor(
        get(urlEqualTo(getAftVersionsUrl))
          .willReturn(
            forbidden
              .withBody(errorResponse("FORBIDDEN"))
          )
      )
      recoverToExceptionIf[Upstream4xxResponse](connector.getAftVersions(pstr, startDt)) map {
        ex =>
          ex.upstreamResponseCode mustBe FORBIDDEN
          ex.message must include("FORBIDDEN")
      }
    }

    "throw Upstream5XX for INTERNAL SERVER ERROR - 500" in {

      server.stubFor(
        get(urlEqualTo(getAftVersionsUrl))
          .willReturn(
            serverError
              .withBody(errorResponse("SERVER_ERROR"))
          )
      )

      recoverToExceptionIf[Upstream5xxResponse](connector.getAftVersions(pstr, startDt)) map {
        ex =>
          ex.upstreamResponseCode mustBe INTERNAL_SERVER_ERROR
          ex.message must include("SERVER_ERROR")
      }
    }
  }

  "getCorrelationId" must {
    "return the correct CorrelationId when the request Id is more than 32 characters" in {
      val requestId = Some("govuk-tax-4725c811-9251-4c06-9b8f-f1d84659b2dfe")
      val result = connector.getCorrelationId(requestId)
      result mustBe "4725c81192514c069b8ff1d84659b2df"
    }


    "return the correct CorrelationId when the request Id is less than 32 characters" in {
      val requestId = Some("govuk-tax-4725c811-9251-4c06-9b8f-f1")
      val result = connector.getCorrelationId(requestId)
      result mustBe "4725c81192514c069b8ff1"
    }

    "return the correct CorrelationId when the request Id does not have gov-uk-tax or -" in {
      val requestId = Some("4725c81192514c069b8ff1")
      val result = connector.getCorrelationId(requestId)
      result mustBe "4725c81192514c069b8ff1"
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
object DesConnectorSpec {
  private val etmpAFTDetailsResponse: JsValue = Json.obj(
    "schemeDetails" -> Json.obj(
      "pstr" -> "12345678AB",
      "schemeName" -> "PSTR Scheme"
    ),
    "aftDetails" -> Json.obj(
      "aftStatus" -> "Compiled",
      "quarterStartDate" -> "2020-02-29",
      "quarterEndDate" -> "2020-05-29"
    ),
    "chargeDetails" -> Json.obj(
      "chargeTypeFDetails" -> Json.obj(
        "totalAmount" -> 200.02,
        "dateRegiWithdrawn" -> "1980-02-29"
      )
    )
  )
}

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

import java.time.LocalDate

import audit._
import com.github.tomakehurst.wiremock.client.WireMock._
import models.PsaFS
import org.scalatest.{AsyncWordSpec, EitherValues, MustMatchers}
import org.scalatestplus.mockito.MockitoSugar
import play.api.http.Status._
import play.api.inject.bind
import play.api.inject.guice.GuiceableModule
import play.api.libs.json.{JsValue, Json}
import play.api.mvc.RequestHeader
import play.api.test.FakeRequest
import services.AFTService
import uk.gov.hmrc.http._
import utils.{JsonFileReader, WireMockHelper}

class FinancialStatementConnectorSpec extends AsyncWordSpec with MustMatchers with WireMockHelper with JsonFileReader
  with EitherValues with MockitoSugar {

  import FinancialStatementConnectorSpec._

  private implicit lazy val hc: HeaderCarrier = HeaderCarrier()
  private implicit lazy val rh: RequestHeader = FakeRequest("", "")

  override protected def portConfigKey: String = "microservice.services.des-hod.port"

  private val mockAuditService = mock[AuditService]
  private val mockAftService = mock[AFTService]

  private lazy val connector: FinancialStatementConnector = injector.instanceOf[FinancialStatementConnector]

  override protected def bindings: Seq[GuiceableModule] =
    Seq(
      bind[AuditService].toInstance(mockAuditService),
      bind[AFTService].toInstance(mockAftService)
    )

  private val psaId = "test-psa-id"
  private val getAftUrl = s"/pension-online/financial-statements/psaid/$psaId?dataset=medium"

  "getAftDetails" must {
    "return user answer json when successful response returned from ETMP" in {
      server.stubFor(
        get(urlEqualTo(getAftUrl))
          .willReturn(
            ok
              .withHeader("Content-Type", "application/json")
              .withBody(psaFSResponse.toString())
          )
      )

      connector.getPsaFS(psaId).map { response =>
        response mustBe psaModel
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
        connector.getPsaFS(psaId)
      } map { errorResponse =>
        errorResponse.responseCode mustEqual BAD_REQUEST
        errorResponse.message must include("INVALID_PSTR")
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
        connector.getPsaFS(psaId)
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
      recoverToExceptionIf[Upstream4xxResponse](connector.getPsaFS(psaId)) map {
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

      recoverToExceptionIf[Upstream5xxResponse](connector.getPsaFS(psaId)) map {
        ex =>
          ex.upstreamResponseCode mustBe INTERNAL_SERVER_ERROR
          ex.message must include("SERVER_ERROR")
          ex.reportAs mustBe BAD_GATEWAY
      }
    }
  }

  "getCorrelationId" must {
    "return the correct CorrelationId when the request Id is more than 32 characters" in {
      val requestId = Some("govuk-tax-4725c811-9251-4c06-9b8f-f1d84659b2dfe")
      val result = connector.getCorrelationId(requestId)
      result mustBe "4725c811-9251-4c06-9b8f-f1d84659b2df"
    }


    "return the correct CorrelationId when the request Id is less than 32 characters" in {
      val requestId = Some("govuk-tax-4725c811-9251-4c06-9b8f-f1")
      val result = connector.getCorrelationId(requestId)
      result mustBe "4725c811-9251-4c06-9b8f-f1"
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

object FinancialStatementConnectorSpec {
  private val psaFSResponse: JsValue = Json.arr(
    Json.obj(
    "chargeReference" -> "XY002610150184",
    "chargeType" -> "57001080",
    "dueDate" -> "2020-02-15",
    "outstandingAmount" -> 56049.08,
    "stoodOverAmount" -> 25089.08,
    "amountDue" -> 1029.05,
    "periodStartDate" -> "2020-04-01",
    "periodEndDate" -> "2020-06-30",
    "pstr" -> "24000040IN"
  ),
    Json.obj(
      "chargeReference" -> "XY002610150184",
      "chargeType" -> "57001080",
      "dueDate" -> "2020-02-15",
      "outstandingAmount" -> 56049.08,
      "stoodOverAmount" -> 25089.08,
      "amountDue" -> 1029.05,
      "periodStartDate" -> "2020-04-01",
      "periodEndDate" -> "2020-06-30",
      "pstr" -> "24000041IN"
    )
  )

  private val psaModel: Seq[PsaFS] = Seq(
      PsaFS(
      chargeReference = "XY002610150184",
      chargeType = "AFT Initial LFP",
      dueDate = Some(LocalDate.parse("2020-02-15")),
      outstandingAmount = 56049.08,
      stoodOverAmount = 25089.08,
      amountDue = 1029.05,
      periodStartDate =  LocalDate.parse("2020-04-01"),
      periodEndDate =  LocalDate.parse("2020-06-30"),
      pstr = "24000040IN"
    ),
    PsaFS(
      chargeReference = "XY002610150184",
      chargeType = "AFT Initial LFP",
      dueDate = Some(LocalDate.parse("2020-02-15")),
      outstandingAmount = 56049.08,
      stoodOverAmount = 25089.08,
      amountDue = 1029.05,
      periodStartDate =  LocalDate.parse("2020-04-01"),
      periodEndDate =  LocalDate.parse("2020-06-30"),
      pstr = "24000041IN"
    )
  )
}

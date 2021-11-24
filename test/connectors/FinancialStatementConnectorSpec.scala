/*
 * Copyright 2021 HM Revenue & Customs
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
import models.{PsaFS, SchemeFS}
import org.mockito.ArgumentMatchers.any
import org.mockito.{ArgumentCaptor, Mockito}
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AsyncWordSpec
import org.mockito.MockitoSugar
import play.api.http.Status
import play.api.http.Status._
import play.api.inject.bind
import play.api.inject.guice.GuiceableModule
import play.api.libs.json.{JsObject, JsValue, Json}
import play.api.mvc.RequestHeader
import play.api.test.FakeRequest
import repository.AftBatchedDataCacheRepository
import services.AFTService
import uk.gov.hmrc.http._
import utils.WireMockHelper

class FinancialStatementConnectorSpec extends AsyncWordSpec with Matchers with WireMockHelper with MockitoSugar {

  import FinancialStatementConnectorSpec._

  private implicit lazy val hc: HeaderCarrier = HeaderCarrier()
  private implicit lazy val rh: RequestHeader = FakeRequest("", "")

  override protected def portConfigKey: String = "microservice.services.if-hod.port"

  private val mockAuditService = mock[AuditService]
  private val mockAftService = mock[AFTService]
  private val mockDataCacheRepository = mock[AftBatchedDataCacheRepository]

  private lazy val connector: FinancialStatementConnector = injector.instanceOf[FinancialStatementConnector]

  override protected def bindings: Seq[GuiceableModule] =
    Seq(
      bind[AuditService].toInstance(mockAuditService),
      bind[AftBatchedDataCacheRepository].toInstance(mockDataCacheRepository),
      bind[AFTService].toInstance(mockAftService)
    )

  private val psaId = "test-psa-id"
  private val pstr = "test-pstr"
  private val getPsaFSUrl = s"/pension-online/financial-statements/psaid/$psaId?dataset=medium"
  private val getSchemeFSUrl = s"/pension-online/financial-statements/pstr/$pstr?dataset=medium"

  "getPsaFS" must {
    "return user answer json when successful response returned from ETMP" in {
      server.stubFor(
        get(urlEqualTo(getPsaFSUrl))
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

    "send the GetPsaFS audit event when ETMP has returned OK" in {
      Mockito.reset(mockAuditService)
      server.stubFor(
        get(urlEqualTo(getPsaFSUrl))
          .willReturn(
            ok
              .withHeader("Content-Type", "application/json")
              .withBody(psaFSResponse.toString())
          )
      )

      val eventCaptor = ArgumentCaptor.forClass(classOf[GetPsaFS])
      connector.getPsaFS(psaId).map { _ =>
        verify(mockAuditService, times(1)).sendEvent(eventCaptor.capture())(any(), any())
        eventCaptor.getValue mustEqual GetPsaFS(psaId, Status.OK, Some(Json.toJson(psaModel)))
      }
    }

    "return a BadRequestException for anything else" in {
      server.stubFor(
        get(urlEqualTo(getPsaFSUrl))
          .willReturn(
            badRequest
              .withHeader("Content-Type", "application/json")
              .withBody(errorResponse("INVALID_PSTR"))
          )
      )

      recoverToExceptionIf[BadRequestException] {
        connector.getPsaFS(psaId)
      } map { errorResponse =>
        errorResponse.responseCode mustBe BAD_REQUEST
        errorResponse.message must include("INVALID_PSTR")
      }
    }

    "return Empty sequence - 404" in {
      server.stubFor(
        get(urlEqualTo(getPsaFSUrl))
          .willReturn(
            notFound
              .withBody(errorResponse("NOT_FOUND"))
          )
      )

      connector.getPsaFS(psaId) map {
        response =>
          response mustBe Seq.empty
      }
    }

    "throw UpstreamErrorResponse for server unavailable - 403" in {

      server.stubFor(
        get(urlEqualTo(getPsaFSUrl))
          .willReturn(
            forbidden
              .withBody(errorResponse("FORBIDDEN"))
          )
      )
      recoverToExceptionIf[UpstreamErrorResponse](connector.getPsaFS(psaId)) map {
        ex =>
          ex.statusCode mustBe FORBIDDEN
          ex.message must include("FORBIDDEN")
      }
    }

    "throw UpstreamErrorResponse for internal server error - 500 and log the event as error" in {

      server.stubFor(
        get(urlEqualTo(getPsaFSUrl))
          .willReturn(
            serverError
              .withBody(errorResponse("SERVER_ERROR"))
          )
      )

      recoverToExceptionIf[UpstreamErrorResponse](connector.getPsaFS(psaId)) map {
        ex =>
          ex.statusCode mustBe INTERNAL_SERVER_ERROR
          ex.message must include("SERVER_ERROR")
          ex.reportAs mustBe BAD_GATEWAY
      }
    }
  }

  "getSchemeFS" must {
    "return user answer json when successful response returned from ETMP" in {
      server.stubFor(
        get(urlEqualTo(getSchemeFSUrl))
          .willReturn(
            ok
              .withHeader("Content-Type", "application/json")
              .withBody(schemeFSResponse.toString())
          )
      )

      connector.getSchemeFS(pstr).map { response =>
        response mustBe schemeModel
      }
    }

    "send the GetSchemeFS audit event when ETMP has returned OK" in {
      Mockito.reset(mockAuditService)
      server.stubFor(
        get(urlEqualTo(getSchemeFSUrl))
          .willReturn(
            ok
              .withHeader("Content-Type", "application/json")
              .withBody(schemeFSResponse.toString())
          )
      )

      val eventCaptor = ArgumentCaptor.forClass(classOf[GetSchemeFS])
      connector.getSchemeFS(pstr).map { _ =>
        verify(mockAuditService, times(1)).sendEvent(eventCaptor.capture())(any(), any())
        eventCaptor.getValue mustEqual GetSchemeFS(pstr, Status.OK, Some(Json.toJson(schemeModel)))
      }
    }

    "return a BadRequestException for a 400 INVALID_PSTR response" in {
      server.stubFor(
        get(urlEqualTo(getSchemeFSUrl))
          .willReturn(
            badRequest
              .withHeader("Content-Type", "application/json")
              .withBody(errorResponse("INVALID_PSTR"))
          )
      )

      recoverToExceptionIf[BadRequestException] {
        connector.getSchemeFS(pstr)
      } map { errorResponse =>
        errorResponse.responseCode mustEqual BAD_REQUEST
        errorResponse.message must include("INVALID_PSTR")
      }
    }

    "return Empty sequence - 404" in {
      server.stubFor(
        get(urlEqualTo(getSchemeFSUrl))
          .willReturn(
            notFound
              .withBody(errorResponse("NOT_FOUND"))
          )
      )

      connector.getSchemeFS(pstr).map { response =>
        response mustBe Seq.empty
      }
    }

    "throw UpstreamErrorResponse for server unavailable - 403" in {

      server.stubFor(
        get(urlEqualTo(getSchemeFSUrl))
          .willReturn(
            forbidden
              .withBody(errorResponse("FORBIDDEN"))
          )
      )
      recoverToExceptionIf[UpstreamErrorResponse](connector.getSchemeFS(pstr)) map {
        ex =>
          ex.statusCode mustBe FORBIDDEN
          ex.message must include("FORBIDDEN")
      }
    }

    "throw UpstreamErrorResponse for internal server error - 500 and log the event as error" in {

      server.stubFor(
        get(urlEqualTo(getSchemeFSUrl))
          .willReturn(
            serverError
              .withBody(errorResponse("SERVER_ERROR"))
          )
      )

      recoverToExceptionIf[UpstreamErrorResponse](connector.getSchemeFS(pstr)) map {
        ex =>
          ex.statusCode mustBe INTERNAL_SERVER_ERROR
          ex.message must include("SERVER_ERROR")
          ex.reportAs mustBe BAD_GATEWAY
      }
    }
  }
}

object FinancialStatementConnectorSpec {
  def errorResponse(code: String): String = {
    Json.stringify(
      Json.obj(
        "code" -> code,
        "reason" -> s"Reason for $code"
      )
    )
  }

  private val psaFSResponse: JsValue = Json.arr(
    Json.obj(
      "chargeReference" -> "Not Applicable",
      "chargeType" -> "00600100",
      "totalAmount" -> -15000.00,
      "dueDate" -> "2020-06-25",
      "amountDue" -> -15000.00,
      "outstandingAmount" -> -15000.00,
      "stoodOverAmount" -> 0.00,
      "accruedInterestTotal" -> 0.00,
    ),
    Json.obj(
      "chargeReference" -> "XY002610150184",
      "chargeType" -> "57001080",
      "dueDate" -> "2020-02-15",
      "totalAmount" -> 80000.00,
      "outstandingAmount" -> 56049.08,
      "stoodOverAmount" -> 25089.08,
      "accruedInterestTotal" -> 123.00,
      "amountDue" -> 1029.05,
      "periodStartDate" -> "2020-04-01",
      "periodEndDate" -> "2020-06-30",
      "pstr" -> "24000040IN"
    ),
    Json.obj(
      "chargeReference" -> "XY002610150184",
      "chargeType" -> "57001091",
      "dueDate" -> "2020-02-15",
      "totalAmount" -> 80000.00,
      "outstandingAmount" -> 56049.08,
      "stoodOverAmount" -> 25089.08,
      "accruedInterestTotal" -> 123.00,
      "amountDue" -> 1029.05,
      "periodStartDate" -> "2020-04-01",
      "periodEndDate" -> "2020-06-30",
      "pstr" -> "24000041IN"
    )
  )

  private val psaModel: Seq[PsaFS] = Seq(
    PsaFS(
      chargeReference = "XY002610150184",
      chargeType = "Accounting for Tax late filing penalty",
      dueDate = Some(LocalDate.parse("2020-02-15")),
      totalAmount = 80000.00,
      outstandingAmount = 56049.08,
      stoodOverAmount = 25089.08,
      accruedInterestTotal = 123.00,
      amountDue = 1029.05,
      periodStartDate = LocalDate.parse("2020-04-01"),
      periodEndDate = LocalDate.parse("2020-06-30"),
      pstr = "24000040IN"
    ),
    PsaFS(
      chargeReference = "XY002610150184",
      chargeType = "Accounting for Tax further late filing penalty",
      dueDate = Some(LocalDate.parse("2020-02-15")),
      totalAmount = 80000.00,
      outstandingAmount = 56049.08,
      stoodOverAmount = 25089.08,
      accruedInterestTotal = 123.00,
      amountDue = 1029.05,
      periodStartDate = LocalDate.parse("2020-04-01"),
      periodEndDate = LocalDate.parse("2020-06-30"),
      pstr = "24000041IN"
    )
  )

  private def schemeFSJsValue(chargeReference: String): JsObject = Json.obj(
    "chargeReference" -> s"XY00261015018$chargeReference",
    "chargeType" -> "56001000",
    "dueDate" -> "2020-02-15",
    "totalAmount" -> 80000.00,
    "outstandingAmount" -> 56049.08,
    "stoodOverAmount" -> 25089.08,
    "amountDue" -> 1029.05,
    "accruedInterestTotal" -> 100.05,
    "periodStartDate" -> "2020-04-01",
    "periodEndDate" -> "2020-06-30"
  )

  private def schemeFSModel(chargeReference: String) = SchemeFS(
    chargeReference = s"XY00261015018$chargeReference",
    chargeType = "Accounting for Tax return",
    dueDate = Some(LocalDate.parse("2020-02-15")),
    totalAmount = 80000.00,
    amountDue = 1029.05,
    outstandingAmount = 56049.08,
    accruedInterestTotal = 100.05,
    stoodOverAmount = 25089.08,
    periodStartDate = Some(LocalDate.parse("2020-04-01")),
    periodEndDate = Some(LocalDate.parse("2020-06-30"))
  )

  private val schemeFSResponse: JsValue = Json.arr(
    schemeFSJsValue(chargeReference = "4"),
    schemeFSJsValue(chargeReference = "5")
  )

  private val schemeModel: Seq[SchemeFS] = Seq(
    schemeFSModel(chargeReference = "4"),
    schemeFSModel(chargeReference = "5")
  )
}

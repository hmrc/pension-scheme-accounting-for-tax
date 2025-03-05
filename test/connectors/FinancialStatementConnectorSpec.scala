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

package connectors

import audit._
import com.github.tomakehurst.wiremock.client.WireMock._
import models._
import org.mockito.ArgumentMatchers.{any, eq => eqTo}
import org.mockito.Mockito.{times, verify, when}
import org.mockito.{ArgumentCaptor, Mockito}
import org.scalatest.BeforeAndAfterEach
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AsyncWordSpec
import org.scalatestplus.mockito.MockitoSugar
import play.api.http.Status
import play.api.http.Status._
import play.api.inject.bind
import play.api.inject.guice.GuiceableModule
import play.api.libs.json.{JsValue, Json}
import play.api.mvc.RequestHeader
import play.api.test.FakeRequest
import repository._
import services.AFTService
import uk.gov.hmrc.http._
import utils.WireMockHelper

import java.time.LocalDate
import scala.concurrent.Future

class FinancialStatementConnectorSpec extends AsyncWordSpec with Matchers with WireMockHelper with MockitoSugar with BeforeAndAfterEach {

  import FinancialStatementConnectorSpec._

  private implicit lazy val hc: HeaderCarrier = HeaderCarrier()
  private implicit lazy val rh: RequestHeader = FakeRequest("", "")

  override protected def portConfigKey: String = "microservice.services.if-hod.port"

  private val mockAuditService = mock[AuditService]
  private val mockAftService = mock[AFTService]
  private lazy val connector: FinancialStatementConnector = injector.instanceOf[FinancialStatementConnector]
  private val mockRepo = mock[SchemeFSCacheRepository]


  override protected def bindings: Seq[GuiceableModule] =
    Seq(
      bind[AuditService].toInstance(mockAuditService),
      bind[AFTService].toInstance(mockAftService),
      bind[AftBatchedDataCacheRepository].toInstance(mock[AftBatchedDataCacheRepository]),
      bind[AftOverviewCacheRepository].toInstance(mock[AftOverviewCacheRepository]),
      bind[FileUploadReferenceCacheRepository].toInstance(mock[FileUploadReferenceCacheRepository]),
      bind[FileUploadOutcomeRepository].toInstance(mock[FileUploadOutcomeRepository]),
      bind[FinancialInfoCacheRepository].toInstance(mock[FinancialInfoCacheRepository]),
  bind[SchemeFSCacheRepository].toInstance(mockRepo))

  private val psaId = "test-psa-id"
  private val pstr = "test-pstr"
  private val getPsaFSMaxUrl = s"/pension-online/financial-statements/psaid/$psaId?dataset=maximum"
  private val getSchemeFSMaxUrl = s"/pension-online/financial-statements/pstr/$pstr?dataset=maximum"

  override def beforeEach(): Unit = {

    import org.mockito.Mockito._
    super.beforeEach()
    reset(mockRepo)
  }

  "getPsaFS" must {

    "return user answer json when successful response returned from ETMP" in {
      server.stubFor(
        get(urlEqualTo(getPsaFSMaxUrl))
          .willReturn(
            ok
              .withHeader("Content-Type", "application/json")
              .withBody(psaFSWrapperResponseMax.toString())
          )
      )
      connector.getPsaFS(psaId).map { response =>
        response mustBe psaModelMax
      }
    }

    "send the GetPsaFS audit event when ETMP has returned OK" in {
      Mockito.reset(mockAuditService)
      server.stubFor(
        get(urlEqualTo(getPsaFSMaxUrl))
          .willReturn(
            ok
              .withHeader("Content-Type", "application/json")
              .withBody(psaFSWrapperResponseMax.toString())
          )
      )

      val eventCaptor = ArgumentCaptor.forClass(classOf[GetPsaFS])
      connector.getPsaFS(psaId).map { _ =>
        verify(mockAuditService, times(1)).sendEvent(eventCaptor.capture())(any(), any())
        eventCaptor.getValue mustEqual GetPsaFS(psaId, Status.OK, Some(Json.toJson(psaModelMax)))
      }
    }

    "return a BadRequestException for anything else" in {
      server.stubFor(
        get(urlEqualTo(getPsaFSMaxUrl))
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
        get(urlEqualTo(getPsaFSMaxUrl))
          .willReturn(
            notFound
              .withBody(errorResponse("NOT_FOUND"))
          )
      )

      connector.getPsaFS(psaId) map {
        response =>
          response mustBe PsaFS(inhibitRefundSignal = false, Seq.empty)
      }
    }

    "throw UpstreamErrorResponse for server unavailable - 403" in {

      server.stubFor(
        get(urlEqualTo(getPsaFSMaxUrl))
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
        get(urlEqualTo(getPsaFSMaxUrl))
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

    "return maximum answer json when successful response returned from ETMP " in {


      when(mockRepo.get(eqTo(s"schemeFS-$pstr"))(any()))
        .thenReturn(Future.successful(Some(Json.obj("testId" -> "data"))))

      when(mockRepo.save(any(), any())(any())) thenReturn Future.successful((): Unit)


      server.stubFor(
        get(urlEqualTo(getSchemeFSMaxUrl))
          .willReturn(
            ok
              .withHeader("Content-Type", "application/json")
              .withBody(schemeFSWrapperResponseMax.toString())
          )
      )

      connector.getSchemeFS(pstr).map { response =>
        response mustBe schemeFSWrapperModel
      }
    }

    "timeout when ETMP takes too long to respond" in {

      Mockito.reset(mockAuditService)

      when(mockRepo.get(eqTo(s"schemeFS-$pstr"))(any()))
        .thenReturn(Future.successful(Some(Json.obj("testId" -> "data"))))

      when(mockRepo.save(any(), any())(any())) thenReturn Future.successful((): Unit)

      server.stubFor(
        get(urlEqualTo(getSchemeFSMaxUrl))
          .willReturn(
            aResponse()
              .withFixedDelay(2000)
              .withHeader("Content-Type", "application/json")
          )
      )

      recoverToExceptionIf[Exception] {
        connector.getSchemeFS(pstr)
      }.map { exception =>
        exception.getMessage must include("Request timeout")
        exception.getMessage must include("after 1000 ms")
      }
    }

    "send the GetSchemeFS audit event when ETMP has returned OK" in {
      Mockito.reset(mockAuditService)

      when(mockRepo.get(eqTo(s"schemeFS-$pstr"))(any()))
        .thenReturn(Future.successful(Some(Json.obj("testId" -> "data"))))

      when(mockRepo.save(any(), any())(any())) thenReturn Future.successful((): Unit)

      server.stubFor(
        get(urlEqualTo(getSchemeFSMaxUrl))
          .willReturn(
            ok
              .withHeader("Content-Type", "application/json")
              .withBody(schemeFSWrapperResponseMax.toString())
          )
      )

      val eventCaptor = ArgumentCaptor.forClass(classOf[GetSchemeFS])
      connector.getSchemeFS(pstr).map { _ =>
        verify(mockAuditService, times(1)).sendEvent(eventCaptor.capture())(any(), any())
        eventCaptor.getValue mustEqual GetSchemeFS(pstr, Status.OK, Some(Json.toJson(schemeFSWrapperModel)))
      }
    }

    "return a BadRequestException for a 400 INVALID_PSTR response" in {

      when(mockRepo.get(eqTo(s"schemeFS-$pstr"))(any()))
        .thenReturn(Future.successful(Some(Json.obj("testId" -> "data"))))

      when(mockRepo.save(any(), any())(any())) thenReturn Future.successful((): Unit)

      server.stubFor(
        get(urlEqualTo(getSchemeFSMaxUrl))
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

      when(mockRepo.get(eqTo(s"schemeFS-$pstr"))(any()))
        .thenReturn(Future.successful(Some(Json.obj("testId" -> "data"))))

      when(mockRepo.save(any(), any())(any())) thenReturn Future.successful((): Unit)

      server.stubFor(
        get(urlEqualTo(getSchemeFSMaxUrl))
          .willReturn(
            notFound
              .withBody(errorResponse("NOT_FOUND"))
          )
      )

      connector.getSchemeFS(pstr).map { response =>
        response mustBe SchemeFS(inhibitRefundSignal = false, Seq.empty)
      }
    }

    "throw UpstreamErrorResponse for server unavailable - 403" in {

      when(mockRepo.get(eqTo(s"schemeFS-$pstr"))(any()))
        .thenReturn(Future.successful(Some(Json.obj("testId" -> "data"))))

      when(mockRepo.save(any(), any())(any())) thenReturn Future.successful((): Unit)

      server.stubFor(
        get(urlEqualTo(getSchemeFSMaxUrl))
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

      when(mockRepo.get(eqTo(s"schemeFS-$pstr"))(any()))
        .thenReturn(Future.successful(Some(Json.obj("testId" -> "data"))))

      when(mockRepo.save(any(), any())(any())) thenReturn Future.successful((): Unit)

      server.stubFor(
        get(urlEqualTo(getSchemeFSMaxUrl))
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

  private val psaModelMax: PsaFS = PsaFS(inhibitRefundSignal = true, Seq(
    PsaFSDetail(
      index = 1,
      chargeReference = "Not Applicable",
      chargeType = "Payment on Account",
      dueDate = Some(LocalDate.parse("2020-06-25")),
      totalAmount = -15000.00,
      amountDue = -15000.00,
      outstandingAmount = -15000.00,
      stoodOverAmount = 0.00,
      accruedInterestTotal = 0.00,
      periodStartDate = LocalDate.parse("2020-04-01"),
      periodEndDate = LocalDate.parse("2020-06-30"),
      pstr = "24000040IN",
      sourceChargeRefForInterest = Some("XY002610150181"),
      psaSourceChargeInfo = None,
      Seq(DocumentLineItemDetail(
        clearingReason = Some("C1"),
        clearingDate = Some(LocalDate.parse("2020-06-30")),
        paymDateOrCredDueDate = Some(LocalDate.parse("2020-04-24")),
        clearedAmountItem = BigDecimal(0.00))
      )
    ),
    PsaFSDetail(
      index = 2,
      chargeReference = "Not Applicable",
      chargeType = "Accounting for Tax Late Filing Penalty",
      dueDate = Some(LocalDate.parse("2020-02-15")),
      totalAmount = 80000.00,
      amountDue = 1029.05,
      outstandingAmount = 56049.08,
      stoodOverAmount = 25089.08,
      accruedInterestTotal = 123.00,
      periodStartDate = LocalDate.parse("2020-04-01"),
      periodEndDate = LocalDate.parse("2020-06-30"),
      pstr = "24000040IN",
      sourceChargeRefForInterest = Some("XY002610150181"),
      psaSourceChargeInfo = None,
      Seq(DocumentLineItemDetail(
        clearingReason = Some("C1"),
        clearingDate = Some(LocalDate.parse("2020-06-30")),
        paymDateOrCredDueDate = Some(LocalDate.parse("2020-04-24")),
        clearedAmountItem = BigDecimal(0.00))
      )
    ),
    PsaFSDetail(
      index = 3,
      chargeReference = "XY002610150184",
      chargeType = "Accounting for Tax Further Late Filing Penalty",
      dueDate = Some(LocalDate.parse("2020-02-15")),
      totalAmount = 80000.00,
      amountDue = 1029.05,
      outstandingAmount = 56049.08,
      stoodOverAmount = 25089.08,
      accruedInterestTotal = 123.00,
      periodStartDate = LocalDate.parse("2020-04-01"),
      periodEndDate = LocalDate.parse("2020-06-30"),
      pstr = "24000040IN",
      sourceChargeRefForInterest = Some("XY002610150181"),
      psaSourceChargeInfo = None,
      Seq(DocumentLineItemDetail(
        clearingReason = Some("C1"),
        clearingDate = Some(LocalDate.parse("2020-06-30")),
        paymDateOrCredDueDate = Some(LocalDate.parse("2020-04-24")),
        clearedAmountItem = BigDecimal(0.00))
      ))
  )
  )

  private val psaFSMaxResponse: JsValue = Json.arr(
    Json.obj(
      "index" -> 1,
      "chargeReference" -> "Not Applicable",
      "chargeType" -> "00600100",
      "totalAmount" -> -15000.00,
      "dueDate" -> "2020-06-25",
      "amountDue" -> -15000.00,
      "outstandingAmount" -> -15000.00,
      "stoodOverAmount" -> 0.00,
      "accruedInterestTotal" -> 0.00,
      "periodStartDate" -> "2020-04-01",
      "periodEndDate" -> "2020-06-30",
      "pstr" -> "24000040IN",
      "sourceChargeIndex" -> None,
      "sourceChargeRefForInterest" -> "XY002610150181",
      "documentLineItemDetails" -> Json.arr(
        Json.obj(
          "clearingDate" -> "2020-06-30",
          "paymDateOrCredDueDate" -> "2020-04-24",
          "clearingReason" -> "C1",
          "clearedAmountItem" -> 0.00
        )
      )
    ),
    Json.obj(
      "index" -> 2,
      "chargeReference" -> "Not Applicable",
      "chargeType" -> "57001080",
      "dueDate" -> "2020-02-15",
      "totalAmount" -> 80000.00,
      "outstandingAmount" -> 56049.08,
      "stoodOverAmount" -> 25089.08,
      "accruedInterestTotal" -> 123.00,
      "amountDue" -> 1029.05,
      "periodStartDate" -> "2020-04-01",
      "periodEndDate" -> "2020-06-30",
      "pstr" -> "24000040IN",
      "sourceChargeRefForInterest" -> "XY002610150181",
      "sourceChargeIndex" -> None,
      "documentLineItemDetails" -> Json.arr(
        Json.obj(
          "clearingDate" -> "2020-06-30",
          "paymDateOrCredDueDate" -> "2020-04-24",
          "clearingReason" -> "C1",
          "clearedAmountItem" -> 0.00
        )
      )
    ),
    Json.obj(
      "index" -> 3,
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
      "pstr" -> "24000040IN",
      "sourceChargeRefForInterest" -> "XY002610150181",
      "sourceChargeIndex" -> None,
      "documentLineItemDetails" -> Json.arr(
        Json.obj(
          "clearingDate" -> "2020-06-30",
          "paymDateOrCredDueDate" -> "2020-04-24",
          "clearingReason" -> "C1",
          "clearedAmountItem" -> 0.00
        )
      )
    )
  )

  private val psaFSWrapperResponseMax: JsValue = Json.obj("accountHeaderDetails" -> Json.obj("inhibitRefundSignal" -> true)) ++
    Json.obj("documentHeaderDetails" -> psaFSMaxResponse)

  private val schemeFSMaxSeqJson: JsValue = Json.arr(
    Json.obj(
      "chargeReference" -> s"XY002610150184",
      "chargeType" -> "56001000",
      "dueDate" -> "2020-02-15",
      "totalAmount" -> 80000.00,
      "outstandingAmount" -> 56049.08,
      "stoodOverAmount" -> 25089.08,
      "amountDue" -> 1029.05,
      "accruedInterestTotal" -> 100.05,
      "periodStartDate" -> "2020-01-01",
      "periodEndDate" -> "2020-03-31",
      "sapDocumentNumber" -> "123456789192",
      "postingDate" -> "<StartOfQ1LastYear>",
      "clearedAmountTotal" -> 7035.10,
      "formbundleNumber" -> "222",
      "aftVersion" -> 1,
      "chargeClassification" -> "Charge",
      "documentLineItemDetails" -> Json.arr(
        Json.obj(
          "clearedAmountItem" -> 0.00,
          "clearingDate" -> "2020-06-30",
          "paymDateOrCredDueDate" -> "2020-04-24",
          "clearingReason" -> "C1",
        )
      )
    ),
    Json.obj(
      "chargeReference" -> s"XY002610150185",
      "chargeType" -> "56052000",
      "dueDate" -> "2020-07-15",
      "totalAmount" -> 129.00,
      "outstandingAmount" -> 78.08,
      "stoodOverAmount" -> 56.08,
      "amountDue" -> 333.05,
      "accruedInterestTotal" -> 22.05,
      "periodStartDate" -> "2020-04-01",
      "periodEndDate" -> "2020-06-30",
      "sapDocumentNumber" -> "123456789192",
      "postingDate" -> "<StartOfQ1LastYear>",
      "clearedAmountTotal" -> 7035.10,
      "formbundleNumber" -> "123456789183",
      "aftVersion" -> 1,
      "chargeClassification" -> "Charge",
      "sourceChargeRefForInterest" -> "XY002610150184",
      "documentLineItemDetails" -> Json.arr(
        Json.obj(
          "clearedAmountItem" -> 0.00,
          "clearingDate" -> "2020-06-30",
          "paymDateOrCredDueDate" -> "2020-04-24",
          "clearingReason" -> "C1"
        )
      )
    )
  )

  //scalastyle:off method.length
  private def schemeFSMaxSeqModel: Seq[SchemeFSDetail] = Seq(
    SchemeFSDetail(
      index = 1,
      chargeReference = "XY002610150184",
      chargeType = "Accounting for Tax Return",
      dueDate = Some(LocalDate.parse("2020-02-15")),
      totalAmount = 80000.00,
      amountDue = 1029.05,
      outstandingAmount = 56049.08,
      accruedInterestTotal = 100.05,
      stoodOverAmount = 25089.08,
      periodStartDate = Some(LocalDate.parse("2020-01-01")),
      periodEndDate = Some(LocalDate.parse("2020-03-31")),
      formBundleNumber = Some("222"),
      version = None,
      receiptDate = None,
      aftVersion = Some(1),
      sourceChargeRefForInterest = None,
      sourceChargeInfo = None,
      Seq(DocumentLineItemDetail(
        clearingReason = Some("C1"),
        clearingDate = Some(LocalDate.parse("2020-06-30")),
        paymDateOrCredDueDate = Some(LocalDate.parse("2020-04-24")),
        clearedAmountItem = BigDecimal(0.00))
      )
    ),
    SchemeFSDetail(
      index = 2,
      chargeReference = "XY002610150185",
      chargeType = "Accounting for Tax Return Interest",
      dueDate = Some(LocalDate.parse("2020-07-15")),
      totalAmount = 129.00,
      amountDue = 333.05,
      outstandingAmount = 78.08,
      accruedInterestTotal = 22.05,
      stoodOverAmount = 56.08,
      periodStartDate = Some(LocalDate.parse("2020-04-01")),
      periodEndDate = Some(LocalDate.parse("2020-06-30")),
      formBundleNumber = Some("123456789183"),
      version = None,
      receiptDate = None,
      aftVersion = Some(1),
      sourceChargeRefForInterest = Some("XY002610150184"),
      sourceChargeInfo = Some(
        SchemeSourceChargeInfo(
          index = 1,
          version = None,
          receiptDate = None,
          periodStartDate = Some(LocalDate.parse("2020-01-01")),
          periodEndDate = Some(LocalDate.parse("2020-03-31"))
        )
      ),
      Seq(DocumentLineItemDetail(
        clearingReason = Some("C1"),
        clearingDate = Some(LocalDate.parse("2020-06-30")),
        paymDateOrCredDueDate = Some(LocalDate.parse("2020-04-24")),
        clearedAmountItem = BigDecimal(0.00))
      )
    )
  )

  private val schemeFSWrapperModel: SchemeFS = SchemeFS(inhibitRefundSignal = true, schemeFSMaxSeqModel)

  private val schemeFSWrapperResponseMax: JsValue = Json.obj("accountHeaderDetails" -> Json.obj("inhibitRefundSignal" -> true)) ++
    Json.obj("documentHeaderDetails" -> schemeFSMaxSeqJson)
}


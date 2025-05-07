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
import models.enumeration.JourneyType
import models.{AFTOverview, AFTOverviewVersion}
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{times, verify, when}
import org.mockito.{ArgumentCaptor, Mockito}
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AsyncWordSpec
import org.scalatestplus.mockito.MockitoSugar
import play.api.http.Status
import play.api.http.Status._
import play.api.inject.bind
import play.api.inject.guice.GuiceableModule
import play.api.libs.json.Json
import play.api.mvc.RequestHeader
import play.api.test.FakeRequest
import repository._
import services.AFTService
import uk.gov.hmrc.http._
import utils.{JsonFileReader, WireMockHelper}

import java.time.LocalDate

class AFTConnectorSpec extends AsyncWordSpec with Matchers with WireMockHelper with JsonFileReader with MockitoSugar { // scalastyle:off magic.number

  private implicit lazy val hc: HeaderCarrier = HeaderCarrier()
  private implicit lazy val rh: RequestHeader = FakeRequest("", "")

  override protected def portConfigKey: String = "microservice.services.if-hod.port"

  private val mockAuditService = mock[AuditService]
  private val mockAftService = mock[AFTService]
  private val mockHeaderUtils = mock[HeaderUtils]

  private lazy val connector: AFTConnector = injector.instanceOf[AFTConnector]

  override protected def bindings: Seq[GuiceableModule] =
    Seq(
      bind[AuditService].toInstance(mockAuditService),
      bind[AFTService].toInstance(mockAftService),
      bind[HeaderUtils].toInstance(mockHeaderUtils),
      bind[AftBatchedDataCacheRepository].toInstance(mock[AftBatchedDataCacheRepository]),
      bind[AftOverviewCacheRepository].toInstance(mock[AftOverviewCacheRepository]),
      bind[FileUploadReferenceCacheRepository].toInstance(mock[FileUploadReferenceCacheRepository]),
      bind[FileUploadOutcomeRepository].toInstance(mock[FileUploadOutcomeRepository]),
      bind[FinancialInfoCacheRepository].toInstance(mock[FinancialInfoCacheRepository]),
      bind[FinancialInfoCreditAccessRepository].toInstance(mock[FinancialInfoCreditAccessRepository])
    )

  private val pstr = "test-pstr"
  private val startDt = "2020-01-01"
  private val endDate = "2021-06-30"
  private val aftSubmitUrl = s"/pension-online/$pstr/aft/return"
  private val getAftDetailsUrl = s"/pension-online/$pstr/aft/details"

  private val getAftOverviewUrl = s"/pension-online/reports/overview/pods/$pstr/AFT?fromDate=$startDt&toDate=$endDate"
  private val testCorrelationId = "testCorrelationId"
  private val overview1 = AFTOverview(LocalDate.of(2020, 4, 1), LocalDate.of(2020, 6, 30),
    tpssReportPresent = false,
    Some(AFTOverviewVersion(3, submittedVersionAvailable = false, compiledVersionAvailable = true)))
  private val overview2 = AFTOverview(LocalDate.of(2020, 7, 1), LocalDate.of(2020, 10, 31),
    tpssReportPresent = false,
    Some(AFTOverviewVersion(2, submittedVersionAvailable = true, compiledVersionAvailable = true)))
  private val aftOverview = Seq(overview1, overview2)
  private val journeyType = JourneyType.AFT_SUBMIT_RETURN.toString

  override def beforeAll(): Unit = {
    when(mockHeaderUtils.getCorrelationId).thenReturn(testCorrelationId)
    super.beforeAll()
  }


  "get aft details" must {

    "return successfully when ETMP has returned OK when original aftVersion header in header carrier" in {
      val hc: HeaderCarrier = HeaderCarrier()
      val response = Json.obj("test" -> "value")
      server.stubFor(
        get(urlEqualTo(getAftDetailsUrl))
          .withHeader("aftVersion", equalTo("001"))
          .willReturn(
            ok.withBody(Json.stringify(response))
          )
      )

      connector.getAftDetails(pstr, startDt, "001")(headerCarrier = hc, implicitly, implicitly) map { jsValue =>
        jsValue `mustBe` response
      }
    }
  }

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
      when(mockAftService.isChargeZeroedOut(any())).thenReturn(false)

      connector.fileAFTReturn(pstr, journeyType, data) map {
        _.status `mustBe` OK
      }
    }

    "send the FileAftReturn audit event when ETMP has returned OK" in {
      Mockito.reset(mockAuditService)
      val data = Json.obj(fields = "Id" -> "value")
      val successResponse = Json.obj("response" -> "success")
      server.stubFor(
        post(urlEqualTo(aftSubmitUrl))
          .withRequestBody(equalTo(Json.stringify(data)))
          .willReturn(
            ok.withBody(Json.stringify(successResponse))
          )
      )
      when(mockAftService.isChargeZeroedOut(any())).thenReturn(false)
      val eventCaptor = ArgumentCaptor.forClass(classOf[FileAftReturn])
      connector.fileAFTReturn(pstr, journeyType, data).map { _ =>
        verify(mockAuditService, times(1)).sendEvent(eventCaptor.capture())(any(), any())
        eventCaptor.getValue mustEqual FileAftReturn(pstr, journeyType, Status.OK, data, Some(successResponse))
      }
    }

    "send the FileAFTReturnOneChargeAndNoValue audit event when ETMP has returned OK and true passed into method" in {
      Mockito.reset(mockAuditService)
      val data = Json.obj(fields = "Id" -> "value")
      val successResponse = Json.obj("response" -> "success")
      server.stubFor(
        post(urlEqualTo(aftSubmitUrl))
          .withRequestBody(equalTo(Json.stringify(data)))
          .willReturn(
            ok.withBody(Json.stringify(successResponse))
          )
      )
      when(mockAftService.isChargeZeroedOut(any())).thenReturn(true)
      val eventCaptor = ArgumentCaptor.forClass(classOf[FileAftReturn])
      connector.fileAFTReturn(pstr, journeyType, data).map { _ =>
        verify(mockAuditService, times(2)).sendEvent(eventCaptor.capture())(any(), any())
        eventCaptor.getValue mustEqual FileAFTReturnOneChargeAndNoValue(pstr, journeyType, Status.OK, data, Some(successResponse))
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
      when(mockAftService.isChargeZeroedOut(any())).thenReturn(false)
      recoverToExceptionIf[BadRequestException] {
        connector.fileAFTReturn(pstr, journeyType, data)
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
        connector.fileAFTReturn(pstr, journeyType, data)
      } map {
        _.responseCode mustEqual NOT_FOUND
      }
    }

    "send the FileAftReturn audit event when ETMP has returned NotFoundException" in {
      Mockito.reset(mockAuditService)
      val data = Json.obj(fields = "Id" -> "value")
      server.stubFor(
        post(urlEqualTo(aftSubmitUrl))
          .withRequestBody(equalTo(Json.stringify(data)))
          .willReturn(
            notFound()
          )
      )
      val eventCaptor = ArgumentCaptor.forClass(classOf[FileAftReturn])

      recoverToExceptionIf[NotFoundException] {
        connector.fileAFTReturn(pstr, journeyType, data)
      } map { _ =>
        verify(mockAuditService, times(1)).sendEvent(eventCaptor.capture())(any(), any())
        eventCaptor.getValue mustEqual FileAftReturn(pstr, journeyType, Status.NOT_FOUND, data, None)
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
      recoverToExceptionIf[UpstreamErrorResponse](connector.fileAFTReturn(pstr, journeyType, data)) map {
        _.statusCode `mustBe` INTERNAL_SERVER_ERROR
      }
    }

    "send the FileAftReturn audit event when ETMP has returned Internal Server Error" in {
      Mockito.reset(mockAuditService)
      val data = Json.obj(fields = "Id" -> "value")
      server.stubFor(
        post(urlEqualTo(aftSubmitUrl))
          .withRequestBody(equalTo(Json.stringify(data)))
          .willReturn(
            serverError()
          )
      )
      when(mockAftService.isChargeZeroedOut(any())).thenReturn(false)
      val eventCaptor = ArgumentCaptor.forClass(classOf[FileAftReturn])
      recoverToExceptionIf[UpstreamErrorResponse](connector.fileAFTReturn(pstr, journeyType, data)) map { _ =>
        verify(mockAuditService, times(1)).sendEvent(eventCaptor.capture())(any(), any())
        eventCaptor.getValue mustEqual FileAftReturn(pstr, journeyType, Status.INTERNAL_SERVER_ERROR, data, None)
      }
    }
  }


  "getAftOverview" must {
    "return the seq of overviewDetails returned from the ETMP" in {
      val aftOverviewResponseJson = Json.arr(
        Json.obj(
          "periodStartDate" -> "2020-04-01",
          "periodEndDate" -> "2020-06-30",
          "numberOfVersions" -> 3,
          "submittedVersionAvailable" -> "No",
          "compiledVersionAvailable" -> "Yes"
        ),
        Json.obj(
          "periodStartDate" -> "2020-07-01",
          "periodEndDate" -> "2020-10-31",
          "numberOfVersions" -> 2,
          "submittedVersionAvailable" -> "Yes",
          "compiledVersionAvailable" -> "Yes"
        )
      )

      server.stubFor(
        get(urlEqualTo(getAftOverviewUrl))
          .willReturn(
            ok
              .withHeader("Content-Type", "application/json")
              .withBody(aftOverviewResponseJson.toString())
          )
      )
      connector.getAftOverview(pstr, startDt, endDate).map { response =>
        response.mustBe(aftOverview)
      }
    }

    "return a BadRequestException for BAD REQUEST - 400" in {
      server.stubFor(
        get(urlEqualTo(getAftOverviewUrl))
          .willReturn(
            badRequest
              .withHeader("Content-Type", "application/json")
              .withBody(errorResponse("INVALID_PSTR"))
          )
      )

      recoverToExceptionIf[BadRequestException] {
        connector.getAftOverview(pstr, startDt, endDate)
      } map { errorResponse =>
        errorResponse.responseCode mustEqual BAD_REQUEST
        errorResponse.message must include("INVALID_PSTR")
      }
    }

    "return a Seq.empty for NOT FOUND - 404 with response code NO_REPORT_FOUND" in {
      server.stubFor(
        get(urlEqualTo(getAftOverviewUrl))
          .willReturn(
            notFound
              .withBody(errorResponse("NO_REPORT_FOUND"))
          )
      )

      connector.getAftOverview(pstr, startDt, endDate) map { response =>
        response.mustEqual(Seq.empty)
      }
    }

    "return a Seq.empty for 404 with response code NO_REPORT_FOUND in a sequence of errors" in {
      server.stubFor(
        get(urlEqualTo(getAftOverviewUrl))
          .willReturn(
            notFound
              .withBody(seqErrorResponse("NO_REPORT_FOUND"))
          )
      )

      connector.getAftOverview(pstr, startDt, endDate) map { response =>
        response.mustEqual(Seq.empty)
      }
    }

    "return a NotFoundException for a 404 response code without NO_REPORT_FOUND in a sequence of errors" in {
      server.stubFor(
        get(urlEqualTo(getAftOverviewUrl))
          .willReturn(
            notFound
              .withBody(seqErrorResponse("SOME_OTHER_ERROR"))
          )
      )

      recoverToExceptionIf[NotFoundException] {
        connector.getAftOverview(pstr, startDt, endDate)
      } map { response =>
        response.responseCode mustEqual NOT_FOUND
        response.message must include("SOME_OTHER_ERROR")
      }
    }

    "return a NotFoundException for NOT FOUND - 404" in {
      server.stubFor(
        get(urlEqualTo(getAftOverviewUrl))
          .willReturn(
            notFound
              .withBody(errorResponse("NOT_FOUND"))
          )
      )

      recoverToExceptionIf[NotFoundException] {
        connector.getAftOverview(pstr, startDt, endDate)
      } map { response =>
        response.responseCode mustEqual NOT_FOUND
        response.message must include("NOT_FOUND")
      }
    }

    "throw Upstream4XX for FORBIDDEN - 403" in {

      server.stubFor(
        get(urlEqualTo(getAftOverviewUrl))
          .willReturn(
            forbidden
              .withBody(errorResponse("FORBIDDEN"))
          )
      )
      recoverToExceptionIf[UpstreamErrorResponse](connector.getAftOverview(pstr, startDt, endDate)) map {
        ex =>
          ex.statusCode mustBe FORBIDDEN
          ex.message must include("FORBIDDEN")
      }
    }

    "throw Upstream5XX for INTERNAL SERVER ERROR - 500" in {

      server.stubFor(
        get(urlEqualTo(getAftOverviewUrl))
          .willReturn(
            serverError
              .withBody(errorResponse("SERVER_ERROR"))
          )
      )

      recoverToExceptionIf[UpstreamErrorResponse](connector.getAftOverview(pstr, startDt, endDate)) map {
        ex =>
          ex.statusCode mustBe INTERNAL_SERVER_ERROR
          ex.message must include("SERVER_ERROR")
      }
    }
  }

  private def errorResponse(code: String): String = {
    Json.stringify(
      Json.obj(
        "code" -> code,
        "reason" -> s"Reason for $code"
      )
    )
  }

  private def seqErrorResponse(code: String): String = {
    Json.stringify(
      Json.obj(
        "failures" ->
          Json.arr(
            Json.obj(
              "code" -> code,
              "reason" -> s"Reason for $code"
            )
          )
      )
    )
  }
}


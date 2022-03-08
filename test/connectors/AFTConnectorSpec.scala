/*
 * Copyright 2022 HM Revenue & Customs
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
import models.{AFTOverview, AFTOverviewVersion, AFTVersion}
import org.mockito.ArgumentMatchers.any
import org.mockito.{ArgumentCaptor, Mockito, MockitoSugar}
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AsyncWordSpec
import play.api.http.Status
import play.api.http.Status._
import play.api.inject.bind
import play.api.inject.guice.GuiceableModule
import play.api.libs.json.{JsValue, Json}
import play.api.mvc.RequestHeader
import play.api.test.FakeRequest
import services.AFTService
import uk.gov.hmrc.http._
import utils.{JsonFileReader, WireMockHelper}

import java.time.LocalDate

class AFTConnectorSpec extends AsyncWordSpec with Matchers with WireMockHelper with JsonFileReader with MockitoSugar {

  import AFTConnectorSpec._

  private implicit lazy val hc: HeaderCarrier = HeaderCarrier()
  private implicit lazy val rh: RequestHeader = FakeRequest("", "")

  override protected def portConfigKey: String = "microservice.services.des-hod.port"

  private val mockAuditService = mock[AuditService]
  private val mockAftService = mock[AFTService]
  private val mockHeaderUtils = mock[HeaderUtils]

  private lazy val connector: AFTConnector = injector.instanceOf[AFTConnector]

  override protected def bindings: Seq[GuiceableModule] =
    Seq(
      bind[AuditService].toInstance(mockAuditService),
      bind[AFTService].toInstance(mockAftService),
      bind[HeaderUtils].toInstance(mockHeaderUtils)
    )

  private val pstr = "test-pstr"
  private val startDt = "2020-01-01"
  private val endDate = "2021-06-30"
  private val aftVersion = "1"
  private val fbNumber = "123456789123"
  private val aftSubmitUrl = s"/pension-online/pstr/$pstr/aft/return"
  private val getAftUrl = s"/pension-online/aft-return/$pstr?startDate=$startDt&aftVersion=$aftVersion"
  private val getAftFbNumberUrl = s"/pension-online/aft-return/$pstr?fbNumber=$fbNumber"
  private val getAftVersionsUrl = s"/pension-online/reports/$pstr/AFT/versions?startDate=$startDt"
  private val getAftOverviewUrl = s"/pension-online/reports/overview/$pstr/AFT?fromDate=$startDt&toDate=$endDate"
  private val testCorrelationId = "testCorrelationId"
  private val overview1 = AFTOverview(LocalDate.of(2020, 4, 1), LocalDate.of(2020, 6, 30),
    tpssReportPresent = false,
    Some(AFTOverviewVersion(3, false, true)))
  private val overview2 = AFTOverview(LocalDate.of(2020, 7, 1), LocalDate.of(2020, 10, 31),
    tpssReportPresent = false,
    Some(AFTOverviewVersion(2, true, true)))
  private val aftOverview = Seq(overview1, overview2)
  private val journeyType = JourneyType.AFT_SUBMIT_RETURN.toString

  override def beforeEach(): Unit = {
    when(mockHeaderUtils.getCorrelationId).thenReturn(testCorrelationId)
    super.beforeEach()
  }

  "fileAFTReturnDES" must {
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

      connector.fileAFTReturnDES(pstr, journeyType, data) map {
        _.status mustBe OK
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
      connector.fileAFTReturnDES(pstr, journeyType, data).map { _ =>
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
      connector.fileAFTReturnDES(pstr, journeyType, data).map { _ =>
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
        connector.fileAFTReturnDES(pstr, journeyType, data)
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
        connector.fileAFTReturnDES(pstr, journeyType, data)
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
        connector.fileAFTReturnDES(pstr, journeyType, data)
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
      recoverToExceptionIf[UpstreamErrorResponse](connector.fileAFTReturnDES(pstr, journeyType, data)) map {
        _.statusCode mustBe INTERNAL_SERVER_ERROR
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
      recoverToExceptionIf[UpstreamErrorResponse](connector.fileAFTReturnDES(pstr, journeyType, data)) map { _ =>
        verify(mockAuditService, times(1)).sendEvent(eventCaptor.capture())(any(), any())
        eventCaptor.getValue mustEqual FileAftReturn(pstr, journeyType, Status.INTERNAL_SERVER_ERROR, data, None)
      }
    }
  }

  "getAftOverviewDES" must {
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
      connector.getAftOverviewDES(pstr, startDt, endDate).map { response =>
        response mustBe aftOverview
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
        connector.getAftOverviewDES(pstr, startDt, endDate)
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

      connector.getAftOverviewDES(pstr, startDt, endDate) map { response =>
        response mustEqual Seq.empty
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
        connector.getAftOverviewDES(pstr, startDt, endDate)
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
      recoverToExceptionIf[UpstreamErrorResponse](connector.getAftOverviewDES(pstr, startDt, endDate)) map {
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

      recoverToExceptionIf[UpstreamErrorResponse](connector.getAftOverviewDES(pstr, startDt, endDate)) map {
        ex =>
          ex.statusCode mustBe INTERNAL_SERVER_ERROR
          ex.message must include("SERVER_ERROR")
      }
    }
  }


  "getAftDetails" must {
    "return user answer json when successful response returned from ETMP" in {
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

    "return user answer json with fbNumber when successful response returned from ETMP" in {
      server.stubFor(
        get(urlEqualTo(getAftFbNumberUrl))
          .willReturn(
            ok
              .withHeader("Content-Type", "application/json")
              .withBody(etmpAFTDetailsResponse.toString())
          )
      )

      connector.getAftDetails(pstr, fbNumber).map { response =>
        response mustBe etmpAFTDetailsResponse
      }
    }

    "send AftGet audit event when successful response returned from ETMP" in {
      Mockito.reset(mockAuditService)
      server.stubFor(
        get(urlEqualTo(getAftUrl))
          .willReturn(
            ok
              .withHeader("Content-Type", "application/json")
              .withBody(etmpAFTDetailsResponse.toString())
          )
      )
      val eventCaptor = ArgumentCaptor.forClass(classOf[GetAFTDetails])
      connector.getAftDetails(pstr, startDt, aftVersion).map { _ =>
        verify(mockAuditService, times(1)).sendEvent(eventCaptor.capture())(any(), any())
        eventCaptor.getValue mustEqual audit.GetAFTDetails(pstr, startDt, Status.OK, Some(etmpAFTDetailsResponse))
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

    "send AftGet audit event when a 400 INVALID_PSTR response returned from ETMP" in {
      Mockito.reset(mockAuditService)
      server.stubFor(
        get(urlEqualTo(getAftUrl))
          .willReturn(
            badRequest
              .withHeader("Content-Type", "application/json")
              .withBody(errorResponse("INVALID_PSTR"))
          )
      )
      val eventCaptor = ArgumentCaptor.forClass(classOf[GetAFTDetails])
      recoverToExceptionIf[BadRequestException] {
        connector.getAftDetails(pstr, startDt, aftVersion)
      } map { _ =>
        verify(mockAuditService, times(1)).sendEvent(eventCaptor.capture())(any(), any())
        eventCaptor.getValue mustEqual audit.GetAFTDetails(pstr, startDt, Status.BAD_REQUEST, None)
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
      recoverToExceptionIf[UpstreamErrorResponse](connector.getAftDetails(pstr, startDt, aftVersion)) map {
        ex =>
          ex.statusCode mustBe FORBIDDEN
          ex.message must include("FORBIDDEN")
      }
    }

    "send AftGet audit event when a Upstream4XX for server unavailable - 403 is thrown from ETMP" in {
      Mockito.reset(mockAuditService)
      server.stubFor(
        get(urlEqualTo(getAftUrl))
          .willReturn(
            forbidden
              .withBody(errorResponse("FORBIDDEN"))
          )
      )
      val eventCaptor = ArgumentCaptor.forClass(classOf[GetAFTDetails])
      recoverToExceptionIf[UpstreamErrorResponse](connector.getAftDetails(pstr, startDt, aftVersion)) map {
        _ =>
          verify(mockAuditService, times(1)).sendEvent(eventCaptor.capture())(any(), any())
          eventCaptor.getValue mustEqual audit.GetAFTDetails(pstr, startDt, Status.FORBIDDEN, None)
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

      recoverToExceptionIf[UpstreamErrorResponse](connector.getAftDetails(pstr, startDt, aftVersion)) map {
        ex =>
          ex.statusCode mustBe INTERNAL_SERVER_ERROR
          ex.message must include("SERVER_ERROR")
          ex.reportAs mustBe BAD_GATEWAY
      }
    }
  }

  "getAftVersions" must {
    "return the seq of versions returned from the ETMP" in {
      val aftVersionsResponseJson = Json.arr(
        Json.obj(fields = "reportVersion" -> 1,
          "reportStatus" -> "submitted",
          "compilationOrSubmissionDate" -> "2019-10-17T09:31:47Z"),
        Json.obj(fields = "reportVersion" -> 2,
          "reportStatus" -> "compiled",
          "compilationOrSubmissionDate" -> "2019-10-17T09:31:47Z"))
      server.stubFor(
        get(urlEqualTo(getAftVersionsUrl))
          .willReturn(
            ok
              .withHeader("Content-Type", "application/json")
              .withBody(aftVersionsResponseJson.toString())
          )
      )
      connector.getAftVersions(pstr, startDt).map { response =>
        response mustBe Seq(
          AFTVersion(1, LocalDate.of(2019, 10, 17), "submitted"),
          AFTVersion(2, LocalDate.of(2019, 10, 17), "compiled"))
      }
    }

    "send the GetReportVersions audit event when the success response returned from ETMP" in {
      Mockito.reset(mockAuditService)
      val aftVersionsResponseJson = Json.arr(Json.obj(fields = "reportVersion" -> 1,
        "reportStatus" -> "submitted",
        "compilationOrSubmissionDate" -> "2019-10-17T09:31:47Z"),
        Json.obj(fields = "reportVersion" -> 2,
          "reportStatus" -> "compiled",
          "compilationOrSubmissionDate" -> "2019-10-17T09:31:47Z"))

      server.stubFor(
        get(urlEqualTo(getAftVersionsUrl))
          .willReturn(
            ok
              .withHeader("Content-Type", "application/json")
              .withBody(aftVersionsResponseJson.toString())
          )
      )
      val eventCaptor = ArgumentCaptor.forClass(classOf[GetAFTVersions])
      connector.getAftVersions(pstr, startDt).map { _ =>
        verify(mockAuditService, times(1)).sendEvent(eventCaptor.capture())(any(), any())
        eventCaptor.getValue mustEqual audit.GetAFTVersions(pstr, startDt, Status.OK, Some(aftVersionsResponseJson))
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

      recoverToExceptionIf[NotFoundException] {
        connector.getAftVersions(pstr, startDt)
      } map { response =>
        response.responseCode mustEqual NOT_FOUND
        response.message must include("NOT_FOUND")
      }
    }

    "send the GetReportVersions audit event when the NOT FOUND - 404 response returned from ETMP" in {
      Mockito.reset(mockAuditService)
      server.stubFor(
        get(urlEqualTo(getAftVersionsUrl))
          .willReturn(
            notFound
              .withBody(errorResponse("NOT_FOUND"))
          )
      )

      val eventCaptor = ArgumentCaptor.forClass(classOf[GetAFTVersions])
      recoverToExceptionIf[NotFoundException] {
        connector.getAftVersions(pstr, startDt)
      } map { response =>
        response.responseCode mustEqual NOT_FOUND
        response.message must include("NOT_FOUND")
        verify(mockAuditService, times(1)).sendEvent(eventCaptor.capture())(any(), any())
        eventCaptor.getValue mustEqual audit.GetAFTVersions(pstr, startDt, Status.NOT_FOUND, None)
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
      recoverToExceptionIf[UpstreamErrorResponse](connector.getAftVersions(pstr, startDt)) map {
        ex =>
          ex.statusCode mustBe FORBIDDEN
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

      recoverToExceptionIf[UpstreamErrorResponse](connector.getAftVersions(pstr, startDt)) map {
        ex =>
          ex.statusCode mustBe INTERNAL_SERVER_ERROR
          ex.message must include("SERVER_ERROR")
      }
    }

    "send the GetReportVersions audit event when the INTERNAL SERVER ERROR - 500 response returned from ETMP" in {
      Mockito.reset(mockAuditService)
      server.stubFor(
        get(urlEqualTo(getAftVersionsUrl))
          .willReturn(
            serverError
              .withBody(errorResponse("SERVER_ERROR"))
          )
      )
      val eventCaptor = ArgumentCaptor.forClass(classOf[GetAFTVersions])
      recoverToExceptionIf[UpstreamErrorResponse](connector.getAftVersions(pstr, startDt)) map {
        _ =>
          verify(mockAuditService, times(1)).sendEvent(eventCaptor.capture())(any(), any())
          eventCaptor.getValue mustEqual audit.GetAFTVersions(pstr, startDt, Status.INTERNAL_SERVER_ERROR, None)
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

object AFTConnectorSpec {
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

class AFTConnectorIFSpec extends AsyncWordSpec with Matchers with WireMockHelper with JsonFileReader with MockitoSugar {

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
      bind[HeaderUtils].toInstance(mockHeaderUtils)
    )

  private val pstr = "test-pstr"
  private val startDt = "2020-01-01"
  private val endDate = "2021-06-30"
  private val aftVersion = "1"
  private val fbNumber = "123456789123"
  private val aftSubmitUrl = s"/pension-online/pstr/$pstr/aft/return"
  private val getAftUrl = s"/pension-online/aft-return/$pstr?startDate=$startDt&aftVersion=$aftVersion"
  private val getAftFbNumberUrl = s"/pension-online/aft-return/$pstr?fbNumber=$fbNumber"
  private val getAftVersionsUrl = s"/pension-online/reports/$pstr/AFT/versions?startDate=$startDt"
  private val getAftOverviewUrl = s"/pension-online/reports/overview/$pstr/AFT?fromDate=$startDt&toDate=$endDate"
  private val testCorrelationId = "testCorrelationId"
  private val overview1 = AFTOverview(LocalDate.of(2020, 4, 1), LocalDate.of(2020, 6, 30),
    tpssReportPresent = false,
    Some(AFTOverviewVersion(3, false, true)))
  private val overview2 = AFTOverview(LocalDate.of(2020, 7, 1), LocalDate.of(2020, 10, 31),
    tpssReportPresent = false,
    Some(AFTOverviewVersion(2, true, true)))
  private val aftOverview = Seq(overview1, overview2)
  private val journeyType = JourneyType.AFT_SUBMIT_RETURN.toString

  override def beforeEach(): Unit = {
    when(mockHeaderUtils.getCorrelationId).thenReturn(testCorrelationId)
    super.beforeEach()
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
        _.status mustBe OK
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
        _.statusCode mustBe INTERNAL_SERVER_ERROR
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
        response mustBe aftOverview
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
        response mustEqual Seq.empty
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
        response mustEqual Seq.empty
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


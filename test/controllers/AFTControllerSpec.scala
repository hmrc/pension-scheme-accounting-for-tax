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

import java.time.LocalDate

import connectors.DesConnector
import models.enumeration.JourneyType
import models.{AFTOverview, AFTVersion}
import org.mockito.Matchers
import org.mockito.Matchers.any
import org.mockito.Mockito._
import org.scalatest.{AsyncWordSpec, BeforeAndAfter, MustMatchers}
import org.scalatestplus.mockito.MockitoSugar
import play.api.Application
import play.api.inject.bind
import play.api.inject.guice.{GuiceApplicationBuilder, GuiceableModule}
import play.api.libs.json._
import play.api.test.FakeRequest
import play.api.test.Helpers._
import repository.AftDataCacheRepository
import services.AFTService
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.http._
import utils.JsonFileReader

import scala.concurrent.Future

class AFTControllerSpec extends AsyncWordSpec with MustMatchers with MockitoSugar with BeforeAndAfter with JsonFileReader {

  import AFTControllerSpec._

  implicit val hc: HeaderCarrier = HeaderCarrier()

  private val fakeRequest = FakeRequest("GET", "/")
  private val mockDesConnector = mock[DesConnector]
  private val mockAftService = mock[AFTService]
  private val authConnector: AuthConnector = mock[AuthConnector]
  private val mockDataCacheRepository = mock[AftDataCacheRepository]

  private val nonZeroCurrencyValue = BigDecimal(44.33)

  private val version1 = AFTVersion(1, LocalDate.now(), "submitted")
  private val version2 = AFTVersion(2, LocalDate.now(), "compiled")
  private val versions = Seq(version1, version2)
  private val journeyType = JourneyType.AFT_SUBMIT_RETURN

  val modules: Seq[GuiceableModule] =
    Seq(
      bind[AuthConnector].toInstance(authConnector),
      bind[DesConnector].toInstance(mockDesConnector),
      bind[AFTService].toInstance(mockAftService),
      bind[AftDataCacheRepository].toInstance(mockDataCacheRepository)
    )

  val application: Application = new GuiceApplicationBuilder()
    .configure(conf = "auditing.enabled" -> false, "metrics.enabled" -> false, "metrics.jvm" -> false).
    overrides(modules: _*).build()

  private def controllerForGetAftVersions: AFTController = {

    val controller = application.injector.instanceOf[AFTController]
    when(mockDesConnector.getAftVersions(Matchers.eq(pstr), Matchers.eq(startDt))(any(), any(), any())).thenReturn(
      Future.successful(versions))
    controller
  }

  before {
    reset(mockDesConnector, mockAftService, authConnector)
    when(authConnector.authorise[Option[String]](any(), any())(any(), any())) thenReturn Future.successful(Some("Ext-137d03b9-d807-4283-a254-fb6c30aceef1"))
  }

  "fileReturn" must {
    "return OK when valid response from DES" in {

      val controller = application.injector.instanceOf[AFTController]

      when(mockDesConnector.fileAFTReturn(any(), any(), any())(any(), any(), any()))
        .thenReturn(Future.successful(HttpResponse(OK, fileAFTUaRequestJson.toString)))

      val result = controller.fileReturn(journeyType)(fakeRequest.withJsonBody(fileAFTUaRequestJson).withHeaders(
        newHeaders = "pstr" -> pstr))
      status(result) mustBe OK
    }

    "throw Upstream5XXResponse on Internal Server Error from DES" in {

      val controller = application.injector.instanceOf[AFTController]

      when(mockDesConnector.fileAFTReturn(any(), any(), any())(any(), any(), any()))
        .thenReturn(Future.failed(UpstreamErrorResponse(message = "Internal Server Error", INTERNAL_SERVER_ERROR, INTERNAL_SERVER_ERROR)))

      recoverToExceptionIf[UpstreamErrorResponse] {
        controller.fileReturn(journeyType)(fakeRequest.withJsonBody(fileAFTUaRequestJson).
          withHeaders(newHeaders = "pstr" -> pstr))
      } map {
        _.statusCode mustBe INTERNAL_SERVER_ERROR
      }
    }

    "return OK when valid response from DES for payload with only one member based charge and zero value" in {

      val controller = application.injector.instanceOf[AFTController]
      val jsonPayload = jsonOneMemberZeroValue

      when(mockDesConnector.fileAFTReturn(any(), any(), any())(any(), any(), any()))
        .thenReturn(Future.successful(HttpResponse(OK, jsonPayload.toString)))
      val result = controller.fileReturn(journeyType)(fakeRequest.withJsonBody(jsonPayload).
        withHeaders(newHeaders = "pstr" -> pstr))
      status(result) mustBe OK
    }
  }

  "getVersions" must {

    "return OK with the version if there is only version" in {

      val controller = application.injector.instanceOf[AFTController]

      when(mockDesConnector.getAftVersions(Matchers.eq(pstr), Matchers.eq(startDt))(any(), any(), any())).thenReturn(
        Future.successful(Seq(AFTVersion(1, LocalDate.now(), "submitted"))))
      when(mockDesConnector.getAftDetails(Matchers.eq(pstr), Matchers.eq(startDt), Matchers.eq("1"))(any(), any(), any())).thenReturn(
        Future.successful(createAFTDetailsResponse(chargeSectionWithValue(nonZeroCurrencyValue)))
      )
      when(mockAftService.isChargeZeroedOut(any())).thenReturn(false)

      val result = controller.getVersions()(fakeRequest.withHeaders(newHeaders = "pstr" -> pstr, "startDate" -> startDt))

      status(result) mustBe OK
      contentAsJson(result) mustBe Json.arr(Json.toJson(version1))
    }

    "return OK with the versions if more than one versions are present" in {
      val result = controllerForGetAftVersions
        .getVersions()(fakeRequest.withHeaders(
          newHeaders = "pstr" -> pstr,
          "startDate" -> startDt
        ))

      status(result) mustBe OK
      contentAsJson(result) mustBe Json.toJson(Seq(version1, version2))
    }

    "throw BadRequestException when PSTR is not present in the header" in {

      val controller = application.injector.instanceOf[AFTController]

      recoverToExceptionIf[BadRequestException] {
        controller.getVersions()(fakeRequest.withHeaders(newHeaders = "startDate" -> startDt))
      } map { response =>
        response.responseCode mustBe BAD_REQUEST
        response.message must include("Bad Request with missing PSTR/Quarter Start Date")
      }
    }

    "throw Upstream5xxResponse when UpStream5XXResponse with INTERNAL_SERVER_ERROR returned from Des" in {

      val controller = application.injector.instanceOf[AFTController]

      when(mockDesConnector.getAftVersions(Matchers.eq(pstr), Matchers.eq(startDt))(any(), any(), any())).thenReturn(
        Future.failed(UpstreamErrorResponse(errorResponse("INTERNAL SERVER ERROR"), INTERNAL_SERVER_ERROR, INTERNAL_SERVER_ERROR)))

      recoverToExceptionIf[UpstreamErrorResponse] {
        controller.getVersions()(fakeRequest.withHeaders(newHeaders = "startDate" -> startDt, "pstr" -> pstr))
      } map { response =>
        response.statusCode mustBe INTERNAL_SERVER_ERROR
        response.getMessage must include("INTERNAL SERVER ERROR")
        response.reportAs mustBe INTERNAL_SERVER_ERROR
      }
    }
  }

  "getAftDetails" must {

    "return OK when the details are returned based on pstr, start date and AFT version" in {

      val controller = application.injector.instanceOf[AFTController]

      when(mockDesConnector.getAftDetails(Matchers.eq(pstr), Matchers.eq(startDt), Matchers.eq(aftVer))(any(), any(), any())).thenReturn(
        Future.successful(etmpAFTDetailsResponse))

      val result = controller.getDetails()(fakeRequestForGetDetails)

      status(result) mustBe OK
      contentAsJson(result) mustBe transformedAftDEtailsUAJson
    }

    "throw BadRequestException when PSTR is not present in the header" in {

      val controller = application.injector.instanceOf[AFTController]

      recoverToExceptionIf[BadRequestException] {
        controller.getDetails()(fakeRequest.withHeaders(("startDate", startDt), ("aftVersion", aftVer)))
      } map { response =>
        response.responseCode mustBe BAD_REQUEST
        response.getMessage mustBe "Bad Request with missing PSTR/Quarter Start Date"
      }
    }

    "throw BadRequestException when bad request with INVALID_START_DATE returned from Des" in {

      val controller = application.injector.instanceOf[AFTController]

      when(mockDesConnector.getAftDetails(Matchers.eq(pstr), Matchers.eq(startDt), Matchers.eq(aftVer))(any(), any(), any())).thenReturn(
        Future.failed(new BadRequestException(errorResponse("INVALID_START_DATE"))))

      recoverToExceptionIf[BadRequestException] {
        controller.getDetails()(fakeRequestForGetDetails)
      } map { response =>
        response.responseCode mustBe BAD_REQUEST
        response.getMessage mustBe errorResponse("INVALID_START_DATE")
      }
    }

    "throw Upstream4xxResponse when UpStream4XXResponse returned from Des" in {

      val controller = application.injector.instanceOf[AFTController]

      when(mockDesConnector.getAftDetails(Matchers.eq(pstr), Matchers.eq(startDt), Matchers.eq(aftVer))(any(), any(), any())).thenReturn(
        Future.failed(UpstreamErrorResponse(errorResponse("NOT_FOUND"), NOT_FOUND, NOT_FOUND)))

      recoverToExceptionIf[UpstreamErrorResponse] {
        controller.getDetails()(fakeRequestForGetDetails)
      } map { response =>
        response.statusCode mustBe NOT_FOUND
        response.getMessage mustBe errorResponse("NOT_FOUND")
      }
    }

    "throw Upstream5xxResponse when UpStream5XXResponse with INTERNAL_SERVER_ERROR returned from Des" in {

      val controller = application.injector.instanceOf[AFTController]

      when(mockDesConnector.getAftDetails(Matchers.eq(pstr), Matchers.eq(startDt), Matchers.eq(aftVer))(any(), any(), any())).thenReturn(
        Future.failed(UpstreamErrorResponse(errorResponse("INTERNAL_SERVER_ERROR"), INTERNAL_SERVER_ERROR, INTERNAL_SERVER_ERROR)))

      recoverToExceptionIf[UpstreamErrorResponse] {
        controller.getDetails()(fakeRequestForGetDetails)
      } map { response =>
        response.statusCode mustBe INTERNAL_SERVER_ERROR
        response.getMessage mustBe errorResponse("INTERNAL_SERVER_ERROR")
      }
    }

    "throw generic exception when any other exception returned from Des" in {

      val controller = application.injector.instanceOf[AFTController]

      when(mockDesConnector.getAftDetails(Matchers.eq(pstr), Matchers.eq(startDt), Matchers.eq(aftVer))(any(), any(), any())).thenReturn(
        Future.failed(new Exception("Generic Exception")))

      recoverToExceptionIf[Exception] {
        controller.getDetails()(fakeRequestForGetDetails)
      } map { response =>
        response.getMessage mustBe "Generic Exception"
      }
    }
  }

  "getIsChargeNonZero" must {

    "return true when a charge is zeroed out" in {

      val controller = application.injector.instanceOf[AFTController]

      when(mockDesConnector.getAftDetails(Matchers.eq(pstr), Matchers.eq(startDt), Matchers.eq(aftVer))(any(), any(), any())).thenReturn(
        Future.successful(etmpAFTDetailsResponse))

      val result = controller.getIsChargeNonZero()(fakeRequestForGetDetails)

      status(result) mustBe OK
      contentAsJson(result) mustBe JsBoolean(true)
    }

    "return false when a charge has positive value" in {

      val controller = application.injector.instanceOf[AFTController]

      when(mockDesConnector.getAftDetails(Matchers.eq(pstr), Matchers.eq(startDt), Matchers.eq(aftVer))(any(), any(), any())).thenReturn(
        Future.successful(etmpAFTDetailsResponse))
      when(mockAftService.isChargeZeroedOut(any())).thenReturn(true)

      val result = controller.getIsChargeNonZero()(fakeRequestForGetDetails)

      status(result) mustBe OK
      contentAsJson(result) mustBe JsBoolean(false)
    }

  }

  "getOverview" must {

    "return OK with the Seq of overview details when the details are returned based on pstr start date and end date" in {

      val controller = application.injector.instanceOf[AFTController]

      when(mockDesConnector.getAftOverview(Matchers.eq(pstr), Matchers.eq(startDt), Matchers.eq(endDate))(any(), any()))
        .thenReturn(Future.successful(aftOverview))

      val result = controller.getOverview()(fakeRequest
        .withHeaders(newHeaders = "pstr" -> pstr, "startDate" -> startDt, "endDate" -> endDate))

      status(result) mustBe OK
      contentAsJson(result) mustBe aftOverviewResponseJson
    }

    "throw BadRequestException when endDate is not present in the header" in {

      val controller = application.injector.instanceOf[AFTController]

      recoverToExceptionIf[BadRequestException] {
        controller.getOverview()(fakeRequest.withHeaders(newHeaders = "pstr" -> pstr, "startDate" -> startDt))
      } map { response =>
        response.responseCode mustBe BAD_REQUEST
        response.message must include("Bad Request with no endDate")
      }
    }

    "throw Upstream5xxResponse when UpStream5XXResponse with INTERNAL_SERVER_ERROR returned from Des" in {

      val controller = application.injector.instanceOf[AFTController]

      when(mockDesConnector.getAftVersions(Matchers.eq(pstr), Matchers.eq(startDt))(any(), any(), any())).thenReturn(
        Future.failed(UpstreamErrorResponse(errorResponse("INTERNAL SERVER ERROR"), INTERNAL_SERVER_ERROR, INTERNAL_SERVER_ERROR)))

      recoverToExceptionIf[UpstreamErrorResponse] {
        controller.getVersions()(fakeRequest.withHeaders(newHeaders = "pstr" -> pstr, "startDate" -> startDt, "endDate" -> endDate))
      } map { response =>
        response.statusCode mustBe INTERNAL_SERVER_ERROR
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
  private val endDate = "2020-12-31"
  private val aftVer = "99"
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
        "amendedVersion" -> 1,
        "totalAmount" -> 200.02,
        "dateRegiWithdrawn" -> "1980-02-29"
      )
    )
  )

  private val transformedAftDEtailsUAJson = Json.obj(
    "aftStatus" -> "Compiled",
    "quarter" -> Json.obj(
      "startDate" -> "2020-02-29",
      "endDate" -> "2020-05-29"
    ),
    "pstr" -> "12345678AB",
    "schemeName" -> "PSTR Scheme",
    "chargeFDetails" -> Json.obj(
      "chargeDetails" -> Json.obj(
      "totalAmount" -> 200.02,
      "deRegistrationDate" -> "1980-02-29"
      ),
      "amendedVersion" -> 1
    )
  )

  private def createAFTDetailsResponse(chargeSection: JsObject): JsObject = Json.obj(
    "schemeDetails" -> Json.obj(
      "pstr" -> "12345678AB",
      "schemeName" -> "PSTR Scheme"
    ),
    "aftDetails" -> Json.obj(
      "aftStatus" -> "Compiled",
      "quarterStartDate" -> "2020-02-29",
      "quarterEndDate" -> "2020-05-29"
    ),
    "chargeDetails" -> chargeSection
  )

  val aftOverviewResponseJson: JsArray = Json.arr(
    Json.obj(
      "periodStartDate"-> "2020-04-01",
      "periodEndDate"-> "2020-06-30",
      "numberOfVersions"-> 3,
      "submittedVersionAvailable"-> false,
      "compiledVersionAvailable"-> true
    ),
    Json.obj(
      "periodStartDate"-> "2020-07-01",
      "periodEndDate"-> "2020-10-31",
      "numberOfVersions"-> 2,
      "submittedVersionAvailable"-> true,
      "compiledVersionAvailable"-> true
    )
  )

  private val overview1 = AFTOverview(
      LocalDate.of(2020, 4, 1),
      LocalDate.of(2020, 6, 30),
      3,
      submittedVersionAvailable = false,
      compiledVersionAvailable = true)

  private val overview2 = AFTOverview(
      LocalDate.of(2020, 7, 1),
      LocalDate.of(2020, 10, 31),
      2,
      submittedVersionAvailable = true,
      compiledVersionAvailable = true)

  private val aftOverview = Seq(overview1, overview2)

  private def chargeSectionWithValue(currencyValue: BigDecimal): JsObject =
    Json.obj(
      "chargeADetails" -> Json.obj(
        "totalAmount" -> currencyValue
      )
    )


  private val fakeRequestForGetDetails = FakeRequest("GET", "/").withHeaders(("pstr", pstr), ("startDate", startDt), ("aftVersion", aftVer))
  private val json =
    """{
      |  "aftStatus": "Compiled",
      |  "quarter": {
      |    "startDate": "2019-01-01",
      |    "endDate": "2019-03-31"
      |  },
      |  "chargeFDetails": {
      |    "chargeDetails": {
      |      "totalAmount": 200.02,
      |      "deRegistrationDate": "1980-02-29"
      |    }
      |  }
      |}""".stripMargin
  private val fileAFTUaRequestJson = Json.parse(json)

  private val jsonOneMemberZeroValue = Json.parse(
    """{
      |  "aftStatus": "Compiled",
      |  "quarter": {
      |       "startDate": "2019-01-01",
      |       "endDate": "2019-03-31"
      |  },
      |  "chargeCDetails": {
      |         "employers" : [
      |                {
      |                    "sponsoringIndividualDetails" : {
      |                        "firstName" : "asas",
      |                        "lastName" : "asa",
      |                        "nino" : "CS121212C"
      |                    },
      |                    "whichTypeOfSponsoringEmployer" : "individual",
      |                    "sponsoringEmployerAddress" : {
      |                        "line1" : "asas",
      |                        "line2" : "asas",
      |                        "country" : "FR"
      |                    },
      |                    "chargeDetails" : {
      |                        "paymentDate" : "2000-01-01",
      |                        "amountTaxDue" : 0
      |                    }
      |                }
      |            ],
      |            "totalChargeAmount" : 0
      |  }
      |}""".stripMargin)

}

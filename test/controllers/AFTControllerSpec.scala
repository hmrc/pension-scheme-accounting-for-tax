/*
 * Copyright 2023 HM Revenue & Customs
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

import audit.{AuditService, FileAftReturnSchemaValidator}
import connectors.AFTConnector
import models.enumeration.JourneyType
import models.{AFTOverview, AFTOverviewVersion, AFTVersion}
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{reset, times, verify, when}
import org.mockito.{ArgumentCaptor, ArgumentMatchers, Mockito}
import org.scalatest.BeforeAndAfter
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AsyncWordSpec
import org.scalatestplus.mockito.MockitoSugar
import play.api.Application
import play.api.inject.bind
import play.api.inject.guice.{GuiceApplicationBuilder, GuiceableModule}
import play.api.libs.json._
import play.api.test.FakeRequest
import play.api.test.Helpers._
import repository._
import services.AFTService
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.http._
import utils.{ErrorReport, JSONPayloadSchemaValidator, JsonFileReader}

import java.time.LocalDate
import java.util.UUID
import scala.concurrent.Future

class AFTControllerSpec extends AsyncWordSpec with Matchers with MockitoSugar with BeforeAndAfter with JsonFileReader {

  import AFTControllerSpec._

  implicit val hc: HeaderCarrier = HeaderCarrier()

  private val mockAuditService = mock[AuditService]

  private val uuid = UUID.randomUUID().toString
  private val fakeRequest = FakeRequest("GET", "/")
  private val mockDesConnector = mock[AFTConnector]
  private val mockAftService = mock[AFTService]
  private val authConnector: AuthConnector = mock[AuthConnector]
  private val mockAftOverviewCacheRepository = mock[AftOverviewCacheRepository]
  private val mockJSONPayloadSchemaValidator = mock[JSONPayloadSchemaValidator]

  private val nonZeroCurrencyValue = BigDecimal(44.33)

  private val version1 = AFTVersion(1, LocalDate.now(), "submitted")
  private val version2 = AFTVersion(2, LocalDate.now(), "compiled")
  private val versions = Seq(version1, version2)
  private val journeyType = JourneyType.AFT_SUBMIT_RETURN

  val modules: Seq[GuiceableModule] =
    Seq(
      bind[AuditService].toInstance(mockAuditService),
      bind[AuthConnector].toInstance(authConnector),
      bind[AFTConnector].toInstance(mockDesConnector),
      bind[AFTService].toInstance(mockAftService),
      bind[AftOverviewCacheRepository].toInstance(mockAftOverviewCacheRepository),
      bind[JSONPayloadSchemaValidator].toInstance(mockJSONPayloadSchemaValidator),
      bind[AftBatchedDataCacheRepository].toInstance(mock[AftBatchedDataCacheRepository]),
      bind[FileUploadReferenceCacheRepository].toInstance(mock[FileUploadReferenceCacheRepository]),
      bind[FileUploadOutcomeRepository].toInstance(mock[FileUploadOutcomeRepository]),
      bind[FinancialInfoCacheRepository].toInstance(mock[FinancialInfoCacheRepository]),
      bind[FinancialInfoCreditAccessRepository].toInstance(mock[FinancialInfoCreditAccessRepository]),
      bind[AdminDataRepository].toInstance(mock[AdminDataRepository])
    )

  val application: Application = new GuiceApplicationBuilder()
    .configure(conf = "auditing.enabled" -> false, "metrics.enabled" -> false, "metrics.jvm" -> false).
    overrides(modules: _*).build()

  private def controllerForGetAftVersions: AFTController = {

    val controller = application.injector.instanceOf[AFTController]
    when(mockDesConnector.getAftVersions(ArgumentMatchers.eq(pstr), ArgumentMatchers.eq(startDt))(any(), any(), any())).thenReturn(
      Future.successful(versions))
    controller
  }

  before {
    reset(mockDesConnector)
    reset(mockAftService)
    reset(authConnector)
    reset(mockJSONPayloadSchemaValidator)
    when(authConnector.authorise[Option[String]](any(), any())(any(), any())) thenReturn Future.successful(Some("Ext-137d03b9-d807-4283-a254-fb6c30aceef1"))
    when(mockJSONPayloadSchemaValidator.validateJsonPayload(any(), any())).thenReturn(Right(true))
  }

  "fileReturn" must {
    "return OK when valid response from DES" in {
      val controller = application.injector.instanceOf[AFTController]

      when(mockDesConnector.idempotentFileAFTReturn(any(), any(), any(), any())(any(), any(), any()))
        .thenReturn(Future.successful( fileAFTUaRequestJson.toString))
      when(mockAftOverviewCacheRepository.remove(any())(any())).thenReturn(Future.successful(true))
      val result = controller.fileReturn(journeyType, uuid)(fakeRequest.withJsonBody(fileAFTUaRequestJson).withHeaders(
        newHeaders = "pstr" -> pstr))
      status(result) mustBe OK
    }

    "return OK when valid response (full Payload) from DES" in {
      val controller = application.injector.instanceOf[AFTController]

      when(mockDesConnector.idempotentFileAFTReturn(any(), any(), any(), any())(any(), any(), any()))
        .thenReturn(Future.successful(fileAFTUaRequestJson.toString))
      when(mockAftOverviewCacheRepository.remove(any())(any())).thenReturn(Future.successful(true))
      val result = controller.fileReturn(journeyType, uuid)(fakeRequest.withJsonBody(fileAFTUaFullPayloadRequestJson).withHeaders(
        newHeaders = "pstr" -> pstr))
      status(result) mustBe OK
    }

    "return invalid payload errors when invalid payload generated by transformation" in {
      Mockito.reset(mockAuditService)
      val eventCaptor = ArgumentCaptor.forClass(classOf[FileAftReturnSchemaValidator])
      val controller = application.injector.instanceOf[AFTController]

      val GivingErrorPayload = "{\"schemaPath\":\"#\",\"keyword\":\"oneOf\",\"instancePath\":\"\"" +
        ",\"errors\":{\"/oneOf/0\":[{\"schemaPath\":\"#/oneOf/0/definitions/totalAmountType\"," +
        "\"errors\":{},\"keyword\":\"type\",\"msgs\":[\"Wrong type. Expected number, was string.\"]," +
        "\"instancePath\":\"/chargeDetails/chargeTypeFDetails/totalAmount\"}]}}"
      val validationResponse = Left(List(ErrorReport("test", GivingErrorPayload)))
      when(mockJSONPayloadSchemaValidator.validateJsonPayload(any(), any()))
        .thenReturn(validationResponse)

      when(mockDesConnector.idempotentFileAFTReturn(any(), any(), any(), any())(any(), any(), any()))
        .thenReturn(Future.successful(fileAFTUaRequestJson.toString))
      when(mockAftOverviewCacheRepository.remove(any())(any())).thenReturn(Future.successful(true))

      recoverToExceptionIf[AFTValidationFailureException] {
        controller.fileReturn(journeyType, uuid)(fakeRequest.withJsonBody(fileAFTUaInvalidPayloadRequestJson).
          withHeaders(newHeaders = "pstr" -> pstr))
      } map { ex =>
        val expectedErrorMessage = "Invalid AFT file AFT return:-\nErrorReport(test,{\"schemaPath\":\"#\",\"keyword\":\"oneOf\",\"instancePath\":\"\",\"errors\":{\"/oneOf/0\":[{\"schemaPath\":\"#/oneOf/0/definitions/totalAmountType\",\"errors\":{},\"keyword\":\"type\",\"msgs\":[\"Wrong type. Expected number, was string.\"],\"instancePath\":\"/chargeDetails/chargeTypeFDetails/totalAmount\"}]}})"
        val expectedSchemaErrorMessage = "ErrorReport(test,{\"schemaPath\":\"#\",\"keyword\":\"oneOf\",\"instancePath\":\"\",\"errors\":{\"/oneOf/0\":[{\"schemaPath\":\"#/oneOf/0/definitions/totalAmountType\",\"errors\":{},\"keyword\":\"type\",\"msgs\":[\"Wrong type. Expected number, was string.\"],\"instancePath\":\"/chargeDetails/chargeTypeFDetails/totalAmount\"}]}})"
        verify(mockAuditService, times(1)).sendEvent(eventCaptor.capture())(any(), any())
        eventCaptor.getValue mustBe FileAftReturnSchemaValidator(psaIdJsValue.toString(), pstr, expectedChargeType, invalidJson, expectedSchemaErrorMessage, 1)
        ex.exMessage mustBe expectedErrorMessage
      }
    }

    "throw Upstream5XXResponse on Internal Server Error from DES" in {
      val controller = application.injector.instanceOf[AFTController]

      when(mockDesConnector.idempotentFileAFTReturn(any(), any(), any(), any())(any(), any(), any()))
        .thenReturn(Future.failed(UpstreamErrorResponse(message = "Internal Server Error", INTERNAL_SERVER_ERROR, INTERNAL_SERVER_ERROR)))
      when(mockAftOverviewCacheRepository.remove(any())(any())).thenReturn(Future.successful(true))
      recoverToExceptionIf[UpstreamErrorResponse] {
        controller.fileReturn(journeyType, uuid)(fakeRequest.withJsonBody(fileAFTUaRequestJson).
          withHeaders(newHeaders = "pstr" -> pstr))
      } map {
        _.statusCode mustBe INTERNAL_SERVER_ERROR
      }
    }

    "return OK when valid response from DES for payload with only one member based charge and zero value" in {
      val controller = application.injector.instanceOf[AFTController]
      val jsonPayload = jsonOneMemberZeroValue
      when(mockAftOverviewCacheRepository.remove(any())(any())).thenReturn(Future.successful(true))
      when(mockDesConnector.idempotentFileAFTReturn(any(), any(), any(), any())(any(), any(), any()))
        .thenReturn(Future.successful(jsonPayload.toString))
      val result = controller.fileReturn(journeyType, uuid)(fakeRequest.withJsonBody(jsonPayload).
        withHeaders(newHeaders = "pstr" -> pstr))
      status(result) mustBe OK
    }
  }

  "getVersions" must {

    "return OK with the version if there is only version" in {

      val controller = application.injector.instanceOf[AFTController]

      when(mockDesConnector.getAftVersions(ArgumentMatchers.eq(pstr), ArgumentMatchers.eq(startDt))(any(), any(), any())).thenReturn(
        Future.successful(Seq(AFTVersion(1, LocalDate.now(), "submitted"))))
      when(mockDesConnector.getAftDetails(ArgumentMatchers.eq(pstr), ArgumentMatchers.eq(startDt), ArgumentMatchers.eq("1"))(any(), any(), any())).thenReturn(
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

      when(mockDesConnector.getAftVersions(ArgumentMatchers.eq(pstr), ArgumentMatchers.eq(startDt))(any(), any(), any())).thenReturn(
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

      when(mockDesConnector.getAftDetails(ArgumentMatchers.eq(pstr), ArgumentMatchers.eq(startDt), ArgumentMatchers.eq(aftVer))(any(), any(), any()))
        .thenReturn(Future.successful(etmpAFTDetailsResponse))

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

      when(mockDesConnector.getAftDetails(ArgumentMatchers.eq(pstr), ArgumentMatchers.eq(startDt), ArgumentMatchers.eq(aftVer))(any(), any(), any()))
        .thenReturn(Future.failed(new BadRequestException(errorResponse("INVALID_START_DATE"))))

      recoverToExceptionIf[BadRequestException] {
        controller.getDetails()(fakeRequestForGetDetails)
      } map { response =>
        response.responseCode mustBe BAD_REQUEST
        response.getMessage mustBe errorResponse("INVALID_START_DATE")
      }
    }

    "throw Upstream4xxResponse when UpStream4XXResponse returned from Des" in {

      val controller = application.injector.instanceOf[AFTController]

      when(mockDesConnector.getAftDetails(ArgumentMatchers.eq(pstr), ArgumentMatchers.eq(startDt), ArgumentMatchers.eq(aftVer))(any(), any(), any()))
        .thenReturn(Future.failed(UpstreamErrorResponse(errorResponse("NOT_FOUND"), NOT_FOUND, NOT_FOUND)))

      recoverToExceptionIf[UpstreamErrorResponse] {
        controller.getDetails()(fakeRequestForGetDetails)
      } map { response =>
        response.statusCode mustBe NOT_FOUND
        response.getMessage mustBe errorResponse("NOT_FOUND")
      }
    }

    "throw Upstream5xxResponse when UpStream5XXResponse with INTERNAL_SERVER_ERROR returned from Des" in {

      val controller = application.injector.instanceOf[AFTController]

      when(mockDesConnector.getAftDetails(ArgumentMatchers.eq(pstr), ArgumentMatchers.eq(startDt), ArgumentMatchers.eq(aftVer))(any(), any(), any()))
        .thenReturn(Future.failed(UpstreamErrorResponse(errorResponse("INTERNAL_SERVER_ERROR"), INTERNAL_SERVER_ERROR, INTERNAL_SERVER_ERROR)))

      recoverToExceptionIf[UpstreamErrorResponse] {
        controller.getDetails()(fakeRequestForGetDetails)
      } map { response =>
        response.statusCode mustBe INTERNAL_SERVER_ERROR
        response.getMessage mustBe errorResponse("INTERNAL_SERVER_ERROR")
      }
    }

    "throw generic exception when any other exception returned from Des" in {

      val controller = application.injector.instanceOf[AFTController]

      when(mockDesConnector.getAftDetails(ArgumentMatchers.eq(pstr), ArgumentMatchers.eq(startDt), ArgumentMatchers.eq(aftVer))(any(), any(), any()))
        .thenReturn(Future.failed(new Exception("Generic Exception")))

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

      when(mockDesConnector.getAftDetails(ArgumentMatchers.eq(pstr), ArgumentMatchers.eq(startDt), ArgumentMatchers.eq(aftVer))(any(), any(), any()))
        .thenReturn(Future.successful(etmpAFTDetailsResponse))

      val result = controller.getIsChargeNonZero()(fakeRequestForGetDetails)

      status(result) mustBe OK
      contentAsJson(result) mustBe JsBoolean(true)
    }

    "return false when a charge has positive value" in {

      val controller = application.injector.instanceOf[AFTController]

      when(mockDesConnector.getAftDetails(ArgumentMatchers.eq(pstr), ArgumentMatchers.eq(startDt), ArgumentMatchers.eq(aftVer))(any(), any(), any()))
        .thenReturn(Future.successful(etmpAFTDetailsResponse))
      when(mockAftService.isChargeZeroedOut(any())).thenReturn(true)

      val result = controller.getIsChargeNonZero()(fakeRequestForGetDetails)

      status(result) mustBe OK
      contentAsJson(result) mustBe JsBoolean(false)
    }

  }

  "getOverview" must {
    "return OK with the Seq of overview details and no data was found in cache" in {
      when(mockAftOverviewCacheRepository.get(any())(any())).thenReturn(Future.successful(None))
      when(mockAftOverviewCacheRepository.save(any(), any())(any())).thenReturn(Future.successful((): Unit))
      when(mockDesConnector.getAftOverview(ArgumentMatchers.eq(pstr), ArgumentMatchers.eq(startDt), ArgumentMatchers.eq(endDate))(any(), any()))
        .thenReturn(Future.successful(aftOverview))

      val controller = application.injector.instanceOf[AFTController]

      val result = controller.getOverview(fakeRequest
        .withHeaders(newHeaders = "pstr" -> pstr, "startDate" -> startDt, "endDate" -> endDate))

      status(result) mustBe OK
      contentAsJson(result) mustBe aftOverviewResponseJson
    }

    "return OK with the Seq of overview details and some data was found in cache" in {

      when(mockAftOverviewCacheRepository.get(any())(any())).thenReturn(Future.successful(Some(Json.toJson(aftOverview))))
      val controller = application.injector.instanceOf[AFTController]

      val result = controller.getOverview(fakeRequest
        .withHeaders(newHeaders = "pstr" -> pstr, "startDate" -> startDt, "endDate" -> endDate))

      status(result) mustBe OK
      contentAsJson(result) mustBe aftOverviewResponseJson
    }

    "throw BadRequestException when endDate is not present in the header" in {

      val controller = application.injector.instanceOf[AFTController]

      recoverToExceptionIf[BadRequestException] {
        controller.getOverview(fakeRequest.withHeaders(newHeaders = "pstr" -> pstr, "startDate" -> startDt))
      } map { response =>
        response.responseCode mustBe BAD_REQUEST
        response.message must include("Bad Request with no endDate")
      }
    }

    "throw Upstream5xxResponse when UpStream5XXResponse with INTERNAL_SERVER_ERROR returned from Des" in {

      val controller = application.injector.instanceOf[AFTController]

      when(mockDesConnector.getAftVersions(ArgumentMatchers.eq(pstr), ArgumentMatchers.eq(startDt))(any(), any(), any())).thenReturn(
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
  private val psaIdJsValue = Json.toJson("A2100005")
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
      "aftVersion" -> "1",
      "quarterStartDate" -> "2020-02-29",
      "quarterEndDate" -> "2020-05-29",
      "receiptDate" -> "2016-12-17T09:30:47Z",
    ),
    "chargeDetails" -> Json.obj(
      "chargeTypeFDetails" -> Json.obj(
        "amendedVersion" -> 1,
        "totalAmount" -> 200.02,
        "dateRegiWithdrawn" -> "1980-02-29"
      )
    ),
    "aftDeclarationDetails" -> Json.obj(
      "submittedBy" -> "PSP",
      "submitterId" -> "10000240",
      "psaId" -> "A0003450",
      "submitterName" -> "Martin Brookes",
      "pspDeclarationDetails" -> Json.obj(
        "pspDeclaration1" -> true,
        "pspDeclaration2" -> true
      )
    )
  )

  private val transformedAftDEtailsUAJson = Json.obj(
    "aftStatus" -> "Compiled",
    "aftVersion" -> "1",
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
    ),
    "submitterDetails" -> Json.obj(
      "submitterType" -> "PSP",
      "submitterID" -> "10000240",
      "authorisingPsaId" -> "A0003450",
      "submitterName" -> "Martin Brookes",
      "receiptDate" -> "2016-12-17"
    )
  )

  private def createAFTDetailsResponse(chargeSection: JsObject): JsObject = Json.obj(
    "schemeDetails" -> Json.obj(
      "pstr" -> "12345678AB",
      "schemeName" -> "PSTR Scheme"
    ),
    "aftDetails" -> Json.obj(
      "aftStatus" -> "Compiled",
      "aftVersion" -> "1",
      "quarterStartDate" -> "2020-02-29",
      "quarterEndDate" -> "2020-05-29"
    ),
    "chargeDetails" -> chargeSection
  )

  val aftOverviewResponseJson: JsArray = Json.arr(
    Json.obj(
      "periodStartDate" -> "2020-04-01",
      "periodEndDate" -> "2020-06-30",
      "tpssReportPresent" -> false,
      "versionDetails" -> Json.obj(
        "numberOfVersions" -> 3,
        "submittedVersionAvailable" -> false,
        "compiledVersionAvailable" -> true
      )),
    Json.obj(
      "periodStartDate" -> "2020-07-01",
      "periodEndDate" -> "2020-10-31",
      "tpssReportPresent" -> false,
      "versionDetails" -> Json.obj(
        "numberOfVersions" -> 2,
        "submittedVersionAvailable" -> true,
        "compiledVersionAvailable" -> true
      ))
  )

  private val overview1 = AFTOverview(
    LocalDate.of(2020, 4, 1),
    LocalDate.of(2020, 6, 30),
    tpssReportPresent = false,
    Some(AFTOverviewVersion(
      3,
      submittedVersionAvailable = false,
      compiledVersionAvailable = true)))

  private val overview2 = AFTOverview(
    LocalDate.of(2020, 7, 1),
    LocalDate.of(2020, 10, 31),
    tpssReportPresent = false,
    Some(AFTOverviewVersion(
      2,
      submittedVersionAvailable = true,
      compiledVersionAvailable = true)))

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
      |  "aftVersion": "1",
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

  private val invalidPayloadJson =
    """
      |{
      |  "schemeStatus": "Open",
      |  "loggedInPersonEmail": "nigel@test.com",
      |  "loggedInPersonName": "Nigel Robert Smith",
      |  "minimalFlags": {
      |    "deceasedFlag": false,
      |    "rlsFlag": false
      |  },
      |  "chargeCDetails": {
      |    "employers": [
      |      {
      |        "sponsoringIndividualDetails": {
      |          "firstName": "Ray",
      |          "lastName": "Golding",
      |          "nino": "AA000020A"
      |        },
      |        "memberStatus": "New",
      |        "sponsoringEmployerAddress": {
      |          "country": "GB",
      |          "line4": "Shropshire",
      |          "postcode": "TF3 4NT",
      |          "line3": "Telford",
      |          "line2": "Ironmasters Way",
      |          "line1": "Plaza 2 "
      |        },
      |        "memberAFTVersion": 1,
      |        "chargeDetails": {
      |          "amountTaxDue": 2300.02,
      |          "paymentDate": "2020-10-18"
      |        },
      |        "whichTypeOfSponsoringEmployer": "individual"
      |      },
      |      {
      |        "sponsoringIndividualDetails": {
      |          "firstName": "Craig",
      |          "lastName": "McMillan",
      |          "nino": "AA000620A"
      |        },
      |        "memberStatus": "New",
      |        "sponsoringEmployerAddress": {
      |          "country": "GB",
      |          "line4": "Warwickshire",
      |          "postcode": "B1 1LA",
      |          "line3": "Birmingham",
      |          "line2": "Post Box APTS",
      |          "line1": "45 UpperMarshall Street"
      |        },
      |        "memberAFTVersion": 1,
      |        "chargeDetails": {
      |          "amountTaxDue": 12340.02,
      |          "paymentDate": "2020-10-28"
      |        },
      |        "whichTypeOfSponsoringEmployer": "individual"
      |      }
      |    ],
      |    "totalChargeAmount": 90.02,
      |    "amendedVersion": 1
      |  },
      |  "submitterDetails": {
      |    "receiptDate": "2016-12-17"
      |  },
      |  "chargeADetails": {
      |    "amendedVersion": 1,
      |    "chargeDetails": {
      |      "totalAmtOfTaxDueAtHigherRate": 2500.02,
      |      "totalAmount": 4500.04,
      |      "numberOfMembers": 2,
      |      "totalAmtOfTaxDueAtLowerRate": 2000.02
      |    }
      |  },
      |  "pstr": "24000041IN",
      |  "chargeBDetails": {
      |    "amendedVersion": 1,
      |    "chargeDetails": {
      |      "totalAmount": 100.02,
      |      "numberOfDeceased": 2
      |    }
      |  },
      |  "chargeDDetails": {
      |    "totalChargeAmount": 2345.02,
      |    "members": [
      |      {
      |        "memberStatus": "New",
      |        "memberDetails": {
      |          "firstName": "Joy",
      |          "lastName": "Kenneth",
      |          "nino": "AA089000A"
      |        },
      |        "memberAFTVersion": 1,
      |        "chargeDetails": {
      |          "dateOfEvent": "2020-10-18",
      |          "taxAt55Percent": 9.02,
      |          "taxAt25Percent": 1.02
      |        }
      |      },
      |      {
      |        "memberStatus": "New",
      |        "memberDetails": {
      |          "firstName": "Brian",
      |          "lastName": "Lara",
      |          "nino": "AA100000A"
      |        },
      |        "memberAFTVersion": 1,
      |        "chargeDetails": {
      |          "dateOfEvent": "2020-10-28",
      |          "taxAt55Percent": 10.02,
      |          "taxAt25Percent": 3.02
      |        }
      |      }
      |    ],
      |    "amendedVersion": 1
      |  },
      |  "aftVersion": 2,
      |  "schemeName": "Open Scheme Overview API Test 2",
      |  "aftStatus": "Submitted",
      |  "quarter": {
      |    "endDate": "2020-12-31",
      |    "startDate": "2020-10-01"
      |  },
      |  "aFTSummary": false,
      |  "valueChangeType": "same",
      |  "confirmSubmitAFTAmendment": true,
      |  "declaration": {
      |    "submittedBy": "PSA",
      |    "submittedID": "A2100005",
      |    "hasAgreed": true
      |  }
      |}
      |""".stripMargin

  private val uaPayloadJson =
    """
      |{
      |  "schemeStatus": "Open",
      |  "loggedInPersonEmail": "nigel@test.com",
      |  "loggedInPersonName": "Nigel Robert Smith",
      |  "minimalFlags": {
      |    "deceasedFlag": false,
      |    "rlsFlag": false
      |  },
      |  "chargeCDetails": {
      |    "employers": [
      |      {
      |        "sponsoringIndividualDetails": {
      |          "firstName": "Ray",
      |          "lastName": "Golding",
      |          "nino": "AA000020A"
      |        },
      |        "memberStatus": "New",
      |        "sponsoringEmployerAddress": {
      |          "country": "GB",
      |          "line4": "Shropshire",
      |          "postcode": "TF3 4NT",
      |          "line3": "Telford",
      |          "line2": "Ironmasters Way",
      |          "line1": "Plaza 2 "
      |        },
      |        "memberAFTVersion": 1,
      |        "chargeDetails": {
      |          "amountTaxDue": 2300.02,
      |          "paymentDate": "2020-10-18"
      |        },
      |        "whichTypeOfSponsoringEmployer": "individual"
      |      },
      |      {
      |        "sponsoringIndividualDetails": {
      |          "firstName": "Craig",
      |          "lastName": "McMillan",
      |          "nino": "AA000620A"
      |        },
      |        "memberStatus": "New",
      |        "sponsoringEmployerAddress": {
      |          "country": "GB",
      |          "line4": "Warwickshire",
      |          "postcode": "B1 1LA",
      |          "line3": "Birmingham",
      |          "line2": "Post Box APTS",
      |          "line1": "45 UpperMarshall Street"
      |        },
      |        "memberAFTVersion": 1,
      |        "chargeDetails": {
      |          "amountTaxDue": 12340.02,
      |          "paymentDate": "2020-10-28"
      |        },
      |        "whichTypeOfSponsoringEmployer": "individual"
      |      }
      |    ],
      |    "totalChargeAmount": 90.02,
      |    "amendedVersion": 1
      |  },
      |  "submitterDetails": {
      |    "receiptDate": "2016-12-17"
      |  },
      |  "chargeADetails": {
      |    "amendedVersion": 1,
      |    "chargeDetails": {
      |      "totalAmtOfTaxDueAtHigherRate": 2500.02,
      |      "totalAmount": 4500.04,
      |      "numberOfMembers": 2,
      |      "totalAmtOfTaxDueAtLowerRate": 2000.02
      |    }
      |  },
      |  "pstr": "24000041IN",
      |  "chargeBDetails": {
      |    "amendedVersion": 1,
      |    "chargeDetails": {
      |      "totalAmount": 100.02,
      |      "numberOfDeceased": 2
      |    }
      |  },
      |  "chargeDDetails": {
      |    "totalChargeAmount": 2345.02,
      |    "members": [
      |      {
      |        "memberStatus": "New",
      |        "memberDetails": {
      |          "firstName": "Joy",
      |          "lastName": "Kenneth",
      |          "nino": "AA089000A"
      |        },
      |        "memberAFTVersion": 1,
      |        "chargeDetails": {
      |          "dateOfEvent": "2020-10-18",
      |          "taxAt55Percent": 9.02,
      |          "taxAt25Percent": 1.02
      |        }
      |      },
      |      {
      |        "memberStatus": "New",
      |        "memberDetails": {
      |          "firstName": "Brian",
      |          "lastName": "Lara",
      |          "nino": "AA100000A"
      |        },
      |        "memberAFTVersion": 1,
      |        "chargeDetails": {
      |          "dateOfEvent": "2020-10-28",
      |          "taxAt55Percent": 10.02,
      |          "taxAt25Percent": 3.02
      |        }
      |      }
      |    ],
      |    "amendedVersion": 1
      |  },
      |  "aftVersion": 2,
      |  "schemeName": "Open Scheme Overview API Test 2",
      |  "aftStatus": "Submitted",
      |  "quarter": {
      |    "endDate": "2020-12-31",
      |    "startDate": "2020-10-01"
      |  },
      |  "aFTSummary": false,
      |  "valueChangeType": "same",
      |  "confirmSubmitAFTAmendment": true,
      |  "declaration": {
      |    "submittedBy": "PSA",
      |    "submittedID": "A2100005",
      |    "hasAgreed": true
      |  }
      |}
      |""".stripMargin

  val invalidJson = Json.parse(
    """
      |{
      |  "aftDetails": {
      |    "aftStatus": "Submitted",
      |    "quarterEndDate": "2020-12-31",
      |    "quarterStartDate": "2020-10-01"
      |  },
      |  "aftDeclarationDetails": {
      |    "submittedBy": "PSA",
      |    "submittedID": "A2100005",
      |    "psaDeclarationDetails": {
      |      "psaDeclaration1": true,
      |      "psaDeclaration2": true
      |    }
      |  },
      |  "chargeDetails": {
      |    "chargeTypeCDetails": {
      |      "totalAmount": 90.02,
      |      "amendedVersion": 1,
      |      "memberDetails": [
      |        {
      |          "dateOfPayment": "2020-10-18",
      |          "memberTypeDetails": {
      |            "individualDetails": {
      |              "firstName": "Ray",
      |              "lastName": "Golding",
      |              "nino": "AA000020A"
      |            },
      |            "memberType": "Individual"
      |          },
      |          "totalAmountOfTaxDue": 2300.02,
      |          "memberStatus": "New",
      |          "memberAFTVersion": 1,
      |          "correspondenceAddressDetails": {
      |            "countryCode": "GB",
      |            "postalCode": "TF3 4NT",
      |            "addressLine1": "Plaza 2 ",
      |            "addressLine2": "Ironmasters Way",
      |            "addressLine3": "Telford",
      |            "addressLine4": "Shropshire",
      |            "nonUKAddress": "False"
      |          }
      |        },
      |        {
      |          "dateOfPayment": "2020-10-28",
      |          "memberTypeDetails": {
      |            "individualDetails": {
      |              "firstName": "Craig",
      |              "lastName": "McMillan",
      |              "nino": "AA000620A"
      |            },
      |            "memberType": "Individual"
      |          },
      |          "totalAmountOfTaxDue": 12340.02,
      |          "memberStatus": "New",
      |          "memberAFTVersion": 1,
      |          "correspondenceAddressDetails": {
      |            "countryCode": "GB",
      |            "postalCode": "B1 1LA",
      |            "addressLine1": "45 UpperMarshall Street",
      |            "addressLine2": "Post Box APTS",
      |            "addressLine3": "Birmingham",
      |            "addressLine4": "Warwickshire",
      |            "nonUKAddress": "False"
      |          }
      |        }
      |      ]
      |    },
      |    "chargeTypeDDetails": {
      |      "totalAmount": 2345.02,
      |      "amendedVersion": 1,
      |      "memberDetails": [
      |        {
      |          "totalAmtOfTaxDueAtHigherRate": 9.02,
      |          "individualsDetails": {
      |            "firstName": "Joy",
      |            "lastName": "Kenneth",
      |            "nino": "AA089000A"
      |          },
      |          "memberStatus": "New",
      |          "totalAmtOfTaxDueAtLowerRate": 1.02,
      |          "memberAFTVersion": 1,
      |          "dateOfBeneCrysEvent": "2020-10-18"
      |        },
      |        {
      |          "totalAmtOfTaxDueAtHigherRate": 10.02,
      |          "individualsDetails": {
      |            "firstName": "Brian",
      |            "lastName": "Lara",
      |            "nino": "AA100000A"
      |          },
      |          "memberStatus": "New",
      |          "totalAmtOfTaxDueAtLowerRate": 3.02,
      |          "memberAFTVersion": 1,
      |          "dateOfBeneCrysEvent": "2020-10-28"
      |        }
      |      ]
      |    },
      |    "chargeTypeADetails": {
      |      "totalAmtOfTaxDueAtHigherRate": 2500.02,
      |      "totalAmount": 4500.04,
      |      "numberOfMembers": 2,
      |      "amendedVersion": 1,
      |      "totalAmtOfTaxDueAtLowerRate": 2000.02
      |    },
      |    "chargeTypeBDetails": {
      |      "totalAmount": 100.02,
      |      "numberOfMembers": 2,
      |      "amendedVersion": 1
      |    }
      |  }
      |}
      |""".stripMargin)

  private val fileAFTUaRequestJson = Json.parse(json)
  private val fileAFTUaFullPayloadRequestJson = Json.parse(uaPayloadJson)
  private val fileAFTUaInvalidPayloadRequestJson = Json.parse(invalidPayloadJson)
  private val jsonOneMemberZeroValue = Json.parse(
    """{
      |  "aftStatus": "Compiled",
      |  "aftVersion": "1",
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

  private val expectedChargeType = "List(chargeTypeA, chargeTypeB, chargeTypeC, chargeTypeD)"
}
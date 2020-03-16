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
import models.AFTVersion
import org.mockito.Matchers.any
import org.mockito.Mockito._
import org.mockito.{ArgumentCaptor, Matchers}
import org.scalatest.{AsyncWordSpec, BeforeAndAfter, MustMatchers}
import org.scalatestplus.mockito.MockitoSugar
import play.api.Application
import play.api.inject.bind
import play.api.inject.guice.{GuiceApplicationBuilder, GuiceableModule}
import play.api.libs.json.{JsObject, JsValue, Json}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.http._
import utils.JsonFileReader

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class AFTControllerSpec extends AsyncWordSpec with MustMatchers with MockitoSugar with BeforeAndAfter with JsonFileReader {

  import AFTControllerSpec._

  implicit val hc: HeaderCarrier = HeaderCarrier()

  private val fakeRequest = FakeRequest("GET", "/")
  private val mockDesConnector = mock[DesConnector]
  private val zeroCurrencyValue = BigDecimal(0.00)
  private val nonZeroCurrencyValue = BigDecimal(44.33)

  private val nonMemberBasedChargeSections = Seq("chargeTypeADetails", "chargeTypeBDetails", "chargeTypeFDetails")
  private val nonMemberBasedChargeNames = Seq("A", "B", "F")

  private val memberBasedChargeCreationFunctions = Seq(
    chargeCSectionWithValue _,
    chargeDSectionWithValue _,
    chargeESectionWithValue _,
    chargeGSectionWithValue _
  )
  private val memberBasedChargeNames = Seq("C", "D", "E", "G")
  private val version1 = AFTVersion(1, LocalDate.now())
  private val version2 = AFTVersion(2, LocalDate.now())
  private val versions = Seq(version1, version2)

  private def controllerForGetAftVersions = {
    val application: Application = new GuiceApplicationBuilder()
      .configure(conf = "auditing.enabled" -> false, "metrics.enabled" -> false, "metrics.jvm" -> false).
      overrides(modules: _*).build()
    val controller = application.injector.instanceOf[AFTController]
    when(mockDesConnector.getAftVersions(Matchers.eq(pstr), Matchers.eq(startDt))(any(), any(), any())).thenReturn(
      Future.successful(versions))
    controller
  }

  before {
    reset(mockDesConnector)
  }
  val modules: Seq[GuiceableModule] =
    Seq(
      bind[DesConnector].toInstance(mockDesConnector)
    )

  "fileReturn" must {
    "return OK when valid response from DES" in {
      val application: Application = new GuiceApplicationBuilder()
        .configure(conf = "auditing.enabled" -> false, "metrics.enabled" -> false, "metrics.jvm" -> false).
        overrides(modules: _*).build()
      val controller = application.injector.instanceOf[AFTController]
      val eventCaptor = ArgumentCaptor.forClass(classOf[Boolean])
      when(mockDesConnector.fileAFTReturn(any(), any(), eventCaptor.capture())(any(), any(), any()))
        .thenReturn(Future.successful(HttpResponse(OK, Some(fileAFTUaRequestJson))))

      val result = controller.fileReturn()(fakeRequest.withJsonBody(fileAFTUaRequestJson).withHeaders(newHeaders = "pstr" -> pstr))
      status(result) mustBe OK
      eventCaptor.getValue mustBe false
    }

    "throw Upstream5XXResponse on Internal Server Error from DES" in {
      val application: Application = new GuiceApplicationBuilder()
        .configure(conf = "auditing.enabled" -> false, "metrics.enabled" -> false, "metrics.jvm" -> false).
        overrides(modules: _*).build()
      val controller = application.injector.instanceOf[AFTController]
      when(mockDesConnector.fileAFTReturn(any(), any(), any())(any(), any(), any()))
        .thenReturn(Future.failed(Upstream5xxResponse(message = "Internal Server Error", INTERNAL_SERVER_ERROR, INTERNAL_SERVER_ERROR)))

      recoverToExceptionIf[Upstream5xxResponse] {
        controller.fileReturn()(fakeRequest.withJsonBody(fileAFTUaRequestJson).withHeaders(newHeaders = "pstr" -> pstr))
      } map {
        _.upstreamResponseCode mustBe INTERNAL_SERVER_ERROR
      }
    }

    "return OK when valid response from DES for payload with only one member based charge and zero value" in {
      val application: Application = new GuiceApplicationBuilder()
        .configure(conf = "auditing.enabled" -> false, "metrics.enabled" -> false, "metrics.jvm" -> false).
        overrides(modules: _*).build()
      val controller = application.injector.instanceOf[AFTController]
      val eventCaptor = ArgumentCaptor.forClass(classOf[Boolean])
      val jsonPayload = jsonOneMemberZeroValue
      when(mockDesConnector.fileAFTReturn(any(), any(), eventCaptor.capture())(any(), any(), any()))
        .thenReturn(Future.successful(HttpResponse(OK, Some(jsonPayload))))
      val result = controller.fileReturn()(fakeRequest.withJsonBody(jsonPayload).withHeaders(newHeaders = "pstr" -> pstr))
      status(result) mustBe OK
      eventCaptor.getValue mustBe true
    }
  }

  "getVersions" must {

    "return OK with the Seq of version numbers when the details are returned based on pstr and start date" in {
      val application: Application = new GuiceApplicationBuilder()
        .configure(conf = "auditing.enabled" -> false, "metrics.enabled" -> false, "metrics.jvm" -> false).
        overrides(modules: _*).build()
      val controller = application.injector.instanceOf[AFTController]
      when(mockDesConnector.getAftVersions(Matchers.eq(pstr), Matchers.eq(startDt))(any(), any(), any())).thenReturn(
        Future.successful(Seq(AFTVersion(1, LocalDate.now()))))
      when(mockDesConnector.getAftDetails(Matchers.eq(pstr), Matchers.eq(startDt), Matchers.eq("1"))(any(), any(), any())).thenReturn(
        Future.successful(createAFTDetailsResponse(chargeSectionWithValue("chargeADetails", nonZeroCurrencyValue)))
      )

      val result = controller.getVersions()(fakeRequest.withHeaders(newHeaders = "pstr" -> pstr, "startDate" -> startDt))

      status(result) mustBe OK
      contentAsJson(result) mustBe Json.arr(Json.toJson(version1))
    }


    memberBasedChargeCreationFunctions
      .zipWithIndex.foreach { case (createChargeSection, chargeSectionIndex) =>
      s"return OK EXCLUDING version number where there is a charge of type ${memberBasedChargeNames(chargeSectionIndex)} with a " +
        s"value of zero AND NO OTHER CHARGES" in {
        when(mockDesConnector.getAftDetails(Matchers.eq(pstr), Matchers.eq(startDt), Matchers.eq("1"))(any(), any(), any())).thenReturn(
          Future.successful(createAFTDetailsResponse(createChargeSection(zeroCurrencyValue))))
        when(mockDesConnector.getAftDetails(Matchers.eq(pstr), Matchers.eq(startDt), Matchers.eq("2"))(any(), any(), any())).thenReturn(
          Future.successful(createAFTDetailsResponse(createChargeSection(nonZeroCurrencyValue))))

        val result = controllerForGetAftVersions.getVersions()(fakeRequest.withHeaders(newHeaders = "pstr" -> pstr, "startDate" -> startDt))

        status(result) mustBe OK
        contentAsJson(result) mustBe Json.arr(Json.toJson(version2))
      }
    }

    nonMemberBasedChargeSections
      .zipWithIndex.foreach { case (nonMemberBasedChargeSection, nonMemberBasedChargeSectionIndex) =>
      memberBasedChargeCreationFunctions
        .zipWithIndex.foreach { case (createChargeSection, chargeSectionIndex) =>
        s"return OK INCLUDING version number where there is a charge of type ${memberBasedChargeNames(chargeSectionIndex)} with a " +
          s"value of zero BUT also a value in another non-member-based charge (${nonMemberBasedChargeNames(nonMemberBasedChargeSectionIndex)}})" in {

          when(mockDesConnector.getAftDetails(Matchers.eq(pstr), Matchers.eq(startDt), Matchers.eq("1"))(any(), any(), any()))
            .thenReturn(Future.successful(
              createAFTDetailsResponse(createChargeSection(zeroCurrencyValue) ++ chargeSectionWithValue(nonMemberBasedChargeSection, nonZeroCurrencyValue))
            ))
          when(mockDesConnector.getAftDetails(Matchers.eq(pstr), Matchers.eq(startDt), Matchers.eq("2"))(any(), any(), any()))
            .thenReturn(Future.successful(
              createAFTDetailsResponse(createChargeSection(nonZeroCurrencyValue))
            ))

          val result = controllerForGetAftVersions.getVersions()(fakeRequest.withHeaders(newHeaders = "pstr" -> pstr, "startDate" -> startDt))

          status(result) mustBe OK
          contentAsJson(result) mustBe Json.toJson(versions)
        }
      }
    }


    memberBasedChargeCreationFunctions.zipWithIndex.foreach { case (createOtherChargeSection, otherChargeSectionIndex) =>
      memberBasedChargeCreationFunctions
        .zipWithIndex.foreach { case (createChargeSection, chargeSectionIndex) =>
        if (chargeSectionIndex != otherChargeSectionIndex) {
          s"return OK INCLUDING version number where there is a charge of type ${memberBasedChargeNames(chargeSectionIndex)} with a " +
            s"value of zero BUT also a value in another member-based charge (${memberBasedChargeNames(otherChargeSectionIndex)})" in {
            when(mockDesConnector.getAftDetails(Matchers.eq(pstr), Matchers.eq(startDt), Matchers.eq("1"))(any(), any(), any()))
              .thenReturn(Future.successful(
                createAFTDetailsResponse(createChargeSection(zeroCurrencyValue) ++ createOtherChargeSection(nonZeroCurrencyValue))
              )
            )
            when(mockDesConnector.getAftDetails(Matchers.eq(pstr), Matchers.eq(startDt), Matchers.eq("2"))(any(), any(), any()))
              .thenReturn(Future.successful(
                createAFTDetailsResponse(createChargeSection(nonZeroCurrencyValue))
              ))

            val result = controllerForGetAftVersions.getVersions()(fakeRequest.withHeaders(newHeaders = "pstr" -> pstr, "startDate" -> startDt))

            status(result) mustBe OK
            contentAsJson(result) mustBe Json.toJson(versions)
          }
        }
      }
    }

    "throw BadRequestException when PSTR is not present in the header" in {
      val application: Application = new GuiceApplicationBuilder()
        .configure(conf = "auditing.enabled" -> false, "metrics.enabled" -> false, "metrics.jvm" -> false).
        overrides(modules: _*).build()
      val controller = application.injector.instanceOf[AFTController]
      recoverToExceptionIf[BadRequestException] {
        controller.getVersions()(fakeRequest.withHeaders(newHeaders = "startDate" -> startDt))
      } map { response =>
        response.responseCode mustBe BAD_REQUEST
        response.message must include("Bad Request with missing PSTR/Quarter Start Date")
      }
    }

    "throw Upstream5xxResponse when UpStream5XXResponse with INTERNAL_SERVER_ERROR returned from Des" in {
      val application: Application = new GuiceApplicationBuilder()
        .configure(conf = "auditing.enabled" -> false, "metrics.enabled" -> false, "metrics.jvm" -> false).
        overrides(modules: _*).build()
      val controller = application.injector.instanceOf[AFTController]
      when(mockDesConnector.getAftVersions(Matchers.eq(pstr), Matchers.eq(startDt))(any(), any(), any())).thenReturn(
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

  "getAftDetails" must {

    "return OK when the details are returned based on pstr, start date and AFT version" in {
      val application: Application = new GuiceApplicationBuilder()
        .configure(conf = "auditing.enabled" -> false, "metrics.enabled" -> false, "metrics.jvm" -> false).
        overrides(modules: _*).build()
      val controller = application.injector.instanceOf[AFTController]
      when(mockDesConnector.getAftDetails(Matchers.eq(pstr), Matchers.eq(startDt), Matchers.eq(aftVer))(any(), any(), any())).thenReturn(
        Future.successful(etmpAFTDetailsResponse))

      val result = controller.getDetails()(fakeRequestForGetDetails)

      status(result) mustBe OK
      contentAsJson(result) mustBe transformedAftDEtailsUAJson
    }

    "throw BadRequestException when PSTR is not present in the header" in {
      val application: Application = new GuiceApplicationBuilder()
        .configure(conf = "auditing.enabled" -> false, "metrics.enabled" -> false, "metrics.jvm" -> false).
        overrides(modules: _*).build()
      val controller = application.injector.instanceOf[AFTController]

      recoverToExceptionIf[BadRequestException] {
        controller.getDetails()(fakeRequest.withHeaders(("startDate", startDt), ("aftVersion", aftVer)))
      } map { response =>
        response.responseCode mustBe BAD_REQUEST
        response.getMessage mustBe "Bad Request with missing PSTR"
      }
    }

    "throw BadRequestException when bad request with INVALID_START_DATE returned from Des" in {
      val application: Application = new GuiceApplicationBuilder()
        .configure(conf = "auditing.enabled" -> false, "metrics.enabled" -> false, "metrics.jvm" -> false).
        overrides(modules: _*).build()
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
      val application: Application = new GuiceApplicationBuilder()
        .configure(conf = "auditing.enabled" -> false, "metrics.enabled" -> false, "metrics.jvm" -> false).
        overrides(modules: _*).build()
      val controller = application.injector.instanceOf[AFTController]
      when(mockDesConnector.getAftDetails(Matchers.eq(pstr), Matchers.eq(startDt), Matchers.eq(aftVer))(any(), any(), any())).thenReturn(
        Future.failed(Upstream4xxResponse(errorResponse("NOT_FOUND"), NOT_FOUND, NOT_FOUND)))

      recoverToExceptionIf[Upstream4xxResponse] {
        controller.getDetails()(fakeRequestForGetDetails)
      } map { response =>
        response.upstreamResponseCode mustBe NOT_FOUND
        response.getMessage mustBe errorResponse("NOT_FOUND")
      }
    }

    "throw Upstream5xxResponse when UpStream5XXResponse with INTERNAL_SERVER_ERROR returned from Des" in {
      val application: Application = new GuiceApplicationBuilder()
        .configure(conf = "auditing.enabled" -> false, "metrics.enabled" -> false, "metrics.jvm" -> false).
        overrides(modules: _*).build()
      val controller = application.injector.instanceOf[AFTController]
      when(mockDesConnector.getAftDetails(Matchers.eq(pstr), Matchers.eq(startDt), Matchers.eq(aftVer))(any(), any(), any())).thenReturn(
        Future.failed(Upstream5xxResponse(errorResponse("INTERNAL_SERVER_ERROR"), INTERNAL_SERVER_ERROR, INTERNAL_SERVER_ERROR)))

      recoverToExceptionIf[Upstream5xxResponse] {
        controller.getDetails()(fakeRequestForGetDetails)
      } map { response =>
        response.upstreamResponseCode mustBe INTERNAL_SERVER_ERROR
        response.getMessage mustBe errorResponse("INTERNAL_SERVER_ERROR")
      }
    }

    "throw generic exception when any other exception returned from Des" in {
      val application: Application = new GuiceApplicationBuilder()
        .configure(conf = "auditing.enabled" -> false, "metrics.enabled" -> false, "metrics.jvm" -> false).
        overrides(modules: _*).build()
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

  private val transformedAftDEtailsUAJson = Json.obj(
    "aftStatus" -> "Compiled",
    "quarter" -> Json.obj(
      "startDate" -> "2020-02-29",
      "endDate" -> "2020-05-29"
    ),
    "pstr" -> "12345678AB",
    "schemeName" -> "PSTR Scheme",
    "chargeFDetails" -> Json.obj(
      "amountTaxDue" -> 200.02,
      "deRegistrationDate" -> "1980-02-29"
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

  private def chargeSectionWithValue(section: String, currencyValue: BigDecimal): JsObject =
    Json.obj(
      section -> Json.obj(
        "totalAmount" -> currencyValue
      )
    )

  private def chargeCSectionWithValue(currencyValue: BigDecimal): JsObject = Json.obj(
    "chargeTypeCDetails" -> Json.obj(
      "totalAmount" -> currencyValue,
      "memberDetails" -> Json.arr(
        Json.obj(
          "memberStatus" -> "New",
          "memberAFTVersion" -> 1,
          "memberTypeDetails" -> Json.obj(
            "memberType" -> "Individual",
            "individualDetails" -> Json.obj(
              "title" -> "Mr",
              "firstName" -> "Ray",
              "lastName" -> "Golding",
              "nino" -> "AA000020A"
            )
          ),
          "correspondenceAddressDetails" -> Json.obj(
            "nonUKAddress" -> "False",
            "addressLine1" -> "Plaza 2 ",
            "addressLine2" -> "Ironmasters Way",
            "addressLine3" -> "Telford",
            "addressLine4" -> "Shropshire",
            "countryCode" -> "GB",
            "postalCode" -> "TF3 4NT"
          ),
          "dateOfPayment" -> "2016-06-29",
          "totalAmountOfTaxDue" -> currencyValue
        )
      )
    )
  )

  private def chargeDSectionWithValue(currencyValue: BigDecimal): JsObject = Json.obj(
    "chargeTypeDDetails" -> Json.obj(
      "totalAmount" -> currencyValue,
      "memberDetails" -> Json.arr(
        Json.obj(
          "memberStatus" -> "New",
          "memberAFTVersion" -> 1,
          "individualsDetails" -> Json.obj(
            "title" -> "Mr",
            "firstName" -> "Ray",
            "lastName" -> "Golding",
            "nino" -> "AA000020A"
          ),
          "dateOfBenefitCrystalizationEvent" -> "2016-06-29",
          "totalAmtOfTaxDueAtLowerRate" -> currencyValue,
          "totalAmtOfTaxDueAtHigherRate" -> currencyValue
        )
      )
    )
  )

  private def chargeESectionWithValue(currencyValue: BigDecimal): JsObject = Json.obj(
    "chargeTypeEDetails" -> Json.obj(
      "totalAmount" -> currencyValue,
      "memberDetails" -> Json.arr(
        Json.obj(
          "memberStatus" -> "New",
          "memberAFTVersion" -> 1,
          "individualsDetails" -> Json.obj(
            "title" -> "Mr",
            "firstName" -> "Ray",
            "lastName" -> "Golding",
            "nino" -> "AA000020A"
          ),
          "dateOfNotice" -> "2016-06-29",
          "amountOfCharge" -> currencyValue,
          "taxYearEnding" -> "2018",
          "paidUnder237b" -> "Yes"
        )
      )
    )
  )

  private def chargeGSectionWithValue(currencyValue: BigDecimal): JsObject = Json.obj(
    "chargeTypeGDetails" -> Json.obj(
      "totalOTCAmount" -> currencyValue,
      "memberDetails" -> Json.arr(
        Json.obj(
          "memberStatus" -> "New",
          "memberAFTVersion" -> 1,
          "individualsDetails" -> Json.obj(
            "title" -> "Mr",
            "firstName" -> "Ray",
            "lastName" -> "Golding",
            "dateOfBirth" -> "1980-02-29",
            "nino" -> "AA000020A"
          ),
          "dateOfTransfer" -> "2016-06-29",
          "amountTransferred" -> currencyValue,
          "amountOfTaxDeducted" -> currencyValue,
          "qropsReference" -> "Q300000"
        )
      )
    )
  )

  private val fakeRequestForGetDetails = FakeRequest("GET", "/").withHeaders(("pstr", pstr), ("startDate", startDt), ("aftVersion", aftVer))
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
      |                        "nino" : "CS121212C",
      |                        "isDeleted" : false
      |                    },
      |                    "isSponsoringEmployerIndividual" : true,
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

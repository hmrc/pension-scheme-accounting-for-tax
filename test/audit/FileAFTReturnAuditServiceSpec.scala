/*
 * Copyright 2025 HM Revenue & Customs
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

package audit

import org.mockito.Mockito.{times, verify}
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.mockito.MockitoSugar
import play.api.http.Status
import play.api.libs.json.Json
import play.api.mvc.RequestHeader
import uk.gov.hmrc.http.{HttpException, HttpResponse, UpstreamErrorResponse}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Success}

class FileAFTReturnAuditServiceSpec extends AnyFreeSpec with Matchers with MockitoSugar {

  "FileAFTReturnAuditService" - {

    "sendFileAFTReturnAuditEvent" - {

      "should send an audit event with a successful HttpResponse" in {
        val auditService = mock[AuditService]
        val service = new FileAFTReturnAuditService(auditService)

        val pstr = "12345678AA"
        val journeyType = "journeyType"
        val data = Json.obj("aftDetails" -> Json.obj("quarterStartDate" -> "2020-04-01"))
        val httpResponse = HttpResponse(Status.OK, Json.stringify(Json.obj("responseKey" -> "responseValue")))

        implicit val request: RequestHeader = mock[RequestHeader]

        val expected = FileAftReturn(pstr, journeyType, Status.OK, data, Some(httpResponse.json))

        val resultHandler = service.sendFileAFTReturnAuditEvent(pstr, journeyType, data)
        resultHandler.isDefinedAt(Success(httpResponse)) shouldBe true
        resultHandler(Success(httpResponse))

        verify(auditService, times(1)).sendEvent(expected)
      }

      "should send an audit event with an UpstreamErrorResponse" in {
        val auditService = mock[AuditService]
        val service = new FileAFTReturnAuditService(auditService)

        val pstr = "12345678AA"
        val journeyType = "journeyType"
        val data = Json.obj("aftDetails" -> Json.obj("quarterStartDate" -> "2020-04-01"))
        val error = UpstreamErrorResponse("Error", Status.BAD_GATEWAY)

        implicit val request: RequestHeader = mock[RequestHeader]

        val expected = FileAftReturn(pstr, journeyType, Status.BAD_GATEWAY, data, None)

        val result = service.sendFileAFTReturnAuditEvent(pstr, journeyType, data)
        result.isDefinedAt(Failure(error)) shouldBe true
        result(Failure(error))

        verify(auditService, times(1)).sendEvent(expected)
      }

      "should send an audit event with a HttpException" in {
        val auditService = mock[AuditService]
        val service = new FileAFTReturnAuditService(auditService)

        val pstr = "12345678AA"
        val journeyType = "journeyType"
        val data = Json.obj("aftDetails" -> Json.obj("quarterStartDate" -> "2020-04-01"))
        val error = new HttpException("Bad request", Status.BAD_REQUEST)

        implicit val request: RequestHeader = mock[RequestHeader]

        val expected = FileAftReturn(pstr, journeyType, Status.BAD_REQUEST, data, None)

        val result = service.sendFileAFTReturnAuditEvent(pstr, journeyType, data)
        result.isDefinedAt(Failure(error)) shouldBe true
        result(Failure(error))

        verify(auditService, times(1)).sendEvent(expected)
      }
    }

    "sendFileAFTReturnWhereOnlyOneChargeWithNoValueAuditEvent" - {

      "should send an audit event with a successful HttpResponse" in {
        val auditService = mock[AuditService]
        val service = new FileAFTReturnAuditService(auditService)

        val pstr = "12345678AA"
        val journeyType = "journeyType"
        val data = Json.obj("aftDetails" -> Json.obj("quarterStartDate" -> "2020-04-01"))
        val httpResponse = HttpResponse(Status.OK, Json.stringify(Json.obj("responseKey" -> "responseValue")))

        implicit val request: RequestHeader = mock[RequestHeader]

        val expected = FileAFTReturnOneChargeAndNoValue(pstr, journeyType, Status.OK, data, Some(httpResponse.json))
        expected.details("pstr").as[String] shouldBe pstr
        expected.details("status").as[String] shouldBe Status.OK.toString
        expected.details("requestSizeInBytes").as[Int] should be > 0

        val result = service.sendFileAFTReturnWhereOnlyOneChargeWithNoValueAuditEvent(pstr, journeyType, data)
        result.isDefinedAt(Success(httpResponse)) shouldBe true
        result(Success(httpResponse))

        verify(auditService, times(1)).sendEvent(expected)
      }

      "should send an audit event with an UpstreamErrorResponse" in {
        val auditService = mock[AuditService]
        val service = new FileAFTReturnAuditService(auditService)

        val pstr = "12345678AA"
        val journeyType = "journeyType"
        val data = Json.obj("aftDetails" -> Json.obj("quarterStartDate" -> "2020-04-01"))
        val error = UpstreamErrorResponse("Upstream error", Status.BAD_GATEWAY)

        implicit val request: RequestHeader = mock[RequestHeader]

        val expected = FileAFTReturnOneChargeAndNoValue(pstr, journeyType, Status.BAD_GATEWAY, data, None)
        expected.details("pstr").as[String] shouldBe pstr
        expected.details("status").as[String] shouldBe Status.BAD_GATEWAY.toString
        expected.details("requestSizeInBytes").as[Int] should be > 0

        val result = service.sendFileAFTReturnWhereOnlyOneChargeWithNoValueAuditEvent(pstr, journeyType, data)
        result.isDefinedAt(Failure(error)) shouldBe true
        result(Failure(error))

        verify(auditService, times(1)).sendEvent(expected)
      }

      "should send an audit event with a HttpException" in {
        val auditService = mock[AuditService]
        val service = new FileAFTReturnAuditService(auditService)

        val pstr = "12345678AA"
        val journeyType = "journeyType"
        val data = Json.obj("aftDetails" -> Json.obj("quarterStartDate" -> "2020-04-01"))
        val error = new HttpException("Http error", Status.BAD_REQUEST)

        implicit val request: RequestHeader = mock[RequestHeader]

        val expected = FileAFTReturnOneChargeAndNoValue(pstr, journeyType, Status.BAD_REQUEST, data, None)
        expected.details("pstr").as[String] shouldBe pstr
        expected.details("status").as[String] shouldBe Status.BAD_REQUEST.toString
        expected.details("requestSizeInBytes").as[Int] should be > 0

        val result = service.sendFileAFTReturnWhereOnlyOneChargeWithNoValueAuditEvent(pstr, journeyType, data)
        result.isDefinedAt(Failure(error)) shouldBe true
        result(Failure(error))

        verify(auditService, times(1)).sendEvent(expected)
      }


      "sendFileAftReturnSchemaValidatorAuditEvent" - {

        "should send an audit event" in {
          val auditService = mock[AuditService]
          val service = new FileAFTReturnAuditService(auditService)

          val psaOrPspId = "PSA123"
          val pstr = "12345678AA"
          val chargeType = "ChargeTypeExample"
          val data = Json.obj("aftDetails" -> Json.obj("quarterStartDate" -> "2020-04-01"))
          val failureResponse = "Invalid Data"
          val numberOfFailures = 1

          implicit val request: RequestHeader = mock[RequestHeader]

          val expected = FileAftReturnSchemaValidator(psaOrPspId, pstr, chargeType, data, failureResponse, numberOfFailures)

          service.sendFileAftReturnSchemaValidatorAuditEvent(psaOrPspId, pstr, chargeType, data, failureResponse, numberOfFailures)

          verify(auditService, times(1)).sendEvent(expected)
        }
      }
    }
    "FileAftReturn" - {

      "should serialize and deserialize correctly using implicit format" in {
        val original = FileAftReturn(
          pstr = "12345678AA",
          journeyType = "journeyType",
          status = 200,
          request = Json.obj("aftDetails" -> Json.obj("quarterStartDate" -> "2020-04-01")),
          response = Some(Json.obj("result" -> "ok"))
        )

        val json = Json.toJson(original)
        val result = json.validate[FileAftReturn]

        result.isSuccess shouldBe true
        result.get shouldBe original
      }
    }
  }
}

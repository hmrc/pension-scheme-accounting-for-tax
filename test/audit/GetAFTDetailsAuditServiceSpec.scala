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
import uk.gov.hmrc.http.{HttpException, UpstreamErrorResponse}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Success}

class GetAFTDetailsAuditServiceSpec extends AnyFreeSpec with Matchers with MockitoSugar {

  "GetAFTDetailsAuditService" - {

    "sendAFTDetailsAuditEvent" - {

      "should send an audit event with a successful response" in {
        val auditService = mock[AuditService]
        val service = new GetAFTDetailsAuditService(auditService)

        val pstr = "12345678AA"
        val startDate = "2020-04-01"
        val data = Json.obj("aftDetails" -> Json.obj("aftStatus" -> "Success"))

        implicit val request: RequestHeader = mock[RequestHeader]

        val expected = GetAFTDetails(pstr, startDate, Status.OK, Some(data))

        val resultHandler = service.sendAFTDetailsAuditEvent(pstr, startDate)
        resultHandler.isDefinedAt(Success(data)).shouldBe(true)
        resultHandler(Success(data))

        verify(auditService, times(1)).sendEvent(expected)
      }

      "should send an audit event with an UpstreamErrorResponse" in {
        val auditService = mock[AuditService]
        val service = new GetAFTDetailsAuditService(auditService)

        val pstr = "12345678AA"
        val startDate = "2020-04-01"
        val error = UpstreamErrorResponse("Error", Status.BAD_GATEWAY)

        implicit val request: RequestHeader = mock[RequestHeader]

        val expected = GetAFTDetails(pstr, startDate, Status.BAD_GATEWAY, None)

        val resultHandler = service.sendAFTDetailsAuditEvent(pstr, startDate)
        resultHandler.isDefinedAt(Failure(error)).shouldBe(true)
        resultHandler(Failure(error))

        verify(auditService, times(1)).sendEvent(expected)
      }

      "should send an audit event with a HttpException" in {
        val auditService = mock[AuditService]
        val service = new GetAFTDetailsAuditService(auditService)

        val pstr = "12345678AA"
        val startDate = "2020-04-01"
        val error = new HttpException("Bad request", Status.BAD_REQUEST)

        implicit val request: RequestHeader = mock[RequestHeader]

        val expected = GetAFTDetails(pstr, startDate, Status.BAD_REQUEST, None)

        val resultHandler = service.sendAFTDetailsAuditEvent(pstr, startDate)
        resultHandler.isDefinedAt(Failure(error)).shouldBe(true)
        resultHandler(Failure(error))

        verify(auditService, times(1)).sendEvent(expected)
      }
    }

    "sendOptionAFTDetailsAuditEvent" - {

      "should send an audit event with a successful response" in {
        val auditService = mock[AuditService]
        val service = new GetAFTDetailsAuditService(auditService)

        val pstr = "12345678AA"
        val startDate = "2020-04-01"
        val data = Json.obj("aftDetails" -> Json.obj("aftStatus" -> "Success"))

        implicit val request: RequestHeader = mock[RequestHeader]

        val expected = GetAFTDetails(pstr, startDate, Status.OK, Some(data))

        val resultHandler = service.sendOptionAFTDetailsAuditEvent(pstr, startDate)
        resultHandler.isDefinedAt(Success(Some(data))).shouldBe(true)
        resultHandler(Success(Some(data)))

        verify(auditService, times(1)).sendEvent(expected)
      }

      "should send an audit event with an UpstreamErrorResponse" in {
        val auditService = mock[AuditService]
        val service = new GetAFTDetailsAuditService(auditService)

        val pstr = "12345678AA"
        val startDate = "2020-04-01"
        val error = UpstreamErrorResponse("Error", Status.BAD_GATEWAY)

        implicit val request: RequestHeader = mock[RequestHeader]

        val expected = GetAFTDetails(pstr, startDate, Status.BAD_GATEWAY, None)

        val resultHandler = service.sendOptionAFTDetailsAuditEvent(pstr, startDate)
        resultHandler.isDefinedAt(Failure(error)).shouldBe(true)
        resultHandler(Failure(error))

        verify(auditService, times(1)).sendEvent(expected)
      }

      "should send an audit event with a HttpException" in {
        val auditService = mock[AuditService]
        val service = new GetAFTDetailsAuditService(auditService)

        val pstr = "12345678AA"
        val startDate = "2020-04-01"
        val error = new HttpException("Bad request", Status.BAD_REQUEST)

        implicit val request: RequestHeader = mock[RequestHeader]

        val expected = GetAFTDetails(pstr, startDate, Status.BAD_REQUEST, None)

        val resultHandler = service.sendOptionAFTDetailsAuditEvent(pstr, startDate)
        resultHandler.isDefinedAt(Failure(error)).shouldBe(true)
        resultHandler(Failure(error))

        verify(auditService, times(1)).sendEvent(expected)
      }
    }
  }
}

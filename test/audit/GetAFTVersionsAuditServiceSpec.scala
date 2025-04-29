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

package audit

import org.mockito.Mockito.{times, verify}
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.mockito.MockitoSugar
import play.api.mvc.RequestHeader
import uk.gov.hmrc.http.{HttpException, UpstreamErrorResponse}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.Failure

class GetAFTVersionsAuditServiceSpec extends AnyFreeSpec with Matchers with MockitoSugar {
  "sendAFTVersionsAuditEvent" - {

    "send an audit event with UpstreamErrorResponse" in {
      val auditService = mock[AuditService]
      val service = new GetAFTVersionsAuditService(auditService)

      val pstr = "12345678AA"
      val startDate = "2020-04-01"
      val error = UpstreamErrorResponse("Error", 502)

      implicit val request: RequestHeader = mock[RequestHeader]

      val resultHandler = service.sendAFTVersionsAuditEvent(pstr, startDate)

      resultHandler.isDefinedAt(Failure(error)) shouldBe true
      resultHandler(Failure(error))

      val expected = GetAFTVersions(pstr, startDate, error.statusCode, None)
      verify(auditService, times(1)).sendEvent(expected)
    }

    "send an audit event with HttpException" in {
      val auditService = mock[AuditService]
      val service = new GetAFTVersionsAuditService(auditService)

      val pstr = "87654321BB"
      val startDate = "2021-01-01"
      val error = new HttpException("Bad request", 400)

      implicit val request: RequestHeader = mock[RequestHeader]

      val resultHandler = service.sendAFTVersionsAuditEvent(pstr, startDate)

      resultHandler.isDefinedAt(Failure(error)) shouldBe true
      resultHandler(Failure(error))

      val expected = GetAFTVersions(pstr, startDate, error.responseCode, None)
      verify(auditService, times(1)).sendEvent(expected)
    }
  }
}

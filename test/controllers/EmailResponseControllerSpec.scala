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

import audit.{AuditService, EmailAuditEvent}
import models.enumeration.JourneyType.AFT_RETURN
import models.{Sent, _}
import org.joda.time.DateTime
import org.mockito.Matchers.any
import org.mockito.Mockito.{never, times, verify, when}
import org.mockito.{ArgumentCaptor, Mockito}
import org.scalatest.{AsyncWordSpec, BeforeAndAfterEach, MustMatchers}
import org.scalatestplus.mockito.MockitoSugar
import play.api.Application
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.Json
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.auth.core._
import uk.gov.hmrc.http.UnauthorizedException

import scala.concurrent.Future

class EmailResponseControllerSpec extends AsyncWordSpec with MustMatchers with MockitoSugar with BeforeAndAfterEach {

  import EmailResponseControllerSpec._

  private val mockAuditService = mock[AuditService]
  private val mockAuthConnector = mock[AuthConnector]

  private val application: Application = new GuiceApplicationBuilder()
    .configure(conf = "auditing.enabled" -> false, "metrics.enabled" -> false, "metrics.jvm" -> false).
    overrides(Seq(
      bind[AuthConnector].toInstance(mockAuthConnector),
      bind[AuditService].toInstance(mockAuditService)
    )).build()

  private val injector = application.injector
  private val controller = injector.instanceOf[EmailResponseController]

  override def beforeEach(): Unit = {
    Mockito.reset(mockAuditService, mockAuthConnector)
    when(mockAuthConnector.authorise[Enrolments](any(), any())(any(), any()))
      .thenReturn(Future.successful(enrolments))
  }

  "EmailResponseController" must {

    "respond OK when given EmailEvents" in {
      val result = controller.retrieveStatus(AFT_RETURN)(fakeRequest.withBody(Json.toJson(emailEvents)))

      status(result) mustBe OK
      verify(mockAuditService, times(2)).sendEvent(eventCaptor.capture())(any(), any())
      eventCaptor.getValue mustEqual EmailAuditEvent("A0000000", Delivered, AFT_RETURN)
    }

    "respond with BAD_REQUEST when not given EmailEvents" in {
      val result = controller.retrieveStatus(AFT_RETURN)(fakeRequest.withBody(Json.obj("name" -> "invalid")))

      verify(mockAuditService, never()).sendEvent(any())(any(), any())
      status(result) mustBe BAD_REQUEST
    }

    "throw AuthorisationException if there are no enrolments" in {
      when(mockAuthConnector.authorise[Enrolments](any(), any())(any(), any()))
        .thenReturn(Future.successful(Enrolments(Set.empty)))

      recoverToExceptionIf[UnauthorizedException] {
        controller.retrieveStatus(AFT_RETURN)(fakeRequest.withBody(Json.toJson(emailEvents)))
      } map { response =>
        response.message mustEqual "Not Authorised - Unable to retrieve enrolments"
      }
    }
  }
}

object EmailResponseControllerSpec {
  private val fakeRequest = FakeRequest("", "")
  private val enrolments = Enrolments(Set(
    Enrolment("HMRC-PODS-ORG", Seq(
      EnrolmentIdentifier("PSAID", "A0000000")
    ), "Activated", None)
  ))
  private val eventCaptor = ArgumentCaptor.forClass(classOf[EmailAuditEvent])
  private val emailEvents = EmailEvents(Seq(EmailEvent(Sent, DateTime.now()), EmailEvent(Delivered, DateTime.now())))
}

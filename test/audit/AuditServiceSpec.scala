/*
 * Copyright 2021 HM Revenue & Customs
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

import org.mockito.ArgumentCaptor
import org.mockito.Matchers.any
import org.mockito.Mockito._
import org.scalatest.{Inside, MustMatchers, WordSpec}
import org.scalatestplus.mockito.MockitoSugar
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.{JsObject, Json}
import play.api.mvc.AnyContentAsEmpty
import play.api.test.FakeRequest
import repository.AftDataCacheRepository
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.audit.http.connector.AuditResult.Success
import uk.gov.hmrc.play.audit.model.ExtendedDataEvent

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class AuditServiceSpec extends WordSpec with MustMatchers with Inside {

  import AuditServiceSpec._

  "AuditServiceImpl" must {
    "construct and send the correct event" in {

      implicit val request: FakeRequest[AnyContentAsEmpty.type] = fakeRequest()

      val event = TestAuditEvent("test-audit-payload")
      val templateCaptor = ArgumentCaptor.forClass(classOf[ExtendedDataEvent])

      when(mockAuditConnector.sendExtendedEvent(any())(any(), any()))
        .thenReturn(Future.successful(Success))
      auditService().sendEvent(event)

      verify(mockAuditConnector, times(1)).sendExtendedEvent(templateCaptor.capture())
      inside(templateCaptor.getValue) {
        case ExtendedDataEvent(auditSource, auditType, _, _, detail, _) =>
          auditSource mustBe appName
          auditType mustBe "TestAuditEvent"
          detail mustBe Json.obj(
            "payload" -> "test-audit-payload"
          )
      }
    }

  }

}

object AuditServiceSpec extends MockitoSugar {

  private val mockAuditConnector: AuditConnector = mock[AuditConnector]
  private val mockDataCacheRepository = mock[AftDataCacheRepository]

  private val app = new GuiceApplicationBuilder()
    .overrides(
      bind[AuditConnector].toInstance(mockAuditConnector),
      bind[AftDataCacheRepository].toInstance(mockDataCacheRepository)
    )
    .build()

  def fakeRequest(): FakeRequest[AnyContentAsEmpty.type] = FakeRequest("", "")

  def auditService(): AuditService = app.injector.instanceOf[AuditService]

  def appName: String = app.configuration.underlying.getString("appName")

}

//noinspection ScalaDeprecation

case class TestAuditEvent(payload: String) extends AuditEvent {

  override def auditType: String = "TestAuditEvent"

  override def details: JsObject =
    Json.obj(
      "payload" -> payload
    )

}

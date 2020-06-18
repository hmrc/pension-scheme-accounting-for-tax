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

import connectors.FinancialStatementConnector
import models.PsaFS
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
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.http._
import utils.JsonFileReader

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class FinancialStatementControllerSpec extends AsyncWordSpec with MustMatchers with MockitoSugar with BeforeAndAfter with JsonFileReader {

  import FinancialStatementControllerSpec._

  implicit val hc: HeaderCarrier = HeaderCarrier()

  private val fakeRequest = FakeRequest("GET", "/")
  private val fakeRequestWithHeaders = fakeRequest.withHeaders(("psaId", psaId))
  private val mockFSConnector = mock[FinancialStatementConnector]
  private val authConnector: AuthConnector = mock[AuthConnector]

  val modules: Seq[GuiceableModule] =
    Seq(
      bind[AuthConnector].toInstance(authConnector),
      bind[FinancialStatementConnector].toInstance(mockFSConnector)
    )

  val application: Application = new GuiceApplicationBuilder()
    .configure(conf = "auditing.enabled" -> false, "metrics.enabled" -> false, "metrics.jvm" -> false).
    overrides(modules: _*).build()

  before {
    reset(mockFSConnector, authConnector)
    when(authConnector.authorise[Option[String]](any(), any())(any(), any())) thenReturn Future.successful(Some("Ext-137d03b9-d807-4283-a254-fb6c30aceef1"))
  }

  "getAftDetails" must {

    "return OK when the details are returned based on pstr, start date and AFT version" in {

      val controller = application.injector.instanceOf[FinancialStatementController]

      when(mockFSConnector.getPsaFS(Matchers.eq(psaId))(any(), any(), any())).thenReturn(
        Future.successful(psaFSResponse))

      val result = controller.psaStatement()(fakeRequestWithHeaders)

      status(result) mustBe OK
      contentAsJson(result) mustBe Json.toJson(psaFSResponse)
    }

    "throw BadRequestException when PSTR is not present in the header" in {

      val controller = application.injector.instanceOf[FinancialStatementController]

      recoverToExceptionIf[BadRequestException] {
        controller.psaStatement()(fakeRequest)
      } map { response =>
        response.responseCode mustBe BAD_REQUEST
        response.getMessage mustBe "Bad Request with missing PSA ID"
      }
    }

    "throw generic exception when any other exception returned from Des" in {

      val controller = application.injector.instanceOf[FinancialStatementController]

      when(mockFSConnector.getPsaFS(Matchers.eq(psaId))(any(), any(), any())).thenReturn(
        Future.failed(new Exception("Generic Exception")))

      recoverToExceptionIf[Exception] {
        controller.psaStatement()(fakeRequestWithHeaders)
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


object FinancialStatementControllerSpec {
  private val psaId = "test-psa-id"

  private val psaFSResponse: Seq[PsaFS] = Seq(
    PsaFS(
      chargeReference = "XY002610150184",
      chargeType = "AFT Initial LFP",
      dueDate = Some(LocalDate.parse("2020-02-15")),
      outstandingAmount = 56049.08,
      stoodOverAmount = 25089.08,
      amountDue = 1029.05,
      periodStartDate =  LocalDate.parse("2020-04-01"),
      periodEndDate =  LocalDate.parse("2020-06-30"),
      pstr = "24000040IN"
    ),
    PsaFS(
      chargeReference = "XY002610150184",
      chargeType = "AFT Initial LFP",
      dueDate = Some(LocalDate.parse("2020-02-15")),
      outstandingAmount = 56049.08,
      stoodOverAmount = 25089.08,
      amountDue = 1029.05,
      periodStartDate =  LocalDate.parse("2020-04-01"),
      periodEndDate =  LocalDate.parse("2020-06-30"),
      pstr = "24000041IN"
    )
  )
}


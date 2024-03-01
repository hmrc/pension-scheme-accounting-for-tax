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

package controllers

import connectors.{AFTConnector, FinancialStatementConnector}
import models._
import org.mockito.ArgumentMatchers
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{reset, when}
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
import services.FeatureToggleService
import uk.gov.hmrc.auth.core.retrieve.~
import uk.gov.hmrc.auth.core.syntax.retrieved.authSyntaxForRetrieved
import uk.gov.hmrc.auth.core.{AuthConnector, Enrolment, EnrolmentIdentifier, Enrolments}
import uk.gov.hmrc.http._
import utils.JsonFileReader

import java.time.LocalDate
import scala.concurrent.Future

class FinancialStatementControllerSpec extends AsyncWordSpec with Matchers with MockitoSugar with BeforeAndAfter with JsonFileReader {

  import FinancialStatementControllerSpec._

  implicit val hc: HeaderCarrier = HeaderCarrier()

  private val mockFSConnector = mock[FinancialStatementConnector]
  private val authConnector: AuthConnector = mock[AuthConnector]
  private val mockAFTConnector = mock[AFTConnector]
  private val mockFeatureToggle = mock[FeatureToggleService]

  private val modules: Seq[GuiceableModule] =
    Seq(
      bind[AuthConnector].toInstance(authConnector),
      bind[FinancialStatementConnector].toInstance(mockFSConnector),
      bind[AFTConnector].toInstance(mockAFTConnector),
      bind[AdminDataRepository].toInstance(mock[AdminDataRepository]),
      bind[AftBatchedDataCacheRepository].toInstance(mock[AftBatchedDataCacheRepository]),
      bind[AftOverviewCacheRepository].toInstance(mock[AftOverviewCacheRepository]),
      bind[FileUploadReferenceCacheRepository].toInstance(mock[FileUploadReferenceCacheRepository]),
      bind[FileUploadOutcomeRepository].toInstance(mock[FileUploadOutcomeRepository]),
      bind[FinancialInfoCacheRepository].toInstance(mock[FinancialInfoCacheRepository]),
      bind[FinancialInfoCreditAccessRepository].toInstance(mock[FinancialInfoCreditAccessRepository]),
      bind[FeatureToggleService].toInstance(mockFeatureToggle)
    )

  private val application: Application = new GuiceApplicationBuilder()
    .configure(conf = "auditing.enabled" -> false, "metrics.enabled" -> false, "metrics.jvm" -> false).
    overrides(modules: _*).build()

  private def expectedAuthorisations(psaId: String = "A2100052"): Option[String] ~ Enrolments = {
    Option("Ext-137d03b9-d807-4283-a254-fb6c30aceef1") and
      Enrolments(
        Set(
          Enrolment("HMRC-PODS-ORG", Seq(EnrolmentIdentifier("PSAID", psaId)), "Activated", None)
        )
      )
  }

  before {
    when(mockFeatureToggle.getToggle(any())).thenReturn(Future.successful(None))
    reset(mockFSConnector)
    reset(authConnector)
  }

  "psaStatement" must {

    "return OK when the details are returned based on pstr, start date and AFT version" in {
      when(authConnector.authorise[Option[String]](any(), any())(any(), any())) thenReturn Future.successful(Some("Ext-137d03b9-d807-4283-a254-fb6c30aceef1"))
      val controller = application.injector.instanceOf[FinancialStatementController]

      when(mockFSConnector.getPsaFS(ArgumentMatchers.eq(psaId))(any(), any(), any())).thenReturn(
        Future.successful(psaFSResponse))

      val result = controller.psaStatement()(fakeRequestWithPsaId)

      status(result) mustBe OK
      contentAsJson(result) mustBe Json.toJson(psaFSResponse)
    }

    "throw BadRequestException when PSTR is not present in the header" in {
      when(authConnector.authorise[Option[String]](any(), any())(any(), any())) thenReturn Future.successful(Some("Ext-137d03b9-d807-4283-a254-fb6c30aceef1"))
      val controller = application.injector.instanceOf[FinancialStatementController]

      recoverToExceptionIf[BadRequestException] {
        controller.psaStatement()(fakeRequest)
      } map { response =>
        response.responseCode mustBe BAD_REQUEST
        response.getMessage mustBe "Bad Request with missing psaId"
      }
    }

    "throw generic exception when any other exception returned from Des" in {
      when(authConnector.authorise[Option[String]](any(), any())(any(), any())) thenReturn Future.successful(Some("Ext-137d03b9-d807-4283-a254-fb6c30aceef1"))
      val controller = application.injector.instanceOf[FinancialStatementController]

      when(mockFSConnector.getPsaFS(ArgumentMatchers.eq(psaId))(any(), any(), any())).thenReturn(
        Future.failed(new Exception("Generic Exception")))

      recoverToExceptionIf[Exception] {
        controller.psaStatement()(fakeRequestWithPsaId)
      } map { response =>
        response.getMessage mustBe "Generic Exception"
      }
    }
  }

  "schemeStatement" must {
    val controller = application.injector.instanceOf[FinancialStatementController]

    "return OK with added data" in {
      when(authConnector.authorise[Option[String] ~ Enrolments](any(), any())(any(), any())).thenReturn(Future.successful(expectedAuthorisations()))
      when(mockFeatureToggle.getToggle(any())).thenReturn(Future.successful(Some(ToggleDetails("new-financial-statement", None, isEnabled = true))))
      when(mockFSConnector.getSchemeFS(ArgumentMatchers.eq(pstr))(any(), any(), any())).thenReturn(
        Future.successful(schemeModelAfterUpdateWithAFTDetails))

      val result = controller.schemeStatement()(fakeRequestWithPstr)

      status(result) mustBe OK
      contentAsJson(result) mustBe Json.toJson(schemeModelAfterUpdateWithAFTDetails)
    }
  }

  "updateChargeType" must {
    val controller = application.injector.instanceOf[FinancialStatementController]
    listOfChargesAndAssociatedCredits.foreach { pairOfChargeAndCredit =>
      val (charge, credit) = pairOfChargeAndCredit
      s"flip ${charge.seqSchemeFSDetail.head.chargeType} to ${credit.seqSchemeFSDetail.head.chargeType} if negative amountDue" in {
        when(authConnector.authorise[Option[String] ~ Enrolments](any(), any())(any(), any())).thenReturn(Future.successful(expectedAuthorisations()))
        when(mockFeatureToggle.getToggle(any())).thenReturn(Future.successful(Some(ToggleDetails("new-financial-statement", None, isEnabled = true))))
        when(mockFSConnector.getSchemeFS(ArgumentMatchers.eq(pstr))(any(), any(), any())).thenReturn(
          Future.successful(charge))
        val result = controller.schemeStatement()(fakeRequestWithPstr)
        status(result) mustBe OK
        contentAsJson(result) mustBe Json.toJson(credit)
      }
    }
  }
}

object FinancialStatementControllerSpec {
  private val psaId = "test-psa-id"
  private val pstr = "test-pstr"
  private val fakeRequest = FakeRequest("GET", "/")
  private val fakeRequestWithPsaId = fakeRequest.withHeaders(("psaId", psaId))
  private val fakeRequestWithPstr = fakeRequest.withHeaders(("pstr", pstr))

  private val psaFSDetailResponse: Seq[PsaFSDetail] = Seq(
    PsaFSDetail(
      index = 0,
      chargeReference = "XY002610150184",
      chargeType = "AFT Initial LFP",
      dueDate = Some(LocalDate.parse("2020-02-15")),
      totalAmount = 80000.00,
      outstandingAmount = 56049.08,
      stoodOverAmount = 25089.08,
      accruedInterestTotal = 0.00,
      amountDue = 1029.05,
      periodStartDate = LocalDate.parse("2020-04-01"),
      periodEndDate = LocalDate.parse("2020-06-30"),
      pstr = "24000040IN"
    )
  )
  private val psaFSResponse: PsaFS =
    PsaFS(inhibitRefundSignal = false, psaFSDetailResponse)

  private val schemeModelAfterUpdateWithAFTDetails: SchemeFS = SchemeFS(
    inhibitRefundSignal = false,
    seqSchemeFSDetail = Seq(
      SchemeFSDetail(
        index = 0,
        chargeReference = s"XY002610150184",
        chargeType = "Accounting for Tax Return",
        dueDate = Some(LocalDate.parse("2020-02-15")),
        totalAmount = 80000.00,
        amountDue = 1029.05,
        outstandingAmount = 56049.08,
        accruedInterestTotal = 100.05,
        formBundleNumber = Some("ww"),
        version = Some(2),
        receiptDate = Some(LocalDate.parse("2020-12-12")),
        stoodOverAmount = 25089.08,
        periodStartDate = Some(LocalDate.parse("2020-04-01")),
        periodEndDate = Some(LocalDate.parse("2020-06-30"))
      )
    )
  )

  private def schemeModelForFlipToCredit(charge: String): SchemeFS = SchemeFS(
    inhibitRefundSignal = false,
    seqSchemeFSDetail = Seq(
      SchemeFSDetail(
        index = 0,
        chargeReference = s"XY002610150184",
        chargeType = charge,
        dueDate = Some(LocalDate.parse("2020-02-15")),
        totalAmount = 80000.00,
        amountDue = -1029.05,
        outstandingAmount = 56049.08,
        accruedInterestTotal = 100.05,
        formBundleNumber = Some("ww"),
        stoodOverAmount = 25089.08,
        periodStartDate = Some(LocalDate.parse("2020-04-01")),
        periodEndDate = Some(LocalDate.parse("2020-06-30"))
      )
    )
  )

  private def schemeModelAfterUpdateToCredit(credit: String): SchemeFS = SchemeFS(
    inhibitRefundSignal = false,
    seqSchemeFSDetail = Seq(
      SchemeFSDetail(
        index = 0,
        chargeReference = "XY002610150184",
        chargeType = credit,
        dueDate = Some(LocalDate.parse("2020-02-15")),
        totalAmount = 80000.00,
        amountDue = -1029.05,
        outstandingAmount = 56049.08,
        accruedInterestTotal = 100.05,
        formBundleNumber = Some("ww"),
        stoodOverAmount = 25089.08,
        periodStartDate = Some(LocalDate.parse("2020-04-01")),
        periodEndDate = Some(LocalDate.parse("2020-06-30"))
      )
    )
  )

  private val listOfChargesAndAssociatedCredits = Seq(
    (schemeModelForFlipToCredit("Accounting for Tax Return"), schemeModelAfterUpdateToCredit("Accounting for Tax Return credit")),
    (schemeModelForFlipToCredit("Overseas Transfer Charge"), schemeModelAfterUpdateToCredit("Overseas Transfer Charge credit")),
    (schemeModelForFlipToCredit("Accounting for Tax assessment"), schemeModelAfterUpdateToCredit("Accounting for Tax assessment credit")),
    (schemeModelForFlipToCredit("Overseas Transfer Charge assessment"), schemeModelAfterUpdateToCredit("Overseas Transfer Charge assessment credit"))
  )
}


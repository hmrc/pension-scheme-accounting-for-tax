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

package controllers.actions

import org.mockito.ArgumentMatchers
import org.mockito.Mockito.{reset, when}
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach}
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.PlaySpec
import play.api.http.Status.{FORBIDDEN, OK}
import play.api.mvc.AnyContent
import play.api.mvc.Results.Ok
import play.api.test.FakeRequest
import play.api.test.Helpers.{contentAsString, status}
import uk.gov.hmrc.domain.PsaId
import uk.gov.hmrc.http.HttpException

import scala.concurrent.Future

class PsaSchemeActionSpec extends PlaySpec with MockitoSugar with BeforeAndAfterAll with BeforeAndAfterEach {

  private val mockSchemeService = mock[SchemeService]
  override def beforeAll(): Unit = {
    super.beforeAll()
  }

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockSchemeService)
  }

  private val authRequest = PsaAuthRequest[AnyContent](FakeRequest("", ""), PsaId(AuthUtils.psaId),  AuthUtils.externalId)

  private val srn = AuthUtils.srn
  private def getResult = {
    new PsaSchemeAuthAction(mockSchemeService)
      .apply(srn)
      .invokeBlock(authRequest, { _: PsaAuthRequest[AnyContent] => Future.successful(Ok("success")) })
  }

  private def mockCheckForAssociation = {
    when(mockSchemeService.isAssociated(ArgumentMatchers.eq(srn), ArgumentMatchers.eq(Left(PsaId(AuthUtils.psaId))))(ArgumentMatchers.any(), ArgumentMatchers.any()))
  }

  "PsaSchemeActionSpec" must {
    "return success response if pension scheme is associated with srn" in {
      mockCheckForAssociation.thenReturn(Future.successful(Right(true)))
      val result = getResult
      status(result) mustBe OK
      contentAsString(result) mustBe "success"
    }

    "return Forbidden if pension scheme is not associated with srn" in {
      mockCheckForAssociation.thenReturn(Future.successful(Right(false)))
      status(getResult) mustBe FORBIDDEN
    }

    "return recover from error if association call fails" in {
      mockCheckForAssociation.thenReturn(Future.successful(Left(new HttpException("failed", 500))))
      getResult.failed.map { _ mustBe new Exception("failed") }
    }
  }
}

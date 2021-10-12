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

package connectors

import config.AppConfig
import org.mockito.Mockito.when
import org.scalatest.{BeforeAndAfterEach, MustMatchers, WordSpec}
import org.scalatestplus.mockito.MockitoSugar
import uk.gov.hmrc.http.{HeaderCarrier, RequestId}

class HeaderUtilsSpec extends WordSpec with MockitoSugar with MustMatchers with BeforeAndAfterEach {
  private val mockConfig = mock[AppConfig]
  private val headerUtils = new HeaderUtils(mockConfig)
  private val desEnv = "test-env-des"
  private val desAuth = "test-auth-des"
  private val ifEnv = "test-env-if"
  private val ifAuth = "test-auth-if"

  override def beforeEach(): Unit = {
    when(mockConfig.integrationframeworkAuthorization).thenReturn(ifAuth)
    when(mockConfig.integrationframeworkEnvironment).thenReturn(ifEnv)
    when(mockConfig.desEnvironment).thenReturn(desEnv)
    when(mockConfig.authorization).thenReturn(desAuth)
  }

  "desHeader" must {

    "return the correct headers" in {
      val hc: HeaderCarrier = HeaderCarrier(requestId = Some(RequestId("govuk-tax-4725c811-9251-4c06-9b8f-f1d84659b2df")))
      val result = headerUtils.desHeader(hc)
      result(0) mustBe "Environment" -> desEnv
      result(1) mustBe "Authorization" -> desAuth
      result(2) mustBe "Content-Type" -> "application/json"
    }
  }

  "integrationFrameworkHeader" must {

    "return the correct headers" in {
      val hc: HeaderCarrier = HeaderCarrier(requestId = Some(RequestId("govuk-tax-4725c811-9251-4c06-9b8f-f1d84659b2df")))
      val result = headerUtils.integrationFrameworkHeader(hc)
      result(0) mustBe "Environment" -> ifEnv
      result(1) mustBe "Authorization" -> ifAuth
      result(2) mustBe "Content-Type" -> "application/json"
    }
  }

  "getCorrelationId" must {
    "return the correct CorrelationId when the request Id is more than 32 characters" in {
      val requestId = Some("govuk-tax-4725c811-9251-4c06-9b8f-f1d84659b2dfe")
      val result = headerUtils.getCorrelationId(requestId)
      result mustBe "4725c811-9251-4c06-9b8f-f1d84659b2df"
    }


    "return the correct CorrelationId when the request Id is less than 32 characters" in {
      val requestId = Some("govuk-tax-4725c811-9251-4c06-9b8f-f1")
      val result = headerUtils.getCorrelationId(requestId)
      result mustBe "4725c811-9251-4c06-9b8f-f1"
    }

    "return the correct CorrelationId when the request Id does not have gov-uk-tax or -" in {
      val requestId = Some("4725c81192514c069b8ff1")
      val result = headerUtils.getCorrelationId(requestId)
      result mustBe "4725c81192514c069b8ff1"
    }
  }
}

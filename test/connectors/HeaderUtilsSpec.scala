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

package connectors

import config.AppConfig
import org.mockito.Mockito.when
import org.scalatest.BeforeAndAfterEach
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.mockito.MockitoSugar

class HeaderUtilsSpec extends AnyWordSpec with MockitoSugar with Matchers with BeforeAndAfterEach {
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
      val result = headerUtils.desHeader
      result.head.`mustBe`("Environment" -> desEnv)
      result(1).`mustBe`("Authorization" -> desAuth)
      result(2).`mustBe`("Content-Type" -> "application/json")
    }
  }

  "integrationFrameworkHeader" must {

    "return the correct headers" in {
      val result = headerUtils.integrationFrameworkHeader
      result.head.`mustBe`("Environment" -> ifEnv)
      result(1).`mustBe`("Authorization" -> ifAuth)
      result(2).`mustBe`("Content-Type" -> "application/json")
    }
  }

  "call getCorrelationId" must {
    "return a CorrelationId of the correct size" in {
      val result = headerUtils.getCorrelationId
      result.length mustEqual headerUtils.maxLengthCorrelationIdIF
    }
  }
}

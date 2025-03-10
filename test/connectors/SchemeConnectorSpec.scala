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

import com.github.tomakehurst.wiremock.client.WireMock._
import models.SchemeReferenceNumber
import org.scalatest.flatspec.AsyncFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.{EitherValues, OptionValues, RecoverMethods}
import org.scalatestplus.mockito.MockitoSugar
import play.api.http.Status.BAD_REQUEST
import play.api.libs.json._
import uk.gov.hmrc.domain.PsaId
import uk.gov.hmrc.http.{BadRequestException, HeaderCarrier}
import utils.WireMockHelper

class SchemeConnectorSpec extends AsyncFlatSpec
  with Matchers
  with WireMockHelper
  with MockitoSugar
  with OptionValues
  with RecoverMethods
  with EitherValues {

  override protected def portConfigKey: String = "microservice.services.pensions-scheme.port"

  private val checkForAssociationUrl = "/pensions-scheme/is-psa-associated"
  private val srn: SchemeReferenceNumber = SchemeReferenceNumber("S0987654321")
  private val psaId: PsaId = PsaId("A7654321")
  private implicit val hc: HeaderCarrier = HeaderCarrier()

  lazy val connector: SchemeConnector = injector.instanceOf[SchemeConnector]

  "SchemeConnector checkForAssociation" should "handle OK (200)" in {

    server.stubFor(
      get(urlEqualTo(checkForAssociationUrl))
        .withHeader("Content-Type", equalTo("application/json"))
        .withHeader("psaId", equalTo(psaId.value))
        .withHeader("schemeReferenceNumber", equalTo(srn))
        .willReturn(
          ok(JsBoolean(true).toString())
            .withHeader("Content-Type", "application/json")
        )
    )

    connector.checkForAssociation(Left(psaId), srn) map { response =>
      response.value shouldBe true
    }

  }

  it should "relay BadRequestException when headers are missing" in {

    server.stubFor(
      get(urlEqualTo(checkForAssociationUrl))
        .withHeader("Content-Type", equalTo("application/json"))
        .willReturn(
          badRequest
            .withBody("Bad Request with missing parameters PSA Id or SRN")
        )
    )

    recoverToExceptionIf[BadRequestException] {
      connector.checkForAssociation(Left(psaId), srn)
    } map { response =>
      response.responseCode shouldBe BAD_REQUEST
      response.message should include("Bad Request with missing parameters PSA Id or SRN")
    }

  }
}

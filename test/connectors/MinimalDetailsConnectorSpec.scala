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

package connectors

import com.github.tomakehurst.wiremock.client.WireMock.{aResponse, get, ok, urlEqualTo}
import controllers.actions.PsaPspAuthRequest
import models.{IndividualDetails, MinimalDetails}
import org.scalatest.RecoverMethods
import org.scalatest.flatspec.AsyncFlatSpec
import org.scalatest.matchers.must.Matchers
import play.api.Application
import play.api.http.Status.FORBIDDEN
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.Json
import play.api.mvc.AnyContent
import play.api.test.FakeRequest
import uk.gov.hmrc.domain.{PsaId, PspId}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.test.WireMockSupport
import utils.AuthUtils

class MinimalDetailsConnectorSpec
  extends AsyncFlatSpec
    with Matchers
    with RecoverMethods
    with WireMockSupport {

  protected lazy val app: Application =
    new GuiceApplicationBuilder()
      .configure(
        "microservice.services.pension-administrator.port" -> wireMockServer.port(),
        "auditing.enabled" -> false,
        "metrics.enabled" -> false
      )
      .build()

  private implicit lazy val hc: HeaderCarrier =
    HeaderCarrier()
  private implicit val request: PsaPspAuthRequest[AnyContent] =
    PsaPspAuthRequest(FakeRequest(), Some(PsaId(AuthUtils.psaId)), Some(PspId(AuthUtils.pspId)), "id")
  private lazy val connector: MinimalDetailsConnector =
    app.injector.instanceOf[MinimalDetailsConnector]
  private val minimalPsaDetailsUrl =
    "/pension-administrator/get-minimal-details-self"

  private val validOrgJson =
    Json.stringify(Json.obj(
      "organisationName" -> "test ltd"
    ))

  private val validIndJson =
    Json.stringify(Json.obj(
      "individualDetails" -> Json.obj(
        "firstName" -> "firstName",
        "lastName" -> "lastName"
      )
    ))

  private val invalidJson =
    Json.stringify(Json.obj("blah" -> "blah"))

  "getMinimalDetails" must "return successfully when the get min details has returned OK for org" in {
    wireMockServer.stubFor(
      get(urlEqualTo(minimalPsaDetailsUrl))
        .willReturn(ok(validOrgJson).withHeader("Content-Type", "application/json"))
    )

    connector
      .getMinimalDetails
      .map(_.mustBe(MinimalDetails(Some("test ltd"), None)))
  }

  it must "return successfully when the get min details has returned OK for ind" in {
    wireMockServer.stubFor(
      get(urlEqualTo(minimalPsaDetailsUrl))
        .willReturn(ok(validIndJson).withHeader("Content-Type", "application/json"))
    )

    connector
      .getMinimalDetails
      .map(_.mustBe(MinimalDetails(None, Some(IndividualDetails("firstName", None, "lastName")))))
  }

  it must "return Exception when the body contains DELIMITED_PSAID" in {
    wireMockServer.stubFor(
      get(urlEqualTo(minimalPsaDetailsUrl))
        .willReturn(ok(invalidJson).withHeader("Content-Type", "application/json"))
    )

    recoverToExceptionIf[Exception](connector.getMinimalDetails).map {
      ex =>
        ex.mustBe(a[Exception])
        ex.getMessage.mustBe("Neither individualDetails or organisationName returned from min details")
    }
  }

  it must "return DelimitedAdminException when the body contains DELIMITED_PSAID" in {
    wireMockServer.stubFor(
      get(urlEqualTo(minimalPsaDetailsUrl))
        .willReturn(
          aResponse()
            .withStatus(FORBIDDEN)
            .withBody("DELIMITED_PSAID")
            .withHeader("Content-Type", "application/json")
        )
    )

    recoverToExceptionIf[DelimitedAdminException](connector.getMinimalDetails).map {
      ex =>
        ex.mustBe(a[DelimitedAdminException])
        ex.getMessage.mustBe("The administrator has already de-registered. The minimal details API has returned a DELIMITED PSA response")
    }
  }
}

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

package connectors

import java.util.UUID.randomUUID

import com.google.inject.Inject
import config.AppConfig
import models.PsaFS
import play.Logger
import play.api.libs.json._
import play.api.mvc.RequestHeader
import uk.gov.hmrc.http._
import uk.gov.hmrc.play.bootstrap.http.HttpClient

import scala.concurrent.{ExecutionContext, Future}

class FinancialStatementConnector @Inject()(http: HttpClient,
                                            config: AppConfig) extends HttpErrorFunctions {

  def getPsaFS(psaId: String)(implicit headerCarrier: HeaderCarrier, ec: ExecutionContext, request: RequestHeader): Future[Seq[PsaFS]] = {

    val url: String = config.psaFinancialStatementUrl.format(psaId)
    implicit val hc: HeaderCarrier = HeaderCarrier(extraHeaders = desHeader(implicitly[HeaderCarrier](headerCarrier)))

    http.GET[JsValue](url)(implicitly, hc, implicitly).map { responseJson =>

      responseJson.validate[Seq[PsaFS]](Reads.seq(PsaFS.rds)) match {
        case JsSuccess(statements, _) => statements
        case JsError(errors) => throw JsResultException(errors)
      }
    }
  }


  private def desHeader(implicit hc: HeaderCarrier): Seq[(String, String)] = {
    val requestId = getCorrelationId(hc.requestId.map(_.value))

    Seq("Environment" -> config.desEnvironment, "Authorization" -> config.authorization,
      "Content-Type" -> "application/json", "CorrelationId" -> requestId)
  }

  def getCorrelationId(requestId: Option[String]): String = {
    requestId.getOrElse {
      Logger.error("No Request Id found")
      randomUUID.toString
    }.replaceAll("(govuk-tax-)", "").slice(0, 36)
  }
}

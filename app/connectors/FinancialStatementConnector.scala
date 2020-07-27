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

import com.google.inject.Inject
import config.AppConfig
import models.{PsaFS, SchemeFS}
import play.api.libs.json._
import play.api.mvc.RequestHeader
import uk.gov.hmrc.http._
import uk.gov.hmrc.play.bootstrap.http.HttpClient
import uk.gov.hmrc.http.HttpReads.Implicits._
import play.api.http.Status._

import scala.concurrent.{ExecutionContext, Future}

class FinancialStatementConnector @Inject()(http: HttpClient,
                                            config: AppConfig,
                                            headerUtils: HeaderUtils) extends HttpErrorFunctions {

  def getPsaFS(psaId: String)(implicit headerCarrier: HeaderCarrier, ec: ExecutionContext, request: RequestHeader): Future[Either[HttpResponse, Seq[PsaFS]]] = {

    val url: String = config.psaFinancialStatementUrl.format(psaId)
    implicit val hc: HeaderCarrier = HeaderCarrier(extraHeaders =
      headerUtils.integrationFrameworkHeader(implicitly[HeaderCarrier](headerCarrier)))

    http.GET[HttpResponse](url)(implicitly, hc, implicitly).map { response =>

      response.status match {
        case OK =>
          Json.toJson(response.body).validate[Seq[PsaFS]](Reads.seq(PsaFS.rds)) match {
            case JsSuccess(statements, _) => Right(statements)
            case JsError(errors) => throw JsResultException(errors)
          }
        case _ => Left(response)
      }
      //      responseJson.validate[Seq[PsaFS]](Reads.seq(PsaFS.rds)) match {
      //        case JsSuccess(statements, _) => statements
      //        case JsError(errors) => throw JsResultException(errors)
      //      }
    }
  }

  def getSchemeFS(pstr: String)(implicit headerCarrier: HeaderCarrier, ec: ExecutionContext, request: RequestHeader): Future[Seq[SchemeFS]] = {

    val url: String = config.schemeFinancialStatementUrl.format(pstr)
    implicit val hc: HeaderCarrier = HeaderCarrier(extraHeaders =
      headerUtils.integrationFrameworkHeader(implicitly[HeaderCarrier](headerCarrier)))

    http.GET[JsValue](url)(implicitly, hc, implicitly).map { responseJson =>

      responseJson.validate[Seq[SchemeFS]](Reads.seq(SchemeFS.rds)) match {
        case JsSuccess(statements, _) => statements
        case JsError(errors) => throw JsResultException(errors)
      }
    }
  }
}

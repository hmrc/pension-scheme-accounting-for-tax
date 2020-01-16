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
import play.Logger
import play.api.libs.json.{JsError, JsResultException, JsSuccess, JsValue}
import uk.gov.hmrc.http._
import uk.gov.hmrc.play.bootstrap.http.HttpClient

import scala.concurrent.{ExecutionContext, Future}

class DesConnector @Inject()(http: HttpClient, config: AppConfig) extends HttpErrorFunctions {

  def fileAFTReturn(pstr: String, data: JsValue)(implicit headerCarrier: HeaderCarrier,
                                                 ec: ExecutionContext): Future[HttpResponse] = {

    val fileAFTReturnURL = config.fileAFTReturnURL.format(pstr)
    implicit val hc: HeaderCarrier = HeaderCarrier(extraHeaders = desHeader(implicitly[HeaderCarrier](headerCarrier)))
    http.POST[JsValue, HttpResponse](fileAFTReturnURL, data)(implicitly, implicitly, hc, implicitly)
  }


  def getAftDetails(queryParams: String)(implicit headerCarrier: HeaderCarrier, ec: ExecutionContext): Future[JsValue] = {

    val getAftUrl: String = config.getAftDetailsUrl.format(queryParams)
    implicit val hc: HeaderCarrier = HeaderCarrier(extraHeaders = desHeader(implicitly[HeaderCarrier](headerCarrier)))

    http.GET[JsValue](getAftUrl)(implicitly, hc, implicitly)
  }

  def getAftVersions(pstr: String, startDate: String)(implicit headerCarrier: HeaderCarrier, ec: ExecutionContext): Future[Seq[Int]] = {

    val getAftVersionUrl: String = config.getAftVersionUrl.format(pstr, startDate)
    implicit val hc: HeaderCarrier = HeaderCarrier(extraHeaders = desHeader(implicitly[HeaderCarrier](headerCarrier)))

    http.GET[JsValue](getAftVersionUrl)(implicitly, hc, implicitly).map { responseJson =>
      (responseJson \ 0 \ "reportVersion").validate[Int] match {
        case JsSuccess(version, _) => Seq(version)
        case JsError(errors) => throw JsResultException(errors)
      }
    }
  }.recoverWith {
    case _: NotFoundException => Future.successful(Nil)
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
    }.replaceAll("(govuk-tax-|-)", "").slice(0, 32)
  }
}

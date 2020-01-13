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
import play.api.http.Status._
import play.api.libs.json.JsValue
import uk.gov.hmrc.http._
import uk.gov.hmrc.play.bootstrap.http.HttpClient

import scala.concurrent.{ExecutionContext, Future}

class DesConnector @Inject()(http: HttpClient, config: AppConfig) extends HttpErrorFunctions {

  private def fileAFTReturnURL(pstr:String):String = config.fileAFTReturnURL.format(pstr)


  def fileAFTReturn(pstr:String, data: JsValue)(implicit headerCarrier: HeaderCarrier,
                                                ec: ExecutionContext):Future[HttpResponse] = {
    http.POST(fileAFTReturnURL(pstr), data)
  }


  def getAftDetails(queryParams: String)(implicit headerCarrier: HeaderCarrier, ec: ExecutionContext): Future[Either[HttpException, JsValue]] = {


    val getAftUrl: String = config.getAftDetailsUrl.format(queryParams)
    implicit val hc: HeaderCarrier = HeaderCarrier(extraHeaders = desHeader(implicitly[HeaderCarrier](headerCarrier)))

    http.GET[HttpResponse](getAftUrl)(implicitly, hc, implicitly).map { response =>
      response.status match {

        case OK => Right(response.json)

        case FORBIDDEN if forbiddenResponseSeqGetAft.exists(response.body.contains(_)) =>
          Left(new ForbiddenException(response.body))

        case _ =>
          Left(handleErrorResponse("getAftDetails", getAftUrl, response, badResponseSeqGetAft))
      }
    }
  }

  private def handleErrorResponse(methodContext: String, url: String, response: HttpResponse, badResponseSeq: Seq[String]): HttpException =
    response.status match {
      case BAD_REQUEST if badResponseSeq.exists(response.body.contains(_)) => new BadRequestException(response.body)
      case NOT_FOUND => new NotFoundException(response.body)
      case status if is4xx(status) =>
        throw Upstream4xxResponse(upstreamResponseMessage(methodContext, url, status, response.body), status, status, response.allHeaders)
      case status if is5xx(status) =>
        throw Upstream5xxResponse(upstreamResponseMessage(methodContext, url, status, response.body), status, BAD_GATEWAY)
      case status =>
        throw new Exception(s"$methodContext failed with status $status. Response body: '${response.body}'")

    }

  val badResponseSeqGetAft = Seq("INVALID_PSTR", "INVALID_FORMBUNDLE_NUMBER", "INVALID_START_DATE", "INVALID_AFT_VERSION", "INVALID_CORRELATIONID")
  val forbiddenResponseSeqGetAft = Seq("INVALID_PSTR", "INVALID_FORMBUNDLE_NUMBER", "INVALID_START_DATE", "INVALID_AFT_VERSION", "INVALID_CORRELATIONID")

  private def desHeader(implicit hc: HeaderCarrier): Seq[(String, String)] = {
    val requestId = getCorrelationId(hc.requestId.map(_.value))

    Seq("Environment" -> config.desEnvironment, "Authorization" -> config.authorization,
      "Content-Type" -> "application/json", "CorrelationId" -> requestId)
  }

  private def getCorrelationId(requestId: Option[String]): String = {
    requestId.getOrElse {
      Logger.error("No Request Id found")
      randomUUID.toString
    }.replaceAll("(govuk-tax-|-)", "").slice(0, 32)
  }
}

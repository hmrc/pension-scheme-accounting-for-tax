/*
 * Copyright 2019 HM Revenue & Customs
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

import com.google.inject.{ImplementedBy, Inject}
import config.AppConfig
import play.Logger
import play.api.libs.json.JsValue
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}
import uk.gov.hmrc.play.bootstrap.http.HttpClient

import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[CompileConnectorImpl])
trait CompileConnector {
  def fileReturn(pstr:String, data: JsValue):Future[HttpResponse]
}

class CompileConnectorImpl @Inject()(http: HttpClient, config: AppConfig)(implicit headerCarrier: HeaderCarrier,
                                     ec: ExecutionContext) extends CompileConnector {

  private def fileReturnURL(pstr:String):String = config.compileFileReturnURL.format(pstr)

  def fileReturn(pstr:String, data: JsValue):Future[HttpResponse] = {
    Logger.debug(s"[Compile-File-Return-Outgoing-Payload] - ${data.toString()}")
    http.POST(fileReturnURL(pstr), data)
  }
}

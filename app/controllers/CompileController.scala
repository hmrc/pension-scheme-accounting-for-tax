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

package controllers

import config.AppConfig
import connectors.DesConnector
import javax.inject.{Inject, Singleton}
import play.api.Logger
import play.api.libs.json.JsValue
import play.api.mvc._
import uk.gov.hmrc.http.{Request => _, _}
import uk.gov.hmrc.play.bootstrap.controller.BackendController

import scala.concurrent.{ExecutionContext, Future}

@Singleton()
class CompileController @Inject()(appConfig: AppConfig,
                                  cc: ControllerComponents,
                                  desConnector: DesConnector
                                 )(implicit ec: ExecutionContext)
  extends BackendController(cc) with HttpErrorFunctions with Results {

  private def upstreamResponseMessage(actionName: String, status: Int, responseBody: String): String =
    s"$actionName' returned $status. Response body: '$responseBody'"

  private def withRequestDetails(request: Request[AnyContent], actionName:String)(block:(String,JsValue) => Future[Result]):Future[Result] = {
    val json = request.body.asJson
    Logger.debug(s"[$actionName: Incoming-Payload]$json")
    (request.headers.get("pstr"), json) match {
      case (Some(pstr), Some(js)) =>
        block(pstr, js)
      case headerValues =>
        Future.failed(new BadRequestException(s"Bad Request without pstr (${headerValues._1}) or request body (${headerValues._2}})"))
    }
  }

  def fileReturn(): Action[AnyContent] = Action.async { implicit request =>
    val actionName = "Compile File Return"
    withRequestDetails(request, actionName){ (pstr, jsValue) =>
        desConnector.compileFileReturn(pstr, jsValue).map { response =>
          Logger.debug(s"[$actionName: Incoming-Payload]${response.body}")
          Ok(response.body)
        }
    }
  }
}

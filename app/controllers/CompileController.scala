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
import connectors.CompileConnector
import javax.inject.{Inject, Singleton}
import play.api.Logger
import play.api.mvc.{Action, AnyContent, ControllerComponents}
import uk.gov.hmrc.http.BadRequestException
import uk.gov.hmrc.play.bootstrap.controller.BackendController

import scala.concurrent.{ExecutionContext, Future}

@Singleton()
class CompileController @Inject()(appConfig: AppConfig,
                                  cc: ControllerComponents,
                                  compileConnector: CompileConnector)(implicit ec: ExecutionContext)
    extends BackendController(cc) {

  def fileReturn(): Action[AnyContent] = Action.async { implicit request =>
    val json = request.body.asJson
    Logger.debug(s"[Compile-File-Return-Incoming-Payload]$json")

    (request.headers.get("pstr"), json) match {
      case (Some(pstr), Some(jsValue)) =>
        compileConnector.fileReturn(pstr, jsValue).map(response => Ok(response.body))
      case _ => Future.failed(new BadRequestException("Bad Request without pstr or request body"))
    }
  }
}

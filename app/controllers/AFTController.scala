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
import play.api.libs.json.{JsError, JsResultException, JsSuccess, JsValue}
import play.api.mvc._
import transformations.userAnswersToETMP.AFTReturnTransformer
import uk.gov.hmrc.http.{Request => _, _}
import uk.gov.hmrc.play.bootstrap.controller.BackendController

import scala.concurrent.{ExecutionContext, Future}

@Singleton()
class AFTController @Inject()(appConfig: AppConfig,
                              cc: ControllerComponents,
                              desConnector: DesConnector,
                              aftReturnTransformer: AFTReturnTransformer
                                 )(implicit ec: ExecutionContext)
  extends BackendController(cc) with HttpErrorFunctions with Results {

  def fileReturn(): Action[AnyContent] = Action.async { implicit request =>
    val actionName = "Compile File Return"

    withRequestDetails(request, actionName) { (pstr, userAnswersJson) =>
      userAnswersJson.transform(aftReturnTransformer.transformToETMPFormat) match {
        case JsSuccess(dataToBeSendToETMP, _) =>
          desConnector.fileAFTReturn(pstr, dataToBeSendToETMP).map { response =>
            Logger.debug(message = s"[$actionName: Incoming-Payload]${response.body}")
            Ok(response.body)
          }
        case JsError(errors) =>
          throw JsResultException(errors)
      }
    }
  }

  private def withRequestDetails(request: Request[AnyContent], actionName: String)
                                (block: (String, JsValue) => Future[Result]): Future[Result] = {
    val json = request.body.asJson

    Logger.debug(message = s"[$actionName: Incoming-Payload]$json")

    (request.headers.get("pstr"), json) match {
      case (Some(pstr), Some(js)) =>
        block(pstr, js)
      case (pstr, jsValue) =>
        Future.failed(new BadRequestException(s"Bad Request without pstr ($pstr) or request body ($jsValue)"))
    }
  }
}

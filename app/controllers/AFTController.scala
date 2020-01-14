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
import utils.ErrorHandler

import scala.concurrent.{ExecutionContext, Future}

@Singleton()
class AFTController @Inject()(appConfig: AppConfig,
                              cc: ControllerComponents,
                              desConnector: DesConnector,
                              aftReturnTransformer: AFTReturnTransformer
                                 )(implicit ec: ExecutionContext)
  extends BackendController(cc) with HttpErrorFunctions with Results with ErrorHandler {

  def fileReturn(): Action[AnyContent] = Action.async { implicit request =>
    val actionName = "Compile File Return"

    withRequestDetails(request, actionName) { (pstr, userAnswersJson) =>
      Logger.debug(message = s"[$actionName: Incoming-Payload]$userAnswersJson")
      userAnswersJson.transform(aftReturnTransformer.transformToETMPFormat) match {
        case JsSuccess(dataToBeSendToETMP, _) =>
          Logger.debug(message = s"[$actionName: Outgoing-Payload]$dataToBeSendToETMP")
          desConnector.fileAFTReturn(pstr, dataToBeSendToETMP).map { response =>
            Ok(response.body)
          }
        case JsError(errors) =>
          throw JsResultException(errors)
      }
    }
  }

  def getDetails: Action[AnyContent] = Action.async {
      implicit request => {

        val startDate = request.headers.get("startDate")
        val aftVersion = request.headers.get("aftVersion")
        val fbNumber = request.headers.get("fbNumber")
        val pstrOpt = request.headers.get("pstr")

        def queryParams(pstr: String): String = (startDate, aftVersion, fbNumber) match {
          case (Some(startDt), Some(aftVer), _) => s"$pstr?startDate=$startDt&aftVersion=$aftVer"
          case (Some(startDt), None, _) => s"$pstr?startDate=$startDt"
          case (_, _, Some(formBundleNumber)) => s"$pstr?fbNumber=$formBundleNumber"
          case _ => pstr
        }

        pstrOpt match {
          case Some(pstr) =>
          desConnector.getAftDetails(queryParams(pstr)).map(Ok(_))
          case _ => Future.failed(new BadRequestException("Bad Request with missing PSTR"))
        }

      } recoverWith recoverFromError

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

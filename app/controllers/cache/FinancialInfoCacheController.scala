/*
 * Copyright 2024 HM Revenue & Customs
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

package controllers.cache

import com.google.inject.Inject
import play.api.Logger
import play.api.mvc._
import repository.FinancialInfoCacheRepository
import uk.gov.hmrc.auth.core.retrieve.v2.Retrievals
import uk.gov.hmrc.auth.core.{AuthConnector, AuthorisedFunctions, Enrolment}
import uk.gov.hmrc.http.{HeaderCarrier, UnauthorizedException}
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import scala.concurrent.{ExecutionContext, Future}

class FinancialInfoCacheController @Inject()(
                                              repository: FinancialInfoCacheRepository,
                                              val authConnector: AuthConnector,
                                              cc: ControllerComponents,
                                              psaPspEnrolmentAuthAction: controllers.actions.PsaPspEnrolmentAuthAction
                                            )(implicit ec: ExecutionContext) extends BackendController(cc) with AuthorisedFunctions {

  import FinancialInfoCacheController._

  private val logger = Logger(classOf[FinancialInfoCacheController])

  def save: Action[AnyContent] = psaPspEnrolmentAuthAction.async {
    implicit request =>
      request.body.asJson.map {
        jsValue =>
          repository.save(request.externalId, jsValue)
            .map(_ => Created)
      } getOrElse Future.successful(BadRequest)
  }

  def get: Action[AnyContent] = psaPspEnrolmentAuthAction.async {
    implicit request =>
      repository.get(request.externalId).map { response =>
        logger.debug(message = s"FinancialInfoCacheController.get: Response for request Id ${request.externalId} is $response")
        response.map {
          Ok(_)
        } getOrElse NotFound
      }
  }

  def remove: Action[AnyContent] = psaPspEnrolmentAuthAction.async {
    implicit request =>
      repository.remove(request.externalId).map(_ => Ok)
  }
}

object FinancialInfoCacheController {

  case class IdNotFoundFromAuth() extends UnauthorizedException("Not Authorised - Unable to retrieve id")

}


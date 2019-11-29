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

package controllers.cache

import com.google.inject.Inject
import play.api.mvc.{Action, AnyContent, ControllerComponents, Result}
import play.api.{Configuration, Logger}
import repository.DataCacheRepository
import uk.gov.hmrc.auth.core.retrieve.v2.Retrievals
import uk.gov.hmrc.auth.core.{AuthConnector, AuthorisedFunctions, Enrolment}
import uk.gov.hmrc.http.{HeaderCarrier, UnauthorizedException}
import uk.gov.hmrc.play.bootstrap.controller.BackendController

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class DataCacheController @Inject()(
                                     config: Configuration,
                                     repository: DataCacheRepository,
                                     val authConnector: AuthConnector,
                                     cc: ControllerComponents
                                   ) extends BackendController(cc) with AuthorisedFunctions {

  import DataCacheController._

  def save: Action[AnyContent] = Action.async {
    implicit request =>
      withIdFromAuth { id =>
        request.body.asJson.map {
          jsValue =>
            repository.save(id, jsValue)
              .map(_ => Created)
        } getOrElse Future.successful(BadRequest)
      }
  }

  def get: Action[AnyContent] = Action.async {
    implicit request =>
      withIdFromAuth { id =>
        repository.get(id).map { response =>
          Logger.debug(message = s"DataCacheController.get: Response for request Id $id is $response")
          response.map {
            Ok(_)
          } getOrElse NotFound
        }
      }
  }

  def remove: Action[AnyContent] = Action.async {
    implicit request =>
      withIdFromAuth { id =>
        repository.remove(id).map(_ => Ok)
      }
  }

  private def withIdFromAuth(block: String => Future[Result])(implicit hc: HeaderCarrier): Future[Result] = {
    authorised(Enrolment("HMRC-PODS-ORG")).retrieve(Retrievals.internalId) {
      case Some(id) =>
        block(id)
      case None =>
        Future.failed(InternalIdNotFoundFromAuth())
    }
  }
}

object DataCacheController {

  case class InternalIdNotFoundFromAuth(msg: String = "Not Authorised - Unable to retrieve Internal Id")
    extends UnauthorizedException(msg)

}

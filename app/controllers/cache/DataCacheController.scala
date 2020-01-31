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

package controllers.cache

import com.google.inject.Inject
import play.api.mvc._
import play.api.{Configuration, Logger}
import repository.DataCacheRepository
import uk.gov.hmrc.auth.core.retrieve.v2.Retrievals
import uk.gov.hmrc.auth.core.{AuthConnector, AuthorisedFunctions, Enrolment}
import uk.gov.hmrc.http.{BadRequestException, HeaderCarrier, UnauthorizedException}
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

  def save(id: String): Action[AnyContent] = Action.async {
    implicit request =>
      withIdFromAuth { case (sessionId, _) =>
        request.body.asJson.map {
          jsValue =>

            repository.save(id, jsValue, sessionId)
              .map(_ => Created)
        } getOrElse Future.successful(BadRequest)
      }
  }

  def setLock(id: String): Action[AnyContent] = Action.async {
    implicit request =>
      withIdFromAuth { case (sessionId, name) =>
        request.body.asJson.map {
          jsValue =>
            repository.setLock(id, name, jsValue, sessionId)
              .map(_ => Created)
        } getOrElse Future.successful(BadRequest)
      }
  }

  def get(id: String): Action[AnyContent] = Action.async {
    implicit request =>
      withIdFromAuth { (sessionId, _) =>
        repository.get(id , sessionId).map { response =>
          Logger.debug(message = s"DataCacheController.get: Response for request Id $id is $response")
          response.map {
            Ok(_)
          } getOrElse NotFound
        }
      }
  }

  def remove(id: String): Action[AnyContent] = Action.async {
    implicit request =>
      withIdFromAuth { (sessionId, _) =>
        repository.remove(id, sessionId).map(_ => Ok)
      }
  }

  def getLock(id: String): Action[AnyContent] = Action.async {
    implicit request =>
      withIdFromAuth { case (sessionId, _) =>

        repository.isLocked(sessionId, id).map { response =>
          Logger.debug(message = s"DataCacheController.isLocked: Response for request Id $id is $response")
          response.map {
            Ok(_)
          } getOrElse NotFound
        }
      }
  }

  private def withIdFromAuth(block: (String, String) => Future[Result])(implicit hc: HeaderCarrier,
                                                              request: Request[AnyContent]): Future[Result] = {

    authorised(Enrolment("HMRC-PODS-ORG")).retrieve(Retrievals.name) {
        case Some(name) =>
          val sessionId = request.headers.get("X-Session-ID").getOrElse(throw MissingHeadersException)
          block(sessionId, s"${name.name.getOrElse("")} ${name.lastName.getOrElse("")}".trim)
        case _ => Future.failed(CredNameNotFoundFromAuth())
      }
  }
}

object DataCacheController {

  case object MissingHeadersException extends BadRequestException("Missing id with pstr and startDate from headers")

  case class CredNameNotFoundFromAuth(msg: String = "Not Authorised - Unable to retrieve credentials - name")
    extends UnauthorizedException(msg)

}

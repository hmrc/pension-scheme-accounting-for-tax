/*
 * Copyright 2021 HM Revenue & Customs
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

import connectors.FinancialStatementConnector
import javax.inject.{Inject, Singleton}
import play.api.libs.json._
import play.api.mvc._
import uk.gov.hmrc.auth.core.retrieve.v2.Retrievals
import uk.gov.hmrc.auth.core.{AuthConnector, AuthorisedFunctions, Enrolment}
import uk.gov.hmrc.http.{UnauthorizedException, Request => _, _}
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController
import utils.HttpResponseHelper

import scala.concurrent.{ExecutionContext, Future}

@Singleton()
class FinancialStatementController @Inject()(cc: ControllerComponents,
                                             financialStatementConnector: FinancialStatementConnector,
                                             val authConnector: AuthConnector
                                            )(implicit ec: ExecutionContext)
  extends BackendController(cc)
    with HttpErrorFunctions
    with Results
    with AuthorisedFunctions
    with HttpResponseHelper {

  def psaStatement: Action[AnyContent] = Action.async {
    implicit request =>
      get(key = "psaId") { psaId =>
        financialStatementConnector.getPsaFS(psaId).map { data =>
          Ok(Json.toJson(data))
        }
      }
  }

  def schemeStatement: Action[AnyContent] = Action.async {
    implicit request =>
      get(key = "pstr") { pstr =>
        financialStatementConnector.getSchemeFS(pstr).map { data =>
          Ok(Json.toJson(data))
        }
      }
  }

  private def get(key: String)(block: String => Future[Result])
                 (implicit hc: HeaderCarrier, request: Request[AnyContent]): Future[Result] = {

    authorised(Enrolment("HMRC-PODS-ORG") or Enrolment("HMRC-PODSPP-ORG")).retrieve(Retrievals.externalId) {
      case Some(_) =>
        request.headers.get(key) match {
          case Some(id) => block(id)
          case _ => Future.failed(new BadRequestException(s"Bad Request with missing $key"))
        }
      case _ =>
        Future.failed(new UnauthorizedException("Not Authorised - Unable to retrieve credentials - externalId"))
    }
  }
}

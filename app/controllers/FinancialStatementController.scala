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
import connectors.FinancialStatementConnector
import javax.inject.{Inject, Singleton}
import play.api.libs.json._
import play.api.mvc._
import uk.gov.hmrc.auth.core.retrieve.v2.Retrievals
import uk.gov.hmrc.auth.core.{AuthConnector, AuthorisedFunctions, Enrolment}
import uk.gov.hmrc.http.{UnauthorizedException, Request => _, _}
import uk.gov.hmrc.play.bootstrap.controller.BackendController

import scala.concurrent.{ExecutionContext, Future}

@Singleton()
class FinancialStatementController @Inject()(appConfig: AppConfig,
                                             cc: ControllerComponents,
                                             financialStatementConnector: FinancialStatementConnector,
                                             val authConnector: AuthConnector
                             )(implicit ec: ExecutionContext)
  extends BackendController(cc) with HttpErrorFunctions with Results with AuthorisedFunctions {

  def psaStatement: Action[AnyContent] = Action.async {
    implicit request =>
      get { psaId =>
        financialStatementConnector.getPsaFS(psaId).map { data =>
                  Ok(Json.toJson(data))
            }
        }
      }


  private def get(block: String => Future[Result])
                 (implicit hc: HeaderCarrier, request: Request[AnyContent]): Future[Result] = {

    authorised(Enrolment("HMRC-PODS-ORG")).retrieve(Retrievals.externalId) {
      case Some(_) =>
        request.headers.get("psaId") match {
          case Some(psaId) => block(psaId)
          case _ => Future.failed(new BadRequestException("Bad Request with missing PSA ID"))
        }
      case _ =>
        Future.failed(new UnauthorizedException("Not Authorised - Unable to retrieve credentials - externalId"))
    }
  }
}

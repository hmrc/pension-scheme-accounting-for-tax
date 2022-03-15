/*
 * Copyright 2022 HM Revenue & Customs
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
import play.api.mvc.Results.Redirect
import play.api.mvc._
import repository.FinancialInfoCreditAccessRepository
import uk.gov.hmrc.auth.core.retrieve.v2.Retrievals
import uk.gov.hmrc.auth.core.retrieve.~
import uk.gov.hmrc.auth.core.{AuthConnector, AuthorisedFunctions, Enrolment}
import uk.gov.hmrc.domain.{PsaId, PspId}
import uk.gov.hmrc.http.{HeaderCarrier, UnauthorizedException}
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class FinancialInfoCreditAccessController @Inject()(
                                                     repository: FinancialInfoCreditAccessRepository,
                                                     val authConnector: AuthConnector,
                                                     cc: ControllerComponents
                                                   ) extends BackendController(cc) with AuthorisedFunctions {

  import FinancialInfoCreditAccessController._

  private val logger = Logger(classOf[FinancialInfoCreditAccessController])

  def schemeAccess(pstr:String): Action[AnyContent] = Action.async {
    /*
    Check if a PSA/PSP accessed scheme in last 28 days and if so return Ok and:-
    PsaID: Option[String]
    PspID: Option[String]

    If not then insert document for current PSA/PSP and return NotFound
     */
    implicit request =>
      withId { (optPsaId, optPspId) =>
        repository.get(id).map { response =>
          logger.debug(message = s"FinancialInfoCreditAccessController.get: Response for request Id $id is $response")
          response.map {
            Ok(_)
          } getOrElse NotFound
        }
      }
  }

  private def withId(block: (Option[String], Option[String]) => Future[Result])
                   (implicit hc: HeaderCarrier): Future[Result] = {
    authorised().retrieve(Retrievals.allEnrolments) { enrolments =>
        val psaId = enrolments.getEnrolment("HMRC-PODS-ORG").flatMap(_.getIdentifier("PSAID")).map(_.value)
        val pspId = enrolments.getEnrolment("HMRC-PODSPP-ORG").flatMap(_.getIdentifier("PSPID")).map(_.value)
        block.apply(psaId, pspId)
    }
  }
}

object FinancialInfoCreditAccessController {

  case class IdNotFoundFromAuth() extends UnauthorizedException("Not Authorised - Unable to retrieve id")

}




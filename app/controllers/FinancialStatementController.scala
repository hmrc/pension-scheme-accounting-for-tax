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

package controllers

import connectors.FinancialStatementConnector
import models.SchemeChargeType.{aftManualAssessment, aftManualAssessmentCredit, aftReturn, aftReturnCredit, otcAftReturn, otcAftReturnCredit, otcManualAssessment, otcManualAssessmentCredit}
import models.{SchemeFSDetail, SchemeReferenceNumber}
import play.api.libs.json._
import play.api.mvc._
import uk.gov.hmrc.auth.core.retrieve.v2.Retrievals
import uk.gov.hmrc.auth.core.retrieve.~
import uk.gov.hmrc.auth.core.{AuthConnector, AuthorisedFunctions, Enrolment, Enrolments}
import uk.gov.hmrc.domain.PsaId
import uk.gov.hmrc.http.{UnauthorizedException, Request => _, _}
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController
import utils.HttpResponseHelper

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton()
class FinancialStatementController @Inject()(cc: ControllerComponents,
                                             financialStatementConnector: FinancialStatementConnector,
                                             val authConnector: AuthConnector,
                                             psaEnrolmentAuthAction: actions.PsaEnrolmentAuthAction
                                            )(implicit ec: ExecutionContext)
  extends BackendController(cc)
    with HttpErrorFunctions
    with Results
    with AuthorisedFunctions
    with HttpResponseHelper {

  def psaStatement: Action[AnyContent] = psaEnrolmentAuthAction.async {
    implicit request =>
      financialStatementConnector.getPsaFS(request.psaId.id).map { data =>
        Ok(Json.toJson(data))
      }
  }

  private def updateChargeType(seqSchemeFSDetail: Seq[SchemeFSDetail]): Seq[SchemeFSDetail] = {
    seqSchemeFSDetail.map { schemeFSDetail =>
      val maybeCreditChargeType = (schemeFSDetail.chargeType, schemeFSDetail.amountDue < 0) match {
        case (aftReturn.value, true) => aftReturnCredit.value
        case (otcAftReturn.value, true) => otcAftReturnCredit.value
        case (aftManualAssessment.value, true) => aftManualAssessmentCredit.value
        case (otcManualAssessment.value, true) => otcManualAssessmentCredit.value
        case _ => schemeFSDetail.chargeType
      }

      schemeFSDetail.copy(
        chargeType = maybeCreditChargeType
      )
    }
  }

  private def withPstrCheck(block: String => Future[Result])
                           (implicit hc: HeaderCarrier, request: Request[AnyContent]): Future[Result] = {
    authorised(Enrolment("HMRC-PODS-ORG") or Enrolment("HMRC-PODSPP-ORG")).retrieve(Retrievals.externalId and Retrievals.allEnrolments) {
      case Some(_) ~ enrolments =>
        (getPsaId(enrolments), request.headers.get("pstr")) match {
          case (_, Some(pstr)) =>
            block(pstr)
          case _ => Future.failed(new BadRequestException("Bad Request with missing psaId or pstr"))
        }
      case _ =>
        Future.failed(new UnauthorizedException("Not Authorised - Unable to retrieve credentials - externalId"))
    }
  }

  def schemeStatement: Action[AnyContent] = Action.async {
    implicit request =>
      withPstrCheck { pstr =>
        financialStatementConnector.getSchemeFS(pstr).map { data =>
          Ok(Json.toJson(data.copy(seqSchemeFSDetail = updateChargeType(data.seqSchemeFSDetail))))
        }
      }
  }

  private def getPsaId(enrolments: Enrolments): Option[PsaId] =
    enrolments
      .getEnrolment(key = "HMRC-PODS-ORG")
      .flatMap(_.getIdentifier("PSAID"))
      .map(id => PsaId(id.value))

  def schemeStatementSrn(srn: SchemeReferenceNumber): Action[AnyContent] = psaEnrolmentAuthAction.async {
    implicit request =>
      request.headers.get("pstr").map { pstr =>
        financialStatementConnector.getSchemeFS(pstr).map { data =>
          Ok(Json.toJson(data.copy(seqSchemeFSDetail = updateChargeType(data.seqSchemeFSDetail))))
        }
      }.getOrElse(Future.failed(new BadRequestException("Bad Request with missing psaId or pstr")))
  }
}
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
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.http.{Request => _, _}
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController
import utils.HttpResponseHelper

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton()
class FinancialStatementController @Inject()(cc: ControllerComponents,
                                             financialStatementConnector: FinancialStatementConnector,
                                             val authConnector: AuthConnector,
                                             psaEnrolmentAuthAction: actions.PsaEnrolmentAuthAction,
                                             psaPspEnrolmentAuthAction: actions.PsaPspEnrolmentAuthAction,
                                             psaPspSchemeAuthAction: actions.PsaPspSchemeAuthAction
                                            )(implicit ec: ExecutionContext)
  extends BackendController(cc) with HttpResponseHelper {

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

  def schemeStatementSrn(srn: SchemeReferenceNumber, loggedInAsPsa: Boolean): Action[AnyContent] = (psaPspEnrolmentAuthAction andThen psaPspSchemeAuthAction(srn, loggedInAsPsa)).async {
    implicit request =>
      request.headers.get("pstr").map { pstr =>
        financialStatementConnector.getSchemeFS(pstr).map { data =>
          Ok(Json.toJson(data.copy(seqSchemeFSDetail = updateChargeType(data.seqSchemeFSDetail))))
        }
      }.getOrElse(Future.failed(new BadRequestException("Bad Request with missing psaId or pstr")))
  }
}
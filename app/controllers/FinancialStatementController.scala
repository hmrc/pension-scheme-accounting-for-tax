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

package controllers

import connectors.{AFTConnector, FinancialStatementConnector}
import models.FeatureToggle.Enabled
import models.FeatureToggleName.FinancialInformationAFT
import models.{SchemeFS, SchemeFSDetail}
import play.api.libs.json._
import play.api.mvc._
import services.FeatureToggleService
import transformations.ETMPToUserAnswers.AFTDetailsTransformer.localDateDateReads
import uk.gov.hmrc.auth.core.retrieve.v2.Retrievals
import uk.gov.hmrc.auth.core.{AuthConnector, AuthorisedFunctions, Enrolment}
import uk.gov.hmrc.http.{UnauthorizedException, Request => _, _}
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController
import utils.HttpResponseHelper

import java.time.LocalDate
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton()
class FinancialStatementController @Inject()(cc: ControllerComponents,
                                             financialStatementConnector: FinancialStatementConnector,
                                             val authConnector: AuthConnector,
                                             aftConnector: AFTConnector,
                                             featureToggleService: FeatureToggleService
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

  private def updateWithVersionAndReceiptDate(pstr: String,
                                              seqSchemeFSDetails: Seq[SchemeFSDetail])(implicit hc: HeaderCarrier,
                                                                                       ec: ExecutionContext,
                                                                                       request: RequestHeader): Future[Seq[SchemeFSDetail]] = {
    Future.sequence(
      seqSchemeFSDetails.map { schemeFSDetail =>
        schemeFSDetail.formBundleNumber match {
          case Some(fb) =>
            aftConnector.getAftDetails(pstr, fb).map { jsValue =>
              val optVersion = (jsValue \ "aftDetails" \ "aftVersion").asOpt[Int]
              val optReceiptDate = (jsValue \ "aftDetails" \ "receiptDate").asOpt[LocalDate](localDateDateReads)
              schemeFSDetail copy(
                receiptDate = optReceiptDate,
                version = optVersion
              )
            }
          case _ => Future.successful(schemeFSDetail)
        }
      }
    )
  }

  private def updateSourceChargeInfo(schemeFS: SchemeFS,
                                     seqSchemeFsDetails: Seq[SchemeFSDetail]
                                    )(implicit hc: HeaderCarrier, ec: ExecutionContext, request: RequestHeader): SchemeFS = {
    val updatedSeqSchemeFSDetail = seqSchemeFsDetails.map { schemeFSDetail =>
      schemeFSDetail.sourceChargeInfo match {
        case Some(sci) =>
          val newSourceChargeInfo = seqSchemeFsDetails.find(_.index == sci.index) match {
            case Some(foundOriginalCharge) =>
              sci copy(
                version = foundOriginalCharge.version,
                receiptDate = foundOriginalCharge.receiptDate
              )
            case _ => sci
          }
          schemeFSDetail copy (
            sourceChargeInfo = Some(newSourceChargeInfo)
            )
        case _ => schemeFSDetail
      }
    }
    schemeFS copy (
      seqSchemeFSDetail = updatedSeqSchemeFSDetail
      )
  }

  def schemeStatement: Action[AnyContent] = Action.async {
    implicit request =>
      get(key = "pstr") { pstr =>
        financialStatementConnector.getSchemeFS(pstr).flatMap { data =>
          val updatedSchemeFS = featureToggleService.get(FinancialInformationAFT).flatMap {
            case Enabled(_) =>
              for {
                updatedSeqSchemeFSDetail <- updateWithVersionAndReceiptDate(pstr, data.seqSchemeFSDetail)
              } yield updateSourceChargeInfo(data, updatedSeqSchemeFSDetail)
            case _ => Future.successful(data)
          }
          updatedSchemeFS.map(schemeFS => Ok(Json.toJson(schemeFS)))
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

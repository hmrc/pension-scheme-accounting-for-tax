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
import models.enumeration.CreditAccessType
import models.enumeration.CreditAccessType.{AccessedByLoggedInPsaOrPsp, AccessedByOtherPsa, AccessedByOtherPsp}
import play.api.Logger
import play.api.libs.json.{JsValue, Json}
import play.api.mvc._
import repository.FinancialInfoCreditAccessRepository
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import scala.concurrent.{ExecutionContext, Future}

class FinancialInfoCreditAccessController @Inject()(
                                                     repository: FinancialInfoCreditAccessRepository,
                                                     cc: ControllerComponents,
                                                     psaEnrolmentAuthAction: controllers.actions.PsaEnrolmentAuthAction,
                                                     psaPspEnrolmentAuthAction: controllers.actions.PsaPspEnrolmentAuthAction,
                                                     psaSchemeAuthAction: controllers.actions.PsaSchemeAuthAction,
                                                     psaPspSchemeAuthAction: controllers.actions.PsaPspSchemeAuthAction
                                                   )(implicit ec: ExecutionContext) extends BackendController(cc) {

  private val logger = Logger(classOf[FinancialInfoCreditAccessController])

  private def retrieveAccessInfo(jsValue: JsValue, optLoggedInPsaId: Option[String], optLoggedInPspId: Option[String]): Option[CreditAccessType] = {
    val foundOptPsaId = (jsValue \ "psaId").asOpt[String]
    val foundOptPspId = (jsValue \ "pspId").asOpt[String]
    (foundOptPsaId, foundOptPspId, optLoggedInPsaId, optLoggedInPspId) match {
      case (Some(foundPsaId), _, Some(loggedInPsaId), _) if foundPsaId == loggedInPsaId => Some(AccessedByLoggedInPsaOrPsp)
      case (Some(_), _, _, Some(_)) => Some(AccessedByOtherPsa)
      case (Some(_), _, Some(_), _) => Some(AccessedByOtherPsa)
      case (_, Some(foundPspId), _, Some(loggedInPspId)) if foundPspId == loggedInPspId => Some(AccessedByLoggedInPsaOrPsp)
      case (_, Some(_), _, Some(_)) => Some(AccessedByOtherPsp)
      case (_, Some(_), Some(_), _) => Some(AccessedByOtherPsp)
      case _ => None
    }
  }

  def getForSchemePsa(psaId: String, srn: String): Action[AnyContent] = (psaEnrolmentAuthAction andThen psaSchemeAuthAction(srn)).async {
    implicit request =>
      getForPsaOrPsp(Some(request.psaId.id), None, srn)
  }

  def getForSchemePsp(pspId: String, srn: String): Action[AnyContent] = (psaPspEnrolmentAuthAction andThen psaPspSchemeAuthAction(srn, loggedInAsPsa = false)).async {
    implicit request =>
      getForPsaOrPsp(None, request.pspId.map(_.id), srn)
  }

  def getForPsa(psaId: String): Action[AnyContent] = psaEnrolmentAuthAction.async {
    implicit request =>
      getForPsaOrPsp(Some(request.psaId.id), None, request.psaId.id)
  }

  private def getForPsaOrPsp(psaId: Option[String], pspId: Option[String], srn: String): Future[Result] = {
    repository.get(srn).flatMap { response =>
      logger.debug(message = s"FinancialInfoCreditAccessController.getForPsaOrPsp: Response for request Id $srn is $response")
      response.flatMap(retrieveAccessInfo(_, psaId, pspId)) match {
        case Some(accessInfo) => Future.successful(Ok(Json.toJson(accessInfo)))
        case _ =>
          val jsObject =
            psaId.map(psaId => Json.obj("psaId" -> psaId)).getOrElse(Json.obj()) ++
              pspId.map(pspId => Json.obj("pspId" -> pspId)).getOrElse(Json.obj())
          repository.save(
            srn,
            jsObject
          ).map(_ => NotFound)
      }
    }
  }
}

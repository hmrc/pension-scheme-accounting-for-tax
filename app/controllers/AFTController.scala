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
import connectors.DesConnector
import javax.inject.{Inject, Singleton}
import models.AFTVersion
import play.api.Logger
import play.api.libs.json._
import play.api.mvc._
import services.AFTService
import transformations.ETMPToUserAnswers.AFTDetailsTransformer
import transformations.userAnswersToETMP.AFTReturnTransformer
import uk.gov.hmrc.auth.core.retrieve.v2.Retrievals
import uk.gov.hmrc.auth.core.{AuthConnector, AuthorisedFunctions, Enrolment}
import uk.gov.hmrc.http.{UnauthorizedException, Request => _, _}
import uk.gov.hmrc.play.bootstrap.controller.BackendController

import scala.concurrent.{ExecutionContext, Future}

@Singleton()
class AFTController @Inject()(appConfig: AppConfig,
                              cc: ControllerComponents,
                              desConnector: DesConnector,
                              val authConnector: AuthConnector,
                              aftReturnTransformer: AFTReturnTransformer,
                              aftDetailsTransformer: AFTDetailsTransformer,
                              aftService: AFTService
                             )(implicit ec: ExecutionContext)
  extends BackendController(cc) with HttpErrorFunctions with Results with AuthorisedFunctions {

  def fileReturn(): Action[AnyContent] = Action.async {
    implicit request =>

      post { (pstr, userAnswersJson) =>
        Logger.debug(message = s"[Compile File Return: Incoming-Payload]$userAnswersJson")
        userAnswersJson.transform(aftReturnTransformer.transformToETMPFormat) match {
          case JsSuccess(dataToBeSendToETMP, _) =>
            Logger.debug(message = s"[Compile File Return: Outgoing-Payload]$dataToBeSendToETMP")
            desConnector.fileAFTReturn(
              pstr,
              dataToBeSendToETMP
            ).map {
              response =>
                Ok(response.body)
            }
          case JsError(errors) =>
            throw JsResultException(errors)
        }
      }
  }

  def getDetails: Action[AnyContent] = Action.async {
    implicit request =>
      get { (pstr, startDate) =>
        request.headers.get("aftVersion") match {
          case Some(aftVer) =>
            desConnector.getAftDetails(pstr, startDate, aftVer).map {
              etmpJson =>
                etmpJson.transform(aftDetailsTransformer.transformToUserAnswers) match {
                  case JsSuccess(userAnswersJson, _) =>
                    Ok(userAnswersJson)
                  case JsError(errors) =>
                    throw JsResultException(errors)
                }
            }
          case _ =>
            Future.failed(new BadRequestException("Bad Request with no AFT version"))
        }
      }
  }

  def getVersions: Action[AnyContent] = Action.async {
    implicit request =>
      get { (pstr, startDate) =>
        desConnector.getAftVersions(pstr, startDate).flatMap {
          case data if data.nonEmpty =>
            val versionsWithNoValueAndOnlyOneMemberRemoved = data.map {
              version =>
                desConnector.getAftDetails(pstr, startDate, version.reportVersion.toString).map {
                  jsValue =>
                    if (aftService.isOnlyOneChargeWithNoValue(jsValue)) Seq[AFTVersion]() else Seq(version)
                }
            }
            Future.sequence(versionsWithNoValueAndOnlyOneMemberRemoved).map {
              seqVersions =>
                Ok(Json.toJson(seqVersions.flatten))
            }
          case data =>
            Future.successful(Ok(Json.toJson(data)))
        }
      }
  }

  def getOverview: Action[AnyContent] = Action.async {
    implicit request =>
      get { (pstr, startDate) =>
        request.headers.get("endDate") match {
          case Some(endDate) =>
            desConnector.getAftOverview(pstr, startDate, endDate).flatMap {
              data =>
                Future.successful(Ok(Json.toJson(data)))
            }
          case _ =>
            Future.failed(new BadRequestException("Bad Request with no endDate"))
        }
      }
  }



  private def post(block: (String, JsValue) => Future[Result])
                  (implicit hc: HeaderCarrier, request: Request[AnyContent]): Future[Result] = {

    Logger.debug(message = s"[Compile File Return: Incoming-Payload]${request.body.asJson}")

    authorised(Enrolment("HMRC-PODS-ORG")).retrieve(Retrievals.externalId) {
      case Some(_) =>
        (
          request.headers.get("pstr"),
          request.body.asJson
        ) match {
          case (Some(pstr), Some(js)) =>
            block(pstr, js)
          case (pstr, jsValue) =>
            Future.failed(new BadRequestException(s"Bad Request without pstr ($pstr) or request body ($jsValue)"))
        }
      case _ =>
        Future.failed(new UnauthorizedException("Not Authorised - Unable to retrieve credentials - externalId"))
    }
  }

  private def get(block: (String, String) => Future[Result])
                 (implicit hc: HeaderCarrier, request: Request[AnyContent]): Future[Result] = {

    authorised(Enrolment("HMRC-PODS-ORG")).retrieve(Retrievals.externalId) {
      case Some(_) =>
        (
          request.headers.get("pstr"),
          request.headers.get("startDate")
        ) match {
          case (Some(pstr), Some(startDate)) =>
            block(pstr, startDate)
          case _ =>
            Future.failed(new BadRequestException("Bad Request with missing PSTR/Quarter Start Date"))
        }
      case _ =>
        Future.failed(new UnauthorizedException("Not Authorised - Unable to retrieve credentials - externalId"))
    }
  }
}

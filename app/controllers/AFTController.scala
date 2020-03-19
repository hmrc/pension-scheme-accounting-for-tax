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
import play.api.Logger
import play.api.libs.json._
import play.api.mvc._
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
                              aftDetailsTransformer: AFTDetailsTransformer
                             )(implicit ec: ExecutionContext)
  extends BackendController(cc) with HttpErrorFunctions with Results with AuthorisedFunctions {

  private val zeroCurrencyValue = BigDecimal(0.00)

  def fileReturn(): Action[AnyContent] = Action.async {
    implicit request =>

      val actionName = "Compile File Return"

      withRequestDetails(request, actionName) { (pstr, userAnswersJson) =>
        Logger.debug(message = s"[$actionName: Incoming-Payload]$userAnswersJson")
        userAnswersJson.transform(aftReturnTransformer.transformToETMPFormat) match {
          case JsSuccess(dataToBeSendToETMP, _) =>
            Logger.debug(message = s"[$actionName: Outgoing-Payload]$dataToBeSendToETMP")
            desConnector.fileAFTReturn(
              pstr,
              dataToBeSendToETMP,
              isOnlyOneChargeWithOneMemberAndNoValue(dataToBeSendToETMP)
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
    implicit request => {

      val actionName: String = "Get AFT details"
      val startDate: Option[String] = request.headers.get("startDate")
      val aftVersion: Option[String] = request.headers.get("aftVersion")

      withRequestDetails(request, actionName) { (pstr, _) =>

        (pstr, startDate, aftVersion) match {
          case (pstr, Some(startDt), Some(aftVer)) =>
            desConnector.getAftDetails(pstr, startDt, aftVer).map {
              etmpJson =>
                etmpJson.transform(aftDetailsTransformer.transformToUserAnswers) match {
                  case JsSuccess(userAnswersJson, _) =>
                    Ok(userAnswersJson)
                  case JsError(errors) =>
                    throw JsResultException(errors)
                }
            }
          case _ =>
            Future.failed(new BadRequestException("Bad Request with missing PSTR"))
        }
      }
    }
  }

  def getVersions: Action[AnyContent] = Action.async {
    implicit request =>

      val actionName: String = "Get AFT versions"
      val startDateOpt: Option[String] = request.headers.get("startDate")

      withRequestDetails(request, actionName) { (pstr, _) =>
        (pstr, startDateOpt) match {
          case (pstr, Some(startDate)) =>
            desConnector.getAftVersions(pstr, startDate).flatMap {
              case data if data.nonEmpty =>
                val versionsWithNoValueAndOnlyOneMemberRemoved = data.map {
                  version =>
                    desConnector.getAftDetails(pstr, startDate, version.toString).map {
                      jsValue =>
                        if (isOnlyOneChargeWithOneMemberAndNoValue(jsValue)) Seq[Int]() else Seq(version)
                    }
                }
                Future.sequence(versionsWithNoValueAndOnlyOneMemberRemoved).map {
                  seqVersions =>
                    Ok(Json.toJson(seqVersions.flatten))
                }
              case data =>
                Future.successful(Ok(Json.toJson(data)))
            }
          case _ =>
            Future.failed(new BadRequestException("Bad Request with missing PSTR/Quarter Start Date"))
        }
      }
  }


  private def isOnlyOneChargeWithOneMemberAndNoValue(jsValue: JsValue): Boolean = {
    val areNoChargesWithValues: Boolean =
      (jsValue \ "chargeDetails" \ "chargeTypeADetails" \ "totalAmount").toOption.flatMap(_.validate[BigDecimal].asOpt).forall(_ == zeroCurrencyValue) &&
        (jsValue \ "chargeDetails" \ "chargeTypeBDetails" \ "totalAmount").toOption.flatMap(_.validate[BigDecimal].asOpt).forall(_ == zeroCurrencyValue) &&
        (jsValue \ "chargeDetails" \ "chargeTypeCDetails" \ "totalAmount").toOption.flatMap(_.validate[BigDecimal].asOpt).forall(_ == zeroCurrencyValue) &&
        (jsValue \ "chargeDetails" \ "chargeTypeDDetails" \ "totalAmount").toOption.flatMap(_.validate[BigDecimal].asOpt).forall(_ == zeroCurrencyValue) &&
        (jsValue \ "chargeDetails" \ "chargeTypeEDetails" \ "totalAmount").toOption.flatMap(_.validate[BigDecimal].asOpt).forall(_ == zeroCurrencyValue) &&
        (jsValue \ "chargeDetails" \ "chargeTypeFDetails" \ "totalAmount").toOption.flatMap(_.validate[BigDecimal].asOpt).forall(_ == zeroCurrencyValue) &&
        (jsValue \ "chargeDetails" \ "chargeTypeGDetails" \ "totalOTCAmount").toOption.flatMap(_.validate[BigDecimal].asOpt).forall(_ == zeroCurrencyValue)

    val isOnlyOneChargeWithOneMember: Boolean = Seq(
      (jsValue \ "chargeDetails" \ "chargeTypeCDetails" \ "memberDetails").validate[JsArray].asOpt.exists(_.value.size == 1),
      (jsValue \ "chargeDetails" \ "chargeTypeDDetails" \ "memberDetails").validate[JsArray].asOpt.exists(_.value.size == 1),
      (jsValue \ "chargeDetails" \ "chargeTypeEDetails" \ "memberDetails").validate[JsArray].asOpt.exists(_.value.size == 1),
      (jsValue \ "chargeDetails" \ "chargeTypeGDetails" \ "memberDetails").validate[JsArray].asOpt.exists(_.value.size == 1)
    ).count(_ == true) == 1
    areNoChargesWithValues && isOnlyOneChargeWithOneMember
  }

  private def withRequestDetails(request: Request[AnyContent], actionName: String)
                                (block: (String, JsValue) => Future[Result])
                                (implicit hc: HeaderCarrier): Future[Result] = {
    authorised(Enrolment("HMRC-PODS-ORG")).retrieve(Retrievals.name) {
      case Some(_) =>
        val json = request.body.asJson

        Logger.debug(message = s"[$actionName: Incoming-Payload]$json")

        (request.headers.get("pstr"), json) match {
          case (Some(pstr), Some(js)) =>
            block(pstr, js)
          case (pstr, jsValue) => {
            println(s"\n\n${request.headers.get("pstr")}\n\n")
            Future.failed(new BadRequestException(s"Bad Request without pstr ($pstr) or request body ($jsValue)"))
          }
        }
      case _ =>
        Future.failed(new UnauthorizedException("Not Authorised - Unable to retrieve credentials - name"))
    }
  }
}

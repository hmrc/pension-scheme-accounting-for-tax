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
import uk.gov.hmrc.http.{Request => _, _}
import uk.gov.hmrc.play.bootstrap.controller.BackendController

import scala.concurrent.{ExecutionContext, Future}

@Singleton()
class AFTController @Inject()(appConfig: AppConfig,
                              cc: ControllerComponents,
                              desConnector: DesConnector,
                              aftReturnTransformer: AFTReturnTransformer,
                              aftDetailsTransformer: AFTDetailsTransformer
                             )(implicit ec: ExecutionContext)
  extends BackendController(cc) with HttpErrorFunctions with Results {

  private val zeroCurrencyValue = BigDecimal(0.00)

  def fileReturn(): Action[AnyContent] = Action.async { implicit request =>
    val actionName = "Compile File Return"

    withRequestDetails(request, actionName) { (pstr, userAnswersJson) =>
      Logger.debug(message = s"[$actionName: Incoming-Payload]$userAnswersJson")
      userAnswersJson.transform(aftReturnTransformer.transformToETMPFormat) match {
        case JsSuccess(dataToBeSendToETMP, _) =>
          Logger.debug(message = s"[$actionName: Outgoing-Payload]$dataToBeSendToETMP")
          desConnector.fileAFTReturn(
            pstr,
            dataToBeSendToETMP,
            isOnlyOneChargeWithOneMemberAndNoValue(userAnswersJson.as[JsObject])
          ).map { response =>
            Ok(response.body)
          }
        case JsError(errors) =>
          throw JsResultException(errors)
      }
    }
  }

  def getDetails: Action[AnyContent] = Action.async {
    implicit request => {
      val startDate = request.headers.get("startDate")
      val aftVersion = request.headers.get("aftVersion")
      val pstrOpt = request.headers.get("pstr")

      (pstrOpt, startDate, aftVersion) match {
        case (Some(pstr), Some(startDt), Some(aftVer)) =>
          getDetailsFromDES(startDt, aftVer, pstr).map { details =>
            Ok(details)
          }
        case _ => Future.failed(new BadRequestException("Bad Request with missing PSTR"))
      }
    }
  }

  private def getDetailsFromDES(startDate: String, aftVersion: String, pstr: String)(implicit request: Request[AnyContent]): Future[JsObject] = {
    desConnector.getAftDetails(pstr, startDate, aftVersion).map { etmpJson =>
      etmpJson.transform(aftDetailsTransformer.transformToUserAnswers) match {
        case JsSuccess(userAnswersJson, _) => userAnswersJson
        case JsError(errors) => throw JsResultException(errors)
      }
    }
  }

  def getVersions: Action[AnyContent] = Action.async {
    implicit request =>
      val pstrOpt = request.headers.get("pstr")
      val startDateOpt = request.headers.get("startDate")

      (pstrOpt, startDateOpt) match {
        case (Some(pstr), Some(startDate)) =>
          desConnector.getAftVersions(pstr, startDate).flatMap {
            case data if data.nonEmpty =>
              val versionsWithNoValueAndOnlyOneMemberRemoved = data.map { version =>
                getDetailsFromDES(startDate, aftVersion = version.toString, pstr).map { jsObject =>
                  if (isOnlyOneChargeWithOneMemberAndNoValue(jsObject)) {
                    Seq[Int]()
                  } else {
                    Seq(version)
                  }
                }
              }
              Future.sequence(versionsWithNoValueAndOnlyOneMemberRemoved).map(seqVersions => Ok(Json.toJson(seqVersions.flatten)))
            case data => Future.successful(Ok(Json.toJson(data)))
          }
        case _ =>
          Future.failed(new BadRequestException("Bad Request with missing PSTR/Quarter Start Date"))
      }
  }


  private def isOnlyOneChargeWithOneMemberAndNoValue(jsObject: JsObject): Boolean = {
    val isChargeCEmployerNotDeleted: JsValue => Boolean = employer =>
      !(if ((employer \ "isSponsoringEmployerIndividual").toOption.exists(_.as[Boolean])) {
        (employer \ "sponsoringIndividualDetails" \ "isDeleted").toOption.exists(_.as[Boolean])
      } else {
        (employer \ "sponsoringOrganisationDetails" \ "isDeleted").toOption.exists(_.as[Boolean])
      })

    val isChargeMemberNotDeleted: JsValue => Boolean = member => ! (member \ "memberDetails" \ "isDeleted").toOption.exists(_.as[Boolean])

    val areNoChargesWithValues: Boolean =
      (jsObject \ "chargeADetails" \ "totalChargeAmount").toOption.flatMap(_.validate[BigDecimal].asOpt).forall(_ == zeroCurrencyValue) &&
        (jsObject \ "chargeBDetails" \ "totalChargeAmount").toOption.flatMap(_.validate[BigDecimal].asOpt).forall(_ == zeroCurrencyValue) &&
        (jsObject \ "chargeCDetails" \ "totalChargeAmount").toOption.flatMap(_.validate[BigDecimal].asOpt).forall(_ == zeroCurrencyValue) &&
        (jsObject \ "chargeDDetails" \ "totalChargeAmount").toOption.flatMap(_.validate[BigDecimal].asOpt).forall(_ == zeroCurrencyValue) &&
        (jsObject \ "chargeEDetails" \ "totalChargeAmount").toOption.flatMap(_.validate[BigDecimal].asOpt).forall(_ == zeroCurrencyValue) &&
        (jsObject \ "chargeFDetails" \ "totalChargeAmount").toOption.flatMap(_.validate[BigDecimal].asOpt).forall(_ == zeroCurrencyValue) &&
        (jsObject \ "chargeGDetails" \ "totalChargeAmount").toOption.flatMap(_.validate[BigDecimal].asOpt).forall(_ == zeroCurrencyValue)

    val isOnlyOneChargeWithOneMember: Boolean = Seq(
      (jsObject \ "chargeCDetails" \ "employers").validate[JsArray].asOpt.exists(_.value.count(isChargeCEmployerNotDeleted) == 1),
      (jsObject \ "chargeDDetails" \ "members").validate[JsArray].asOpt.exists(_.value.count(isChargeMemberNotDeleted) == 1),
      (jsObject \ "chargeEDetails" \ "members").validate[JsArray].asOpt.exists(_.value.count(isChargeMemberNotDeleted) == 1),
      (jsObject \ "chargeGDetails" \ "members").validate[JsArray].asOpt.exists(_.value.count(isChargeMemberNotDeleted) == 1)
    ).count(_ == true) == 1
println( "\n>>>areNoChargesWithValues = " + areNoChargesWithValues)
println( "\n>>>isOnlyOneChargeWithOneMember = " + isOnlyOneChargeWithOneMember)
    areNoChargesWithValues && isOnlyOneChargeWithOneMember
  }

  private def withRequestDetails(request: Request[AnyContent], actionName: String)
                                (block: (String, JsValue) => Future[Result]): Future[Result] = {
    val json = request.body.asJson

    Logger.debug(message = s"[$actionName: Incoming-Payload]$json")

    (request.headers.get("pstr"), json) match {
      case (Some(pstr), Some(js)) =>
        block(pstr, js)
      case (pstr, jsValue) =>
        Future.failed(new BadRequestException(s"Bad Request without pstr ($pstr) or request body ($jsValue)"))
    }
  }
}

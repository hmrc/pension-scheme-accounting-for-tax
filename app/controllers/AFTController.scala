/*
 * Copyright 2023 HM Revenue & Customs
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

import audit.FileAFTReturnAuditService
import connectors.AFTConnector
import models.enumeration.JourneyType
import models.enumeration.JourneyType.AFT_SUBMIT_RETURN
import models.{AFTSubmitterDetails, VersionsWithSubmitter}
import play.api.Logger
import play.api.libs.json._
import play.api.mvc._
import repository.{AftOverviewCacheRepository, SubmitAftReturnCacheRepository}
import services.AFTService
import transformations.ETMPToUserAnswers.AFTDetailsTransformer
import transformations.userAnswersToETMP.AFTReturnTransformer
import uk.gov.hmrc.auth.core.retrieve.v2.Retrievals
import uk.gov.hmrc.auth.core.{AuthConnector, AuthorisedFunctions, Enrolment}
import uk.gov.hmrc.http.{UnauthorizedException, Request => _, _}
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController
import utils.JSONPayloadSchemaValidator

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

@Singleton()
class AFTController @Inject()(
                               cc: ControllerComponents,
                               aftConnector: AFTConnector,
                               val authConnector: AuthConnector,
                               aftReturnTransformer: AFTReturnTransformer,
                               aftDetailsTransformer: AFTDetailsTransformer,
                               fileAFTReturnAuditService: FileAFTReturnAuditService,
                               aftService: AFTService,
                               submitAftReturnCacheRepository: SubmitAftReturnCacheRepository,
                               aftOverviewCacheRepository: AftOverviewCacheRepository,
                               jsonPayloadSchemaValidator: JSONPayloadSchemaValidator
                             )(implicit ec: ExecutionContext)
  extends BackendController(cc)
    with HttpErrorFunctions
    with Results
    with AuthorisedFunctions {

  private val logger = Logger(classOf[AFTController])
  val schemaPath = "/resources/schemas/api-1538-file-aft-return-request-schema-2.1.0.json"

  type SeqOfChargeType = Option[Seq[Option[String]]]

  //scalastyle:off cyclomatic.complexity
  def fileReturn(journeyType: JourneyType.Name): Action[AnyContent] = Action.async {
    implicit request =>
      post { (pstr, userAnswersJson) =>
        aftOverviewCacheRepository.remove(pstr).flatMap { _ =>
          logger.debug(message = s"[Compile File Return: Incoming-Payload]$userAnswersJson")
          userAnswersJson.transform(aftReturnTransformer.transformToETMPFormat) match {
            case JsSuccess(dataToBeSendToETMP, _) =>
              val validationResult = jsonPayloadSchemaValidator.validateJsonPayload(schemaPath, dataToBeSendToETMP)
              validationResult match {
                case Left(errors) =>
                  val psaOrPspId: Option[String] = Try(dataToBeSendToETMP.value("aftDeclarationDetails")).toOption.map {
                    case `value`: JsValue => value("submittedID").toString
                    case _ => ""
                  }
                  val chargeType: SeqOfChargeType = dataToBeSendToETMP.value("chargeDetails").asOpt[JsValue].map {
                    case `value`: JsValue =>
                      Seq(
                        (value \ "chargeTypeADetails").asOpt[JsValue].map(_ => "chargeTypeA"),
                        (value \ "chargeTypeBDetails").asOpt[JsValue].map(_ => "chargeTypeB"),
                        (value \ "chargeTypeCDetails").asOpt[JsValue].map(_ => "chargeTypeC"),
                        (value \ "chargeTypeDDetails").asOpt[JsValue].map(_ => "chargeTypeD"),
                        (value \ "chargeTypeEDetails").asOpt[JsValue].map(_ => "chargeTypeE"),
                        (value \ "chargeTypeFDetails").asOpt[JsValue].map(_ => "chargeTypeF"),
                        (value \ "chargeTypeGDetails").asOpt[JsValue].map(_ => "chargeTypeG")
                      )
                    case _ => Seq.empty[Option[String]]
                  }
                  val chargeTypeList = chargeType match {
                    case Some(list) => list.filter(_.nonEmpty).flatten
                    case None => ""
                  }
                  fileAFTReturnAuditService.sendFileAftReturnSchemaValidatorAuditEvent(psaOrPspId.getOrElse(""),
                    pstr,
                    chargeTypeList.toString,
                    dataToBeSendToETMP,
                    errors.mkString,
                    errors.size)
                  throw AFTValidationFailureException(s"Invalid AFT file AFT return:-\n${errors.mkString}")

                case Right(_) => logger.debug(message = s"[Compile File Return: Outgoing-Payload]$dataToBeSendToETMP")
                  //TODO PODS-7967 connect to mongo collection - check whether jt is submit / compile. if submit - then we check in mongo if there is a doc that matches pstr.
                  // if there is - we don't want to resubmit. if there isn't we want to insert a new entry into collection.
                  // find suitable response code
                  println("\n\n\n\n\n + TEST1111")
                  println("TEST1111")
                  println("TEST1111")
                  submitAftReturnCacheRepository.insertLockData().flatMap { g =>
                    if (!g && journeyType == AFT_SUBMIT_RETURN) {
                      Future.successful(NoContent)
                    } else {
                      aftConnector.fileAFTReturn(pstr, journeyType.toString, dataToBeSendToETMP).map { response =>
                        //TODO PODS-7967 what happens when fileAftReturns a 5xx error?
                        Ok(response.body)
                      }
                    }
                    //TODO PODS-7967: need to post to the collection at this point with all of the confirmation page details
                    // Need to POST here - lock it. Catch any failure - recover case match
                    //                  aftConnector.fileAFTReturn(pstr, journeyType.toString, dataToBeSendToETMP).map { response =>
                    //TODO PODS-7967 what happens when fileAftReturns a 5xx error?
                    //                    Ok(response.body)
                    //                  }
                  }
              }
            case JsError(errors) =>
              throw JsResultException(errors)
          }
        }
      }
  }

  def getOverview: Action[AnyContent] = Action.async {
    implicit request =>
      get { (pstr, startDate) =>
        request.headers.get("endDate") match {
          case Some(endDate) =>
            aftOverviewCacheRepository.get(pstr).flatMap {
              case Some(data) => Future.successful(Ok(data))
              case _ => aftConnector.getAftOverview(pstr, startDate, endDate).flatMap { data =>
                aftOverviewCacheRepository.save(pstr, Json.toJson(data)).map { _ =>
                  Ok(Json.toJson(data))
                }
              }
            }
          case _ =>
            Future.failed(new BadRequestException("Bad Request with no endDate"))
        }
      }

  }

  def getDetails: Action[AnyContent] = Action.async {
    implicit request =>
      get { (pstr, startDate) =>
        withAFTVersion { aftVersion =>

          logger.warn(s"CONTROLLER - GET AFT DETAILS CALLED: aftVersion: $aftVersion")

          aftConnector.getAftDetails(pstr, startDate, aftVersion).map {
            etmpJson =>
              etmpJson.transform(aftDetailsTransformer.transformToUserAnswers) match {

                case JsSuccess(userAnswersJson, _) => Ok(userAnswersJson)
                case JsError(errors) => throw JsResultException(errors)
              }
          }
        }
      }
  }

  def getVersions: Action[AnyContent] = Action.async {
    implicit request =>
      get { (pstr, startDate) =>
        aftConnector.getAftVersions(pstr, startDate).map(v => Ok(Json.toJson(v)))
      }
  }

  def getVersionsWithSubmitter: Action[AnyContent] = Action.async {
    implicit request =>
      get { (pstr, startDate) =>
        aftConnector.getAftVersions(pstr, startDate).flatMap { aftVersions =>
          Future.sequence(aftVersions.map { version =>
            aftConnector.getAftDetails(pstr, startDate, padVersion(version.reportVersion.toString)).map { detailsJs =>
              detailsJs.transform(aftDetailsTransformer.transformToUserAnswers) match {
                case JsSuccess(userAnswersJson, _) =>
                  (userAnswersJson \ "submitterDetails").validate[AFTSubmitterDetails] match {
                    case JsSuccess(subDetails, _) => VersionsWithSubmitter(version, Some(subDetails))
                    case JsError(_) => VersionsWithSubmitter(version, None)
                  }
                case JsError(errors) => throw JsResultException(errors)
              }
            }
          })
        }.map(v => Ok(Json.toJson(v)))
      }
  }

  def getIsChargeNonZero: Action[AnyContent] = Action.async {
    implicit request =>
      get { (pstr, startDate) =>
        withAFTVersion { aftVersion =>
          isChargeNonZero(pstr, startDate, aftVersion).map { isNonZero =>
            Ok(isNonZero.toString)
          }
        }
      }
  }

  private def isChargeNonZero(pstr: String, startDate: String, versionNumber: String
                             )(implicit hc: HeaderCarrier,
                               ec: ExecutionContext,
                               request: RequestHeader): Future[Boolean] = {
    aftConnector.getAftDetails(pstr, startDate, versionNumber).map { jsValue =>
      !aftService.isChargeZeroedOut(jsValue)
    }
  }

  private def post(block: (String, JsValue) => Future[Result])
                  (implicit hc: HeaderCarrier, request: Request[AnyContent]): Future[Result] = {

    logger.debug(message = s"[Compile File Return: Incoming-Payload]${request.body.asJson}")

    authorised(Enrolment("HMRC-PODS-ORG") or Enrolment("HMRC-PODSPP-ORG")).retrieve(Retrievals.externalId) {
      case Some(_) =>
        (
          request.headers.get("pstr"),
          request.body.asJson
        ) match {
          case (Some(pstr), Some(js)) =>
            block(pstr, js)
          case (pstr, jsValue) =>
            Future.failed(new BadRequestException(
              s"Bad Request without pstr ($pstr) or request body ($jsValue)"))
        }
      case _ =>
        Future.failed(new UnauthorizedException("Not Authorised - Unable to retrieve credentials - externalId"))
    }
  }

  private def get(block: (String, String) => Future[Result])
                 (implicit hc: HeaderCarrier, request: Request[AnyContent]): Future[Result] = {

    authorised(Enrolment("HMRC-PODS-ORG") or Enrolment("HMRC-PODSPP-ORG")).retrieve(Retrievals.externalId) {
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

  private def withAFTVersion(block: String => Future[Result])
                            (implicit request: Request[AnyContent]): Future[Result] = {
    request.headers.get("aftVersion") match {
      case Some(version) => block(padVersion(version))
      case _ =>
        Future.failed(new BadRequestException("Bad Request with no aft version"))
    }
  }

  private def padVersion(version: String): String = ("00" + version).takeRight(3)
}

case class AFTValidationFailureException(exMessage: String) extends Exception(exMessage)

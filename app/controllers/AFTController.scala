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

import connectors.AFTConnector
import models.FeatureToggle.Enabled
import models.FeatureToggleName.AftOverviewCache
import models.enumeration.JourneyType
import models.{AFTSubmitterDetails, VersionsWithSubmitter}
import play.api.Logger
import play.api.libs.json._
import play.api.mvc._
import repository.AftOverviewCacheRepository
import services.{AFTService, FeatureToggleService}
import transformations.ETMPToUserAnswers.AFTDetailsTransformer
import transformations.userAnswersToETMP.AFTReturnTransformer
import uk.gov.hmrc.auth.core.retrieve.v2.Retrievals
import uk.gov.hmrc.auth.core.{AuthConnector, AuthorisedFunctions, Enrolment}
import uk.gov.hmrc.http.{UnauthorizedException, Request => _, _}
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController
import utils.{ErrorDetailsExtractor, JSONPayloadSchemaValidator}

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton()
class AFTController @Inject()(
                               cc: ControllerComponents,
                               aftConnector: AFTConnector,
                               val authConnector: AuthConnector,
                               aftReturnTransformer: AFTReturnTransformer,
                               aftDetailsTransformer: AFTDetailsTransformer,
                               aftService: AFTService,
                               featureToggleService: FeatureToggleService,
                               aftOverviewCacheRepository: AftOverviewCacheRepository,
                               jsonPayloadSchemaValidator: JSONPayloadSchemaValidator
                             )(implicit ec: ExecutionContext)
  extends BackendController(cc)
    with HttpErrorFunctions
    with Results
    with AuthorisedFunctions {

  private val logger = Logger(classOf[AFTController])
  val basePath = System.getProperty("user.dir")

  //scalastyle:off cyclomatic.complexity
  def fileReturn(journeyType: JourneyType.Name): Action[AnyContent] = Action.async {
    implicit request =>
      post { (pstr, userAnswersJson) =>
        aftOverviewCacheRepository.remove(pstr).flatMap { _ =>
          logger.debug(message = s"[Compile File Return: Incoming-Payload]$userAnswersJson")
          userAnswersJson.transform(aftReturnTransformer.transformToETMPFormat) match {
            case JsSuccess(dataToBeSendToETMP, _) =>
              val validationResult = jsonPayloadSchemaValidator.validateJsonPayload(s"$basePath/conf/${ErrorDetailsExtractor.schemaPath}",dataToBeSendToETMP)
              validationResult match {
                case JsError(error) =>
                  val errors = ErrorDetailsExtractor.getErrors(error)
                  throw AFTValidationFailureException(s"Invalid AFT file AFT return:-\n$errors")
                case JsSuccess(_, _) =>
                  logger.debug(message = s"[Compile File Return: Outgoing-Payload]$dataToBeSendToETMP")
                  aftConnector.fileAFTReturn(pstr, journeyType.toString, dataToBeSendToETMP).map { response =>
                    Ok(response.body)
                  }
              }
            case JsError(errors) =>
              throw JsResultException(errors)
          }
        }
      }
  }

  //scalastyle:off cyclomatic.complexity
  def getOverview: Action[AnyContent] = Action.async {
    implicit request =>
      get { (pstr, startDate) =>
        request.headers.get("endDate") match {
          case Some(endDate) =>
            featureToggleService.get(AftOverviewCache).flatMap {
              case Enabled(_) =>
                aftOverviewCacheRepository.get(pstr).flatMap {
                  case Some(data) => Future.successful(Ok(data))
                  case _ => aftConnector.getAftOverview(pstr, startDate, endDate).flatMap { data =>
                    aftOverviewCacheRepository.save(pstr, Json.toJson(data)).map { _ =>
                      Ok(Json.toJson(data))
                    }
                  }
                }
              case _ => aftConnector.getAftOverview(pstr, startDate, endDate).flatMap { data =>
                Future.successful(Ok(Json.toJson(data)))
              }
            }
          case _ =>
            Future.failed(new BadRequestException("Bad Request with no endDate"))
        }
      }

  }

  def getDetails: Action[AnyContent] = Action.async {
    implicit request =>

      getFbNumber { (pstr, fbn) =>
        fbn match {
          case Some(fbNumber) =>
            aftConnector.getAftDetails(pstr, fbNumber).map {
              etmpJson =>
                etmpJson.transform(aftDetailsTransformer.transformToUserAnswers) match {

                  case JsSuccess(userAnswersJson, _) => Ok(userAnswersJson)
                  case JsError(errors) => throw JsResultException(errors)
                }
            }
          case _ =>
            get { (pstr, startDate) =>
              request.headers.get("aftVersion") match {
                case Some(aftVer) =>
                  aftConnector.getAftDetails(pstr, startDate, aftVer).map {
                    etmpJson =>
                      etmpJson.transform(aftDetailsTransformer.transformToUserAnswers) match {

                        case JsSuccess(userAnswersJson, _) => Ok(userAnswersJson)
                        case JsError(errors) => throw JsResultException(errors)
                      }
                  }
                case _ =>
                  Future.failed(new BadRequestException("Bad Request with no AFT version"))
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
            aftConnector.getAftDetails(pstr, startDate, version.reportVersion.toString).map { detailsJs =>

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

        val versionNumber: String = request.headers.get("aftVersion")
          .getOrElse(throw new BadRequestException(s"Bad Request without aftVersion"))

        isChargeNonZero(pstr, startDate, versionNumber).map { isNonZero =>
          Ok(isNonZero.toString)
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

  private def getFbNumber(block: (String, Option[String]) => Future[Result])
                         (implicit hc: HeaderCarrier, request: Request[AnyContent]): Future[Result] = {

    authorised(Enrolment("HMRC-PODS-ORG") or Enrolment("HMRC-PODSPP-ORG")).retrieve(Retrievals.externalId) {
      case Some(_) =>
        (
          request.headers.get("pstr"),
          request.headers.get("fbNumber")
        ) match {
          case (Some(pstr), Some(fbNumber)) =>
            block(pstr, Some(fbNumber))
          case (Some(pstr), _) =>
            block(pstr, Option.empty)
          case _ =>
            Future.failed(new BadRequestException("Bad Request with missing PSTR"))
        }
      case _ =>
        Future.failed(new UnauthorizedException("Not Authorised - Unable to retrieve credentials - externalId"))
    }
  }
}

case class AFTValidationFailureException(exMessage: String) extends Exception(exMessage)
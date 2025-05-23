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

import audit.FileAFTReturnAuditService
import connectors.AFTConnector
import models.enumeration.JourneyType
import models.enumeration.JourneyType.{AFT_COMPILE_RETURN, AFT_SUBMIT_RETURN}
import models.{AFTSubmitterDetails, AFTVersion, SchemeReferenceNumber, VersionsWithSubmitter}
import play.api.Logger
import play.api.libs.json._
import play.api.mvc._
import repository.{AftOverviewCacheRepository, SubmitAftReturnCacheRepository}
import services.AFTService
import transformations.ETMPToUserAnswers.AFTDetailsTransformer
import transformations.userAnswersToETMP.AFTReturnTransformer
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.http.{Request => _, _}
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController
import utils.JSONPayloadSchemaValidator

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
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
                               jsonPayloadSchemaValidator: JSONPayloadSchemaValidator,
                               psaPspAuthRequest: actions.PsaPspEnrolmentAuthAction,
                               psaPspSchemeAuthAction: actions.PsaPspSchemeAuthAction
                             )(implicit ec: ExecutionContext)
  extends BackendController(cc)
    with HttpErrorFunctions {

  private type SeqOfChargeType = Option[Seq[Option[String]]]
  val schemaPath = "/resources/schemas/api-1538-file-aft-return-request-schema-2.1.0.json"
  private val logger = Logger(classOf[AFTController])

  //scalastyle:off cyclomatic.complexity
  //scalastyle:off method.length
  def fileReturnSrn(journeyType: JourneyType.Name, srn: SchemeReferenceNumber, loggedInAsPsa: Boolean): Action[AnyContent] =
    (psaPspAuthRequest andThen psaPspSchemeAuthAction(srn, loggedInAsPsa = loggedInAsPsa)).async {
      implicit request =>
        requiredHeadersPost { (pstr, externalUserId, userAnswersJson) =>
          aftOverviewCacheRepository.remove(pstr).flatMap { _ =>
            logger.debug(message = s"[Compile File Return: Incoming-Payload]$userAnswersJson")
            userAnswersJson.transform(aftReturnTransformer.transformToETMPFormat) match {
              case JsSuccess(dataToBeSendToETMP, _) =>
                val validationResult = jsonPayloadSchemaValidator.validateJsonPayload(schemaPath, dataToBeSendToETMP)
                validationResult match {
                  case Left(errors) =>
                    val psaOrPspId: Option[String] = Try(dataToBeSendToETMP.value("aftDeclarationDetails")).toOption.map {
                      case value: JsValue => value("submittedID").toString
                      case null => ""
                    }
                    val chargeType: SeqOfChargeType = dataToBeSendToETMP.value("chargeDetails").asOpt[JsValue].map {
                      case value =>
                        Seq(
                          (value \ "chargeTypeADetails").asOpt[JsValue].map(_ => "chargeTypeA"),
                          (value \ "chargeTypeBDetails").asOpt[JsValue].map(_ => "chargeTypeB"),
                          (value \ "chargeTypeCDetails").asOpt[JsValue].map(_ => "chargeTypeC"),
                          (value \ "chargeTypeDDetails").asOpt[JsValue].map(_ => "chargeTypeD"),
                          (value \ "chargeTypeEDetails").asOpt[JsValue].map(_ => "chargeTypeE"),
                          (value \ "chargeTypeFDetails").asOpt[JsValue].map(_ => "chargeTypeF"),
                          (value \ "chargeTypeGDetails").asOpt[JsValue].map(_ => "chargeTypeG")
                        )
                    }
                    val chargeTypeList = chargeType match {
                      case Some(list) => list.filter(_.nonEmpty).flatten
                      case _ => ""
                    }
                    fileAFTReturnAuditService.sendFileAftReturnSchemaValidatorAuditEvent(psaOrPspId.getOrElse(""),
                      pstr,
                      chargeTypeList.toString,
                      dataToBeSendToETMP,
                      errors.mkString,
                      errors.size)
                    throw AFTValidationFailureException(s"Invalid AFT file AFT return:-\n${errors.mkString}")

                  case Right(_) => logger.debug(message = s"[Compile File Return: Outgoing-Payload]$dataToBeSendToETMP")

                    def filingAftReturn = aftConnector.fileAFTReturn(pstr, journeyType.toString, dataToBeSendToETMP).map { response =>
                      Ok(response.body)
                    }

                    journeyType match {
                      case AFT_SUBMIT_RETURN | AFT_COMPILE_RETURN =>
                        val hash = if(journeyType == AFT_COMPILE_RETURN) {
                          val messageDigest = MessageDigest.getInstance("SHA-256")
                          messageDigest.update(dataToBeSendToETMP.toString().getBytes)
                          Some(new String(messageDigest.digest))
                        } else {
                          None
                        }

                        submitAftReturnCacheRepository.insertLockData(pstr, externalUserId, hash).flatMap {
                          case entryExists if !entryExists => Future.successful(NoContent)
                          case _ => filingAftReturn
                        }
                      case _ => filingAftReturn
                    }
                }
              case JsError(errors) =>
                throw JsResultException(errors)
            }
          }
        }
    }

  private def requiredHeadersPost(block: (String, String, JsValue) => Future[Result])
                  (implicit request: actions.PsaPspAuthRequest[AnyContent]): Future[Result] = {

    logger.debug(message = s"[Compile File Return: Incoming-Payload]${request.body.asJson}")

        (
          request.headers.get("pstr"),
          request.body.asJson
        ) match {
          case (Some(pstr), Some(js)) =>
            block(pstr, request.externalId, js)
          case (pstr, jsValue) =>
            Future.failed(new BadRequestException(
              s"Bad Request without pstr ($pstr) or request body ($jsValue)"))
        }
  }

  def getOverviewSrn(srn: SchemeReferenceNumber, loggedInAsPsa: Boolean): Action[AnyContent] = (psaPspAuthRequest andThen psaPspSchemeAuthAction(srn, loggedInAsPsa)).async {
    implicit request =>
      requiredHeaders { (pstr, startDate) =>
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

  def getDetailsSrn(srn: SchemeReferenceNumber, loggedInAsPsa: Boolean): Action[AnyContent] = (psaPspAuthRequest andThen psaPspSchemeAuthAction(srn, loggedInAsPsa)).async {
    implicit request =>
      requiredHeaders { (pstr, startDate) =>
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

  def getVersionsSrn(srn: SchemeReferenceNumber, loggedInAsPsa: Boolean): Action[AnyContent] = (psaPspAuthRequest andThen psaPspSchemeAuthAction(srn, loggedInAsPsa)).async {
    implicit request =>
      requiredHeaders { (pstr, startDate) =>
        aftConnector.getAftVersions(pstr, startDate).map(v => Ok(Json.toJson(v)))
      }
  }

  private def requiredHeaders(block: (String, String) => Future[Result])
                 (implicit request: Request[AnyContent]): Future[Result] = {
        (
          request.headers.get("pstr"),
          request.headers.get("startDate")
        ) match {
          case (Some(pstr), Some(startDate)) =>
            block(pstr, startDate)
          case _ =>
            Future.failed(new BadRequestException("Bad Request with missing PSTR/Quarter Start Date"))
        }
  }

  def getVersionsWithSubmitterSrn(srn: SchemeReferenceNumber, loggedInAsPsa: Boolean): Action[AnyContent] = (psaPspAuthRequest andThen psaPspSchemeAuthAction(srn, loggedInAsPsa)).async {
    implicit request =>

      requiredHeaders { (pstr, startDate) =>

        val getAFTVersions: Future[Seq[AFTVersion]] = aftConnector.getAftVersions(pstr, startDate)

        val result = for {
          aftVersions <- getAFTVersions
          _ = logger.warn(s"number of versions: ${aftVersions.length}")
          res = aftVersions.map { version =>
            val futureAFTDetails =
              aftConnector.getAftDetails(pstr, startDate, padVersion(version.reportVersion.toString))
            futureAFTDetails.map (detailsJsLogic(_, version))
          }
          ans <- Future.sequence(res)
        } yield ans

        result.map(v => Ok(Json.toJson(v)))

      }
  }

  private def detailsJsLogic(js: JsValue, version: AFTVersion): VersionsWithSubmitter = {
    import models.AFTSubmitterDetails._
    logger.warn(s"detailsJsLogic started for: ${version.reportVersion}")
    val transform = js.validate[AFTSubmitterDetails](readAftDetailsFromIF) match {
      case JsSuccess(subDetails, _) => VersionsWithSubmitter(version, Some(subDetails))
      case JsError(_) => VersionsWithSubmitter(version, None)
    }
    logger.warn(s"detailsJsLogic finished for: ${version.reportVersion}")
    logger.warn(s"JsValue for: ${version.reportVersion} is: ${getObjectSize(js)} bytes")
    transform
  }

  private def getObjectSize[T](obj: T)(implicit writes: Writes[T]): Int = {
    val jsonString = Json.toJson(obj).toString()
    jsonString.getBytes(StandardCharsets.UTF_8).length
  }

  def getIsChargeNonZeroSrn(srn: SchemeReferenceNumber, loggedInAsPsa: Boolean): Action[AnyContent] = (psaPspAuthRequest andThen psaPspSchemeAuthAction(srn, loggedInAsPsa: Boolean)).async {
    implicit request =>
      requiredHeaders { (pstr, startDate) =>
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

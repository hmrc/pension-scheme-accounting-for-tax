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

import audit.{AuditEvent, AuditService}
import com.google.inject.Inject
import connectors.MinimalDetailsConnector
import controllers.actions.{PsaPspAuthRequest, PsaPspEnrolmentAuthAction}
import models.LockDetail.formats
import models.{ChargeAndMember, ChargeType, LockDetail}
import play.api.Logger
import play.api.libs.json.{JsObject, Json}
import play.api.mvc._
import repository.AftBatchedDataCacheRepository
import uk.gov.hmrc.http.BadRequestException
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import scala.concurrent.{ExecutionContext, Future}

class AftDataCacheController @Inject()(
                                        batchedRepository: AftBatchedDataCacheRepository,
                                        cc: ControllerComponents,
                                        auditService: AuditService,
                                        psaPspEnrolmentAuthAction: PsaPspEnrolmentAuthAction,
                                        minimalConnector: MinimalDetailsConnector
                                      )(implicit ec: ExecutionContext) extends BackendController(cc) {

  import AftDataCacheController._

  private val logger = Logger(classOf[AftDataCacheController])

  private def extractChargeAndMemberFromHeaders(implicit request: Request[AnyContent]): Option[ChargeAndMember] = {
    request.headers.get("chargeType") match {
      case None => None
      case Some(ct) =>
        ChargeType.getChargeType(ct).map { chargeType =>
          val memberNo = if (ChargeType.isMemberBasedChargeType(chargeType)) {
            request.headers.get("memberNo").map(_.toInt)
          } else {
            None
          }
          ChargeAndMember(chargeType, memberNo)
        }
    }
  }

  def save: Action[AnyContent] = psaPspEnrolmentAuthAction.async {
    implicit request =>
      withId { case (sessionId, id) =>
        val optChargeAndMember = extractChargeAndMemberFromHeaders
        request.body.asJson.map {
          jsValue =>
            batchedRepository.save(
                id = id,
                sessionId = sessionId,
                chargeAndMember = optChargeAndMember,
                userData = jsValue
              )
              .map(_ => Created)
        } getOrElse Future.successful(BadRequest)
      }
  }

  private case class RequestBodyAuditEvent(psaOrPspId: String, body: Option[String]) extends AuditEvent {
    override def auditType: String = "RequestBodyAuditEvent"

    override def details: JsObject =
      Json.obj(
        "psaOrPspId" -> psaOrPspId,
        "body" -> body
      )
  }

  def setSessionData(lock: Boolean): Action[AnyContent] = psaPspEnrolmentAuthAction.async {
    implicit request =>
      getIdWithNameAndPsaOrPspId(lock) { case (sessionId, id, name, psaOrPspId) =>
        request.body.asJson.map {
          jsValue => {
            (
              request.headers.get("version"),
              request.headers.get("accessMode"),
              request.headers.get("areSubmittedVersionsAvailable")
            ) match {
              case (Some(version), Some(accessMode), Some(areSubmittedVersionsAvailable)) =>
                batchedRepository.setSessionData(id,
                  if (lock) Some(LockDetail(name.get, psaOrPspId)) else None,
                  jsValue,
                  sessionId,
                  version.toInt,
                  accessMode,
                  areSubmittedVersionsAvailable.equals("true")
                ).map(_ => Created)

              case (v, am, asva) =>
                logger.warn("BAD Request returned when setting session data " +
                  s"due to absence of version and/or access mode in request header. Version " +
                  s"is $v, access mode is $am and submitted versions available is $asva.")
                auditService.sendEvent(RequestBodyAuditEvent(psaOrPspId, Some(Json.stringify(jsValue))))
                Future.successful(BadRequest("Version and/or access mode not present in request header"))
            }
          }
        } getOrElse {
          logger.warn("BAD Request returned when setting session data for session due to invalid JSON body: " +
            s"ID $sessionId, id $id and psaOrPspId $psaOrPspId.")
          auditService.sendEvent(RequestBodyAuditEvent(psaOrPspId, request.body.asText))
          Future.successful(BadRequest)
        }
      }

  }

  def lockedBy: Action[AnyContent] = psaPspEnrolmentAuthAction.async {
    implicit request =>
      withId { case (sessionId, id) =>
        batchedRepository.lockedBy(sessionId, id).map {
          case None => NotFound
          case Some(lockDetail) => Ok(Json.toJson(lockDetail))
        }
      }
  }

  def getSessionData: Action[AnyContent] = psaPspEnrolmentAuthAction.async {
    implicit request =>
      withId { case (sessionId, id) =>
        batchedRepository.getSessionData(sessionId, id).map {
          case None => NotFound
          case Some(sd) => Ok(Json.toJson(sd))
        }
      }
  }

  def get: Action[AnyContent] = psaPspEnrolmentAuthAction.async {
    implicit request =>
      withId { (sessionId, id) =>
        batchedRepository.get(id, sessionId).map { response =>
          response.map {
            Ok(_)
          } getOrElse NotFound
        }
      }
  }

  def remove: Action[AnyContent] = psaPspEnrolmentAuthAction.async {
    implicit request =>
      withId { (sessionId, id) =>
        batchedRepository.remove(id, sessionId).map(_ => Ok)
      }
  }

  private def withId(block: (String, String) => Future[Result])(implicit request: Request[AnyContent]) = {
    val id = request.headers.get("id").getOrElse(throw MissingHeadersException)
    val sessionId = request.headers.get("X-Session-ID").getOrElse(throw MissingHeadersException)
    block(sessionId, id)
  }

  private def getIdWithNameAndPsaOrPspId(lock: Boolean)
                                        (block: (String, String, Option[String], String) => Future[Result])
                                        (implicit request: PsaPspAuthRequest[AnyContent]): Future[Result] = {

    val id: String =
      request
        .headers
        .get("id")
        .getOrElse(throw MissingHeadersException)

    val sessionId: String =
      request
        .headers
        .get("X-Session-ID")
        .getOrElse(throw MissingHeadersException)

    val psaOrPspId: String =
      (request.pspId, request.psaId) match {
        case (Some(psp), _) => psp.id
        case (_, Some(psa)) => psa.id
        case _ => throw MissingIDException
      }

    if (lock)
      minimalConnector.getMinimalDetails.flatMap {
        minimalDetails =>
          block(sessionId, id, Some(minimalDetails.name.trim), psaOrPspId)
      }
    else
      block(sessionId, id, None, psaOrPspId)
  }
}

object AftDataCacheController {

  case object MissingHeadersException extends BadRequestException("Missing id(pstr and startDate) or Session Id from headers")

  case object MissingIDException extends BadRequestException("Missing psa ID or psp ID from enrolments")

}

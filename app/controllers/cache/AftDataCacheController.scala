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
import models.LockDetail.formats
import models.{ChargeAndMember, ChargeType, LockDetail}
import play.api.Logger
import play.api.libs.json.{JsObject, Json}
import play.api.mvc._
import repository.AftBatchedDataCacheRepository
import uk.gov.hmrc.auth.core.retrieve.v2.Retrievals
import uk.gov.hmrc.auth.core.retrieve.~
import uk.gov.hmrc.auth.core.{AuthConnector, AuthorisedFunctions, Enrolment}
import uk.gov.hmrc.http.{BadRequestException, HeaderCarrier, UnauthorizedException}
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import scala.concurrent.{ExecutionContext, Future}

class AftDataCacheController @Inject()(
                                        batchedRepository: AftBatchedDataCacheRepository,
                                        val authConnector: AuthConnector,
                                        cc: ControllerComponents,
                                        auditService: AuditService
                                      )(implicit ec: ExecutionContext) extends BackendController(cc) with AuthorisedFunctions {

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

  def save: Action[AnyContent] = Action.async {
    implicit request =>
      getIdWithName { case (sessionId, id, _) =>
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

  def setSessionData(lock: Boolean): Action[AnyContent] = Action.async {
    implicit request =>
      getIdWithNameAndPsaOrPspId { case (sessionId, id, name, psaOrPspId) =>
        request.body.asJson.map {
          jsValue => {
            (
              request.headers.get("version"),
              request.headers.get("accessMode"),
              request.headers.get("areSubmittedVersionsAvailable")
            ) match {
              case (Some(version), Some(accessMode), Some(areSubmittedVersionsAvailable)) =>
                batchedRepository.setSessionData(id,
                  if (lock) Some(LockDetail(name, psaOrPspId)) else None,
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
            s"ID $sessionId, id $id, name $name and psaOrPspId $psaOrPspId.")
          auditService.sendEvent(RequestBodyAuditEvent(psaOrPspId, request.body.asText))
          Future.successful(BadRequest)
        }
      }

  }

  def lockedBy: Action[AnyContent] = Action.async {
    implicit request =>
      getIdWithName { case (sessionId, id, _) =>
        batchedRepository.lockedBy(sessionId, id).map {
          case None => NotFound
          case Some(lockDetail) => Ok(Json.toJson(lockDetail))
        }
      }
  }

  def getSessionData: Action[AnyContent] = Action.async {
    implicit request =>
      getIdWithName { case (sessionId, id, _) =>
        batchedRepository.getSessionData(sessionId, id).map {
          case None => NotFound
          case Some(sd) => Ok(Json.toJson(sd))
        }
      }
  }

  def get: Action[AnyContent] = Action.async {
    implicit request =>
      getIdWithName { (sessionId, id, _) =>
        batchedRepository.get(id, sessionId).map { response =>
          response.map {
            Ok(_)
          } getOrElse NotFound
        }
      }
  }

  def remove: Action[AnyContent] = Action.async {
    implicit request =>
      getIdWithName { (sessionId, id, _) =>
        batchedRepository.remove(id, sessionId).map(_ => Ok)
      }
  }

  private def getIdWithName(block: (String, String, String) => Future[Result])
                           (implicit hc: HeaderCarrier, request: Request[AnyContent]): Future[Result] = {
    authorised(psaEnrolment or pspEnrolment).retrieve(Retrievals.name) {
      case Some(name) =>
        val id = request.headers.get("id").getOrElse(throw MissingHeadersException)
        val sessionId = request.headers.get("X-Session-ID").getOrElse(throw MissingHeadersException)
        block(sessionId, id, s"${name.name.getOrElse("")} ${name.lastName.getOrElse("")}".trim)
      case _ => Future.failed(CredNameNotFoundFromAuth())
    }
  }

  private def getIdWithNameAndPsaOrPspId(block: (String, String, String, String) => Future[Result])
                                        (implicit hc: HeaderCarrier, request: Request[AnyContent]): Future[Result] = {
    authorised(psaEnrolment or pspEnrolment).retrieve(Retrievals.name and Retrievals.allEnrolments) {
      case Some(name) ~ enrolments =>
        val psaOrPspId = (
          enrolments.getEnrolment(key = pspEnrolment.key).flatMap(_.getIdentifier("PSPID").map(_.value)),
          enrolments.getEnrolment(key = psaEnrolment.key).flatMap(_.getIdentifier("PSAID").map(_.value))
        ) match {
          case (Some(pspId), _) => pspId
          case (_, Some(psaId)) => psaId
          case _ => throw MissingIDException
        }

        val id = request.headers.get("id").getOrElse(throw MissingHeadersException)
        val sessionId = request.headers.get("X-Session-ID").getOrElse(throw MissingHeadersException)
        block(sessionId, id, s"${name.name.getOrElse("")} ${name.lastName.getOrElse("")}".trim, psaOrPspId)
      case _ => Future.failed(CredNameNotFoundFromAuth())
    }
  }
}

object AftDataCacheController {

  case object MissingHeadersException extends BadRequestException("Missing id(pstr and startDate) or Session Id from headers")

  case object MissingIDException extends BadRequestException("Missing psa ID or psp ID from enrolments")

  case class CredNameNotFoundFromAuth(msg: String = "Not Authorised - Unable to retrieve credentials - name")
    extends UnauthorizedException(msg)

  private val psaEnrolment = Enrolment("HMRC-PODS-ORG")
  private val pspEnrolment = Enrolment("HMRC-PODSPP-ORG")

}

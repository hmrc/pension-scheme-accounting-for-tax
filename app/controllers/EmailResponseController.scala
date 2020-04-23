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

import audit.{AuditService, EmailAuditEvent}
import com.google.inject.Inject
import models.EmailEvents
import models.enumeration.JourneyType
import play.api.Logger
import play.api.libs.json.JsValue
import play.api.mvc._
import uk.gov.hmrc.auth.core._
import uk.gov.hmrc.auth.core.retrieve.v2.Retrievals
import uk.gov.hmrc.crypto.{ApplicationCrypto, Crypted}
import uk.gov.hmrc.http.UnauthorizedException
import uk.gov.hmrc.play.bootstrap.controller.BackendController

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class EmailResponseController @Inject()(
                                         auditService: AuditService,
                                         crypto: ApplicationCrypto,
                                         cc: ControllerComponents,
                                         parser: PlayBodyParsers,
                                         val authConnector: AuthConnector
                                       ) extends BackendController(cc) with AuthorisedFunctions {

  def retrieveStatus(journeyType: JourneyType.Name, id: String): Action[JsValue] = Action(parser.tolerantJson).async {
    implicit request =>
      authorised(Enrolment("HMRC-PODS-ORG")).retrieve(Retrievals.allEnrolments) { enrolments =>
        val psaId = enrolments.getEnrolment(key = "HMRC-PODS-ORG").flatMap(
          _.getIdentifier("PSAID")).map(_.value).getOrElse(throw InsufficientEnrolments("Unable to retrieve Psa Id"))

        validatePstr(id) match {
          case Right(pstr) =>
            request.body.validate[EmailEvents].fold(
              _ => Future.successful(BadRequest("Bad request received for email call back event")),
              valid => {
                valid.events.foreach { event =>
                  Logger.debug(s"Email Audit event coming from $journeyType is $event")
                  auditService.sendEvent(EmailAuditEvent(psaId, pstr, event.event, journeyType))
                }
                Future.successful(Ok)
              }
            )
          case Left(result) => Future.successful(result)
        }
      } recoverWith {
        case e: AuthorisationException =>
          Logger.warn(message = s"Authorization Failed with error $e")
          Future.failed(new UnauthorizedException("Not Authorised - Unable to retrieve enrolments"))
      }
  }

  private def validatePstr(encryptedPstr: String): Either[Result, String] =
    try {
      val regex = "^[0-9]{8}[A-Z]{2}$"
      val pstr = crypto.QueryParameterCrypto.decrypt(Crypted(encryptedPstr)).value
      if (pstr.matches(regex)) {
        Right(pstr)
      } else {
        Left(Forbidden("Invalid PSTR"))
      }
    } catch {
      case _: IllegalArgumentException => Left(Forbidden("Invalid PSTR"))
    }
}

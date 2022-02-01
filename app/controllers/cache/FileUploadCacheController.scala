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

package controllers.cache

import com.google.inject.Inject
import controllers.cache.FinancialInfoCacheController.IdNotFoundFromAuth
import play.api.Logger
import play.api.libs.json.{JsSuccess, Json}
import play.api.mvc._
import repository.FileUploadReferenceCacheRepository
import uk.gov.hmrc.auth.core.retrieve.v2.Retrievals
import uk.gov.hmrc.auth.core.{AuthConnector, AuthorisedFunctions, Enrolment}
import uk.gov.hmrc.http.{BadRequestException, HeaderCarrier}
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import repository.model.FileUploadStatus

class FileUploadCacheController @Inject()(
                                           repository: FileUploadReferenceCacheRepository,
                                           val authConnector: AuthConnector,
                                           cc: ControllerComponents
                                         ) extends BackendController(cc) with AuthorisedFunctions {

  private val logger = Logger(classOf[FileUploadCacheController])

  def requestUpload: Action[AnyContent] = Action.async {
    implicit request =>
      getId { id =>
        request.body.asJson.map {
          jsValue =>
            (jsValue \ "reference").validate[String] match {
              case JsSuccess(reference, _) => repository.requestUpload(id, reference).map(_ => Ok)
              case _ => Future.failed(new BadRequestException(s"Invalid request received from frontend for request upload"))
            }
        } getOrElse Future.successful(BadRequest)
      }
  }

  def getUploadResult: Action[AnyContent] = Action.async {
    implicit request =>
      getId {
        id =>
          repository.getUploadResult(id).map {
            case Some(response) =>
              logger.debug(message = s"FileUploadCacheController.getUploadResult: Response for UploadId $id is $response")
              Ok(Json.toJson(response.status))
            case None => NotFound
          }
      }
  }

  def registerUploadResult: Action[AnyContent] = Action.async {
    implicit request =>
      getReferenceId { id =>
        request.body.asJson.map {
          jsValue =>
            jsValue.validate[FileUploadStatus] match {
              case JsSuccess(fileUploadStatus, _) => repository.updateStatus(id, fileUploadStatus).map(_ => Ok)
              case _ => Future.failed(new BadRequestException(s"Invalid request received from frontend for registerUploadResult"))
            }
        } getOrElse Future.successful(BadRequest)
      }
  }

  private def getId(block: String => Future[Result])
                   (implicit hc: HeaderCarrier, request: Request[AnyContent]): Future[Result] = {
    authorised(Enrolment("HMRC-PODS-ORG") or Enrolment("HMRC-PODSPP-ORG")).retrieve(Retrievals.externalId) {
      case Some(_) =>
        request.headers.get("uploadId") match {
          case Some(id) => block(id)
          case _ => Future.failed(new BadRequestException(s"Bad Request with missing uploadId"))
        }
      case _ => Future.failed(IdNotFoundFromAuth())
    }
  }

  private def getReferenceId(block: String => Future[Result])
                            (implicit hc: HeaderCarrier, request: Request[AnyContent]): Future[Result] = {
    request.headers.get("reference") match {
      case Some(id) => block(id)
      case _ => Future.failed(new BadRequestException(s"Bad Request with missing reference"))
    }
  }

}




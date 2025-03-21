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

package connectors

import audit.FinancialInfoAuditService
import com.google.inject.Inject
import config.AppConfig
import models.{PsaFS, PsaFSDetail, SchemeFS, SchemeFSDetail}
import play.api.Logger
import play.api.http.Status._
import play.api.libs.json._
import play.api.mvc.RequestHeader
import repository.SchemeFSCacheRepository
import uk.gov.hmrc.http._
import uk.gov.hmrc.http.client.HttpClientV2
import utils.HttpResponseHelper

import java.nio.charset.StandardCharsets
import scala.concurrent.{ExecutionContext, Future}

class FinancialStatementConnector @Inject()(
                                             httpClient2: HttpClientV2,
                                             config: AppConfig,
                                             headerUtils: HeaderUtils,
                                             financialInfoAuditService: FinancialInfoAuditService,
                                             cache: SchemeFSCacheRepository
                                           )
  extends HttpErrorFunctions with HttpResponseHelper {

  private val logger = Logger(classOf[FinancialStatementConnector])

  def getPsaFS(psaId: String)
              (implicit headerCarrier: HeaderCarrier, ec: ExecutionContext, request: RequestHeader): Future[PsaFS] = {

    implicit val hc: HeaderCarrier = headerCarrier.withExtraHeaders(headers = headerUtils.integrationFrameworkHeader: _*)

    transformPSAFS(psaId, config.psaFinancialStatementMaxUrl.format(psaId))(hc, implicitly, implicitly)
  }

  //scalastyle:off cyclomatic.complexity
  private def transformPSAFS(psaId: String, url: String)
                            (implicit hc: HeaderCarrier, ec: ExecutionContext, request: RequestHeader): Future[PsaFS] = {

    val reads: Reads[PsaFS] = PsaFS.rdsPsaFSMax

    val toUrl = url"$url"

    httpClient2
      .get(toUrl)(hc)
      .transform(_.withRequestTimeout(config.ifsTimeout))
      .execute[HttpResponse].map { response =>
      response.status match {
        case OK =>
          logger.debug(s"Ok response received from psaFinInfo api with body: ${response.body}")
          Json.parse(response.body).validate[PsaFS](reads) match {
            case JsSuccess(psaFS, _) =>
              logger.debug(s"Response received from psaFinInfo api transformed successfully to $psaFS")
              PsaFS(
                inhibitRefundSignal = psaFS.inhibitRefundSignal,
                seqPsaFSDetail = psaFS.seqPsaFSDetail.filterNot(charge => charge.chargeType.equals("Repayment Interest"))
              )
            case JsError(errors) =>
              throw JsResultException(errors)
          }
        case NOT_FOUND =>
          PsaFS(
            inhibitRefundSignal = false,
            seqPsaFSDetail = Seq.empty[PsaFSDetail]
          )
        case _ =>
          handleErrorResponse("GET", toUrl.toString)(response)
      }
    } andThen financialInfoAuditService.sendPsaFSAuditEvent(psaId)
  }

  def getSchemeFS(pstr: String)
                      (implicit headerCarrier: HeaderCarrier, ec: ExecutionContext, request: RequestHeader): Future[SchemeFS] = {
    val cacheKey = s"schemeFS-$pstr"
    cache.get(cacheKey).flatMap {
      case Some(cachedValue) =>
        val schemeFS = cachedValue.validate[SchemeFS](SchemeFS.rdsSchemeFS)

         schemeFS match {
          case JsSuccess(value, _) =>
            logger.warn(s"Cache hit for getSchemeFS. CacheKey: $cacheKey")
            Future.successful(value)
          case JsError(errors) =>
            logger.warn(s"Failed parsing json from cache: $cacheKey: $errors")
            setSchemeFsToCache(pstr, cacheKey)
        }


      case None =>
        setSchemeFsToCache(pstr, cacheKey)
    }
  }

  private def setSchemeFsToCache(pstr:String,cacheKey:String)
                          (implicit headerCarrier: HeaderCarrier, ec: ExecutionContext, request: RequestHeader)
  : Future[SchemeFS] = {
    logger.warn(s"Cache missing for getSchemeFS. Fetching from IFS for CacheKey: $cacheKey")
    getSchemeFSCall(pstr).flatMap { result =>
      val jsonResult = Json.toJson(result)
      cache.save(cacheKey, jsonResult).map { _ =>
        logger.warn(s"Cache set for getSchemeFS. CacheKey: $cacheKey")
        result
      }
    }
  }


  private def getObjectSize[T](obj: T)(implicit writes: Writes[T]): Int = {
    val jsonString = Json.toJson(obj).toString()
    jsonString.getBytes(StandardCharsets.UTF_8).length
  }
  private def getSchemeFSCall(pstr: String)
                 (implicit headerCarrier: HeaderCarrier, ec: ExecutionContext, request: RequestHeader): Future[SchemeFS] = {
    implicit val hc: HeaderCarrier = headerCarrier.withExtraHeaders(headers = headerUtils.integrationFrameworkHeader: _*)

    val reads: Reads[SchemeFS] = SchemeFS.rdsSchemeFSMax

    val url = url"${config.schemeFinancialStatementMaxUrl.format(pstr)}"


    httpClient2
              .get(url)(hc)
              .setHeader(headerUtils.integrationFrameworkHeader:_*)
              .transform(_.withRequestTimeout(config.ifsTimeout))
              .execute[HttpResponse].map{ response =>
      response.status match {
        case OK =>
          logger.debug(s"Ok response received from schemeFinInfo api with body: ${response.body}")
          Json.parse(response.body).validate[SchemeFS](reads) match {
            case JsSuccess(schemeFS, _) =>
              logger.debug(s"Response received from schemeFinInfo api transformed successfully to $schemeFS")
              logger.warn(s"Size of schemeFS payload: ${getObjectSize(schemeFS)} bytes")
              SchemeFS(
                inhibitRefundSignal = schemeFS.inhibitRefundSignal,
                seqSchemeFSDetail = schemeFS.seqSchemeFSDetail.filterNot(charge => charge.chargeType.equals("Repayment interest"))
              )
            case JsError(errors) =>
              throw JsResultException(errors)
          }
        case NOT_FOUND =>
          SchemeFS(
            inhibitRefundSignal = false,
            seqSchemeFSDetail = Seq.empty[SchemeFSDetail]
          )
        case _ =>
          handleErrorResponse("GET", url.toString)(response)
        }
      } andThen financialInfoAuditService.sendSchemeFSAuditEvent(pstr)


  }
}


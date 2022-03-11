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

package connectors

import audit.FinancialInfoAuditService
import com.google.inject.Inject
import config.AppConfig
import models.FeatureToggle.Enabled
import models.FeatureToggleName.FinancialInformationAFT
import models.{PsaFS, SchemeFS, SchemeFSWrapper}
import play.api.Logger
import play.api.http.Status._
import play.api.libs.json._
import play.api.mvc.RequestHeader
import services.FeatureToggleService
import uk.gov.hmrc.http.{HttpClient, _}
import utils.HttpResponseHelper

import scala.concurrent.{ExecutionContext, Future}

class FinancialStatementConnector @Inject()(
                                             http: HttpClient,
                                             config: AppConfig,
                                             headerUtils: HeaderUtils,
                                             financialInfoAuditService: FinancialInfoAuditService,
                                             featureToggleService : FeatureToggleService
                                           )
  extends HttpErrorFunctions with HttpResponseHelper {

  private val logger = Logger(classOf[FinancialStatementConnector])

  def getPsaFS(psaId: String)
              (implicit headerCarrier: HeaderCarrier, ec: ExecutionContext, request: RequestHeader): Future[Seq[PsaFS]] = {

    val psaInfoUrl = featureToggleService.get(FinancialInformationAFT).map {
      case Enabled(_) => Tuple2(config.psaFinancialStatementMaxUrl.format(psaId), true)
      case _ => Tuple2(config.psaFinancialStatementUrl.format(psaId), false)
    }

    implicit val hc: HeaderCarrier = headerCarrier.withExtraHeaders(headers = headerUtils.integrationFrameworkHeader: _*)

    psaInfoUrl.flatMap {
      case (url, toggle) =>
        transformPSAFS(psaId, url, toggle)(hc, implicitly, implicitly)
    }
  }

  //scalastyle:off cyclomatic.complexity
  private def transformPSAFS(psaId: String, url: String, toggleValue: Boolean)
                            (implicit hc: HeaderCarrier, ec: ExecutionContext, request: RequestHeader): Future[Seq[PsaFS]] = {

    val reads: Reads[Seq[PsaFS]] = if (toggleValue) {
      PsaFS.rdsMaxSeq
    } else {
      Reads.seq(PsaFS.rds)
    }

    http.GET[HttpResponse](url)(implicitly, hc, implicitly).map { response =>
      response.status match {
        case OK =>
          logger.debug(s"Ok response received from psaFinInfo api with body: ${response.body}")
          Json.parse(response.body).validate[Seq[PsaFS]](reads) match {
            case JsSuccess(values, _) =>
              logger.debug(s"Response received from psaFinInfo api transformed successfully to $values")
              values.filterNot(charge => charge.chargeType.equals("Repayment Interest"))
            case JsError(errors) =>
              throw JsResultException(errors)
          }
        case NOT_FOUND =>
          Seq.empty[PsaFS]
        case _ =>
          handleErrorResponse("GET", url)(response)
      }
    } andThen financialInfoAuditService.sendPsaFSAuditEvent(psaId)
  }

  def getSchemeFS(pstr: String)
                 (implicit headerCarrier: HeaderCarrier, ec: ExecutionContext, request: RequestHeader): Future[Seq[SchemeFS]] = {

    val futureURL = featureToggleService.get(FinancialInformationAFT).map {
      case Enabled(_) => Tuple2(config.schemeFinancialStatementMaxUrl.format(pstr), true)
      case _ => Tuple2(config.schemeFinancialStatementUrl.format(pstr), false)
    }
    implicit val hc: HeaderCarrier = headerCarrier.withExtraHeaders(headers = headerUtils.integrationFrameworkHeader: _*)

    futureURL.flatMap {
      case (url, toggleValue) =>
        transformSchemeFS(pstr, url, toggleValue)(hc, implicitly, implicitly)
    }
  }

// Useful for debugging financial info:-
//        .map(seqSchemeFS => writeSeqCaseClassToCSVFile(seqSchemeFS, pstr))
//  private def writeSeqCaseClassToCSVFile[A <: Product](seqA: Seq[A], fileName: String): Seq[A] = {
//    import org.apache.commons.lang3.StringUtils.EMPTY
//    def writeToDesktop(content: String, fileName: String): Unit = {
//      import java.io._
//      val pw = new PrintWriter(new File(s"/home/digital317593/Desktop/$fileName"))
//      pw.write(content)
//      pw.close()
//    }
//    def toCSV(prod: Product): String = {
//      prod.productIterator.map {
//        case Some(value) => value
//        case None => EMPTY
//        case rest => rest
//      }.mkString(",")
//    }
//
//    val headings = seqA.headOption match {
//      case None => ""
//      case Some(hd) => hd.getClass.getDeclaredFields.foldLeft[String](EMPTY) { (acc, h) =>
//        acc + (if (acc.isEmpty) EMPTY else ",") + h.getName
//      }
//    }
//
//    val body = seqA.foldLeft[String](EMPTY) { (acc, y) =>
//        acc + (if (acc.isEmpty) EMPTY else "\n") + toCSV(y)
//    }
//    writeToDesktop(headings + body, s"$fileName.csv")
//    seqA
//  }

  //scalastyle:off cyclomatic.complexity
  private def transformSchemeFS(pstr: String, url: String, toggleValue: Boolean)
                               (implicit hc: HeaderCarrier, ec: ExecutionContext, request: RequestHeader): Future[Seq[SchemeFS]] = {

    val reads: Reads[SchemeFSWrapper] = if (toggleValue) {
      SchemeFSWrapper.rdsMaxSeq
    } else {
      SchemeFSWrapper.rdsSeq
    }

    http.GET[HttpResponse](url)(implicitly, hc, implicitly).map { response =>
      response.status match {
        case OK =>
          logger.debug(s"Ok response received from schemeFinInfo api with body: ${response.body}")
          Json.parse(response.body).validate[SchemeFSWrapper](reads) match {
            case JsSuccess(values, _) =>
              logger.debug(s"Response received from schemeFinInfo api transformed successfully to $values")
              values.documentHeaderDetails.schemeFSSeq.seq.filterNot(charge => charge.chargeType.equals("Repayment Interest"))
            case JsError(errors) =>
              throw JsResultException(errors)
          }
        case NOT_FOUND =>
          Seq.empty[SchemeFS]
        case _ =>
          handleErrorResponse("GET", url)(response)
      }
    } andThen financialInfoAuditService.sendSchemeFSAuditEvent(pstr)
  }
}


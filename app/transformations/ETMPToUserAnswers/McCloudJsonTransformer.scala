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

package transformations.ETMPToUserAnswers

import helpers.DateHelper.getQuarterStartDate
import play.api.libs.functional.syntax._
import play.api.libs.json.Reads._
import play.api.libs.json._

import java.time.{LocalDate, LocalDateTime}
import java.time.format.DateTimeFormatter

trait McCloudJsonTransformer extends JsonTransformer {
  private val dateFormatterYMD: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

  private def formatDateDMYString(date: String): LocalDateTime = LocalDate.parse(date, dateFormatterYMD).atStartOfDay()

  private val readsBoolean: Reads[Boolean] = __.read[String].flatMap {
    case "Yes" => Reads.pure(true)
    case "No" => Reads.pure(false)
    case _ => Reads.failed[Boolean]("Unknown value")
  }

  private val extractTaxYear: JsValue => JsString = dateString => JsString(formatDateDMYString(dateString.as[JsString].value).getYear.toString)

  
  private def readsScheme(isOtherSchemesNodeName: String, amountNodeName: String, repoPeriodNodeName: String): Reads[JsObject] = {
    (__ \ isOtherSchemesNodeName).readNullable[Boolean](readsBoolean).flatMap {
      case Some(areMoreSchemes) =>
        val mcCloud: Reads[JsObject] = ((__ \ "mccloudRemedy" \ "isPublicServicePensionsRemedy").json.put(JsTrue) and
          (__ \ "mccloudRemedy" \ "wasAnotherPensionScheme").json.put(JsBoolean(areMoreSchemes))).reduce
        val schemeObj = if (areMoreSchemes) {
          (__ \ "pensionSchemeDetails").read[JsArray].map(_.value.size).flatMap { max =>
            val readsSchemeArray = (0 until max).foldLeft[Reads[JsArray]](Reads.pure(Json.arr())) { (acc: Reads[JsArray], curr: Int) =>
              (__ \ "pensionSchemeDetails" \ curr \ repoPeriodNodeName).read[String].flatMap { endDate =>
                val currentJsObj = (
                  (__ \ "taxYearReportedAndPaidPage").json.copyFrom((__ \ "pensionSchemeDetails" \ curr \ repoPeriodNodeName).json.pick.map(extractTaxYear)) and
                    (__ \ "taxQuarterReportedAndPaid" \ "startDate").json.copyFrom(Reads.pure(JsString(getQuarterStartDate(endDate)))) and
                    (__ \ "taxQuarterReportedAndPaid" \ "endDate").json.copyFrom(Reads.pure(JsString(endDate))) and
                    (__ \ "chargeAmountReported").json.copyFrom((__ \ "pensionSchemeDetails" \ curr \ amountNodeName).json.pick) and
                    (__ \ "pstr").json.copyFrom((__ \ "pensionSchemeDetails" \ curr \ "pstr").json.pick)
                  ).reduce orElse doNothing
                acc.flatMap(jsArray => currentJsObj.map(jsObject => jsArray :+ jsObject))
              }
            }
            readsSchemeArray.flatMap { schemeArrayNode =>
              (
                (__ \ "mccloudRemedy" \ "schemes").json.put(schemeArrayNode) and
                  (__ \ "mccloudRemedy" \ "isChargeInAdditionReported").json.put(JsTrue)
                ).reduce
            }
          }
        } else {
          val mcCloudDetailReads = (__ \ "pensionSchemeDetails" \ 0 \ repoPeriodNodeName).read[String].flatMap { endDate =>
            (
              (__ \ "taxYearReportedAndPaidPage").json.copyFrom((__ \ "pensionSchemeDetails" \ 0 \ repoPeriodNodeName).json.pick.map(extractTaxYear)) and
              (__ \ "taxQuarterReportedAndPaid" \ "startDate").json.copyFrom(Reads.pure(JsString(getQuarterStartDate(endDate)))) and
              (__ \ "taxQuarterReportedAndPaid" \ "endDate").json.copyFrom(Reads.pure(JsString(endDate))) and
              (__ \ "chargeAmountReported").json.copyFrom((__ \ "pensionSchemeDetails" \ 0 \ amountNodeName).json.pick)
              ).reduce
          }
          mcCloudDetailReads.flatMap { jsObject =>
            (
              (__ \ "mccloudRemedy").json.put(jsObject) and
                (__ \ "mccloudRemedy" \ "isChargeInAdditionReported").json.put(JsTrue)
              ).reduce
          }
        }
        (mcCloud and schemeObj).reduce
      case None =>
        ((__ \ "mccloudRemedy" \ "isPublicServicePensionsRemedy").json.put(JsTrue) and
          (__ \ "mccloudRemedy" \ "isChargeInAdditionReported").json.put(JsFalse)).reduce
    }
  }

  def readsMcCloudDetails(isPSRNodeName: String, isOtherSchemesNodeName: String, amountNodeName: String, repoPeriodNodeName: String): Reads[JsObject] = {
    (__ \ isPSRNodeName).readNullable[Boolean](readsBoolean).flatMap {
      case Some(true) => readsScheme(isOtherSchemesNodeName, amountNodeName, repoPeriodNodeName)
      case Some(false) => (__ \ "mccloudRemedy" \ "isPublicServicePensionsRemedy").json.put(JsFalse)
      case _ => Reads.pure(Json.obj())
    }
  }
}

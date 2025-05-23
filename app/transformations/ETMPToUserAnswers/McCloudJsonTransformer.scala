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

package transformations.ETMPToUserAnswers

import helpers.DateHelper.{McCloudExtractTaxYear, getQuarterStartDate}
import play.api.libs.functional.syntax._
import play.api.libs.json.Reads._
import play.api.libs.json._

trait McCloudJsonTransformer extends JsonTransformer {

  private val readsBoolean: Reads[Boolean] = __.read[String].flatMap {
    case "Yes" => Reads.pure(true)
    case "No" => Reads.pure(false)
    case _ => Reads.failed[Boolean]("Unknown value")
  }

  private def readsScheme(isOtherSchemesNodeName: String, amountNodeName: String, repoPeriodNodeName: String): Reads[JsObject] = {
    def readsDateAndChargeAmount(endDate: String): Reads[JsObject] = (
      (__ \ "taxYearReportedAndPaidPage").json.copyFrom((__ \ "pensionSchemeDetails" \ 0 \ repoPeriodNodeName).json.pick.map(McCloudExtractTaxYear)) and
        (__ \ "taxQuarterReportedAndPaid" \ "startDate").json.copyFrom(Reads.pure(JsString(getQuarterStartDate(endDate)))) and
        (__ \ "taxQuarterReportedAndPaid" \ "endDate").json.copyFrom(Reads.pure(JsString(endDate))) and
        (__ \ "chargeAmountReported").json.copyFrom((__ \ "pensionSchemeDetails" \ 0 \ amountNodeName).json.pick)
      ).reduce: Reads[JsObject]

    (__ \ isOtherSchemesNodeName).readNullable[Boolean](readsBoolean).flatMap{
      case Some(areMoreSchemes) =>
        val mcCloud: Reads[JsObject] = ((__ \ "mccloudRemedy" \ "isPublicServicePensionsRemedy").json.put(JsTrue) and
          (__ \ "mccloudRemedy" \ "wasAnotherPensionScheme").json.put(JsBoolean(areMoreSchemes))).reduce: Reads[JsObject]
        val schemeObj = if (areMoreSchemes) {
          (__ \ "pensionSchemeDetails").read[JsArray].map(_.value.size).flatMap { max =>
            val readsSchemeArray = (0 until max).foldLeft[Reads[JsArray]](Reads.pure(Json.arr())) { (acc: Reads[JsArray], curr: Int) =>
              (__ \ "pensionSchemeDetails" \ curr \ repoPeriodNodeName).read[String].flatMap { endDate =>
                val currentJsObj = (readsDateAndChargeAmount(endDate) and
                  (__ \ "pstr").json.copyFrom((__ \ "pensionSchemeDetails" \ curr \ "pstr").json.pick)).reduce: Reads[JsObject]
                acc.flatMap(jsArray => currentJsObj.map(jsObject => jsArray :+ jsObject))
              }
            }
            readsSchemeArray.flatMap { schemeArrayNode =>
              (
                (__ \ "mccloudRemedy" \ "schemes").json.put(schemeArrayNode) and
                  (__ \ "mccloudRemedy" \ "isChargeInAdditionReported").json.put(JsTrue)
                ).reduce: Reads[JsObject]
            }
          }
        } else {
          val mcCloudDetailReads = (__ \ "pensionSchemeDetails" \ 0 \ repoPeriodNodeName).read[String].flatMap(readsDateAndChargeAmount)
          mcCloudDetailReads.flatMap { jsObject =>
            (
              (__ \ "mccloudRemedy").json.put(jsObject) and
                (__ \ "mccloudRemedy" \ "isChargeInAdditionReported").json.put(JsTrue)
              ).reduce: Reads[JsObject]
          }
        }
        (mcCloud and schemeObj).reduce: Reads[JsObject]
      case None =>
        ((__ \ "mccloudRemedy" \ "isPublicServicePensionsRemedy").json.put(JsTrue) and
          (__ \ "mccloudRemedy" \ "isChargeInAdditionReported").json.put(JsFalse)).reduce: Reads[JsObject]
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

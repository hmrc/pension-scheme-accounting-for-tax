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

import play.api.libs.functional.syntax._
import play.api.libs.json.Reads._
import play.api.libs.json._

trait McCloudJsonTransformer extends JsonTransformer {

  private def calculateMax: Int = {
    //    val max = (0 to 4).takeWhile { i =>
    //      val h = (__ \ "pensionSchemeDetails" \ i \ "repPeriodForLtac").readNullable[String].map{ value =>
    //        value.isDefined
    //      }
    //    }
    4
  }
  private val readsBoolean: Reads[Boolean] = __.read[String].flatMap {
    case "Yes" => Reads.pure(true)
    case "No" => Reads.pure(false)
    case _ => Reads.failed[Boolean]("Unknown value")
  }

  private def readsScheme: Reads[JsObject] = {
    (__ \ "orLfChgPaidbyAnoPS").readNullable[Boolean](readsBoolean).flatMap {
      case Some(areMoreSchemes) =>
        val mcCloud: Reads[JsObject] = ((__ \ "mccloudRemedy" \ "isPublicServicePensionsRemedy").json.put(JsTrue) and
          (__ \ "mccloudRemedy" \ "wasAnotherPensionScheme").json.put(JsBoolean(areMoreSchemes))).reduce
        val schemeObj = if (areMoreSchemes) {
          val arrayOfItems = (0 to calculateMax).foldLeft[Reads[JsArray]](Reads.pure(Json.arr())) { (acc: Reads[JsArray], curr: Int) =>
            val currentJsObj = (
              (__ \ "taxYearReportedAndPaid" \ "endDate").json.copyFrom((__ \ "pensionSchemeDetails" \ curr \ "repPeriodForLtac").json.pick) and
                (__ \ "chargeAmountReported").json.copyFrom((__ \ "pensionSchemeDetails" \ curr \ "amtOrRepLtaChg").json.pick) and
                (__ \ "pstr").json.copyFrom((__ \ "pensionSchemeDetails" \ curr \ "pstr").json.pick)
              ).reduce orElse doNothing
            acc.flatMap(jsArray => currentJsObj.map(jsObject => jsArray :+ jsObject))
          }
          arrayOfItems.flatMap { schemeNode =>
            (
              (__ \ "mccloudRemedy" \ "schemes").json.put(schemeNode) and
                (__ \ "mccloudRemedy" \ "isChargeInAdditionReported").json.put(JsTrue)
              ).reduce
          }
        } else {
          ((__ \ "mccloudRemedy" \ "taxYearReportedAndPaid" \ "endDate").json.copyFrom((__ \ "pensionSchemeDetails" \ 0 \ "repPeriodForLtac").json.pick) and
            (__ \ "mccloudRemedy" \ "chargeAmountReported").json.copyFrom((__ \ "pensionSchemeDetails" \ 0 \ "amtOrRepLtaChg").json.pick) and
            (__ \ "mccloudRemedy" \ "isChargeInAdditionReported").json.put(JsTrue)).reduce
        }
        (mcCloud and schemeObj).reduce
      case None =>
        ((__ \ "mccloudRemedy" \ "isPublicServicePensionsRemedy").json.put(JsTrue) and
          (__ \ "mccloudRemedy" \ "isChargeInAdditionReported").json.put(JsFalse)).reduce

    }
  }

  def readsMcCloudDetails: Reads[JsObject] = {
    (__ \ "lfAllowanceChgPblSerRem").readNullable[Boolean](readsBoolean).flatMap {
        case Some(true) => readsScheme
        case _ => (__ \ "mccloudRemedy" \ "isPublicServicePensionsRemedy").json.put(JsFalse)
    }
  }
}

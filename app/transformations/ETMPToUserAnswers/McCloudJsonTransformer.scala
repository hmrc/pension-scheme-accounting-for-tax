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

  //  private def readsScheme: Reads[JsObject] = {
  //    (__ \ Symbol("orLfChgPaidbyAnoPS")).read[Boolean].flatMap { areMoreSchemes =>
  //       if (areMoreSchemes) {
  //         ((__ \ Symbol("mccloudRemedy") \ Symbol("isPublicServicePensionsRemedy")).json.put(JsTrue) and
  //           (__ \ Symbol("mccloudRemedy") \ Symbol("wasAnotherPensionScheme")).json.put(JsBoolean(areMoreSchemes))).reduce
  //         //(__ \ Symbol("mccloudRemedy") \ Symbol("schemes") \ Symbol("taxYearReportedAndPaid") \ Symbol("endDate")).json.copyFrom((__ \ "pensionSchemeDetails" \ "repPeriodForLtac").json.pick)
  //         //         (__ \ Symbol("mccloudRemedy") \ Symbol("schemes") \ Symbol("chargeAmountReported")).json.copyFrom((__ \ "pensionSchemeDetails" \ "amtOrRepLtaChg").json.pick)
  //       } else {
  //         ((__ \ Symbol("mccloudRemedy") \ Symbol("taxYearReportedAndPaid") \ Symbol("endDate")).json.copyFrom((__ \ "pensionSchemeDetails" \ 0 \ "repPeriodForLtac").json.pick) and
  //         (__ \ Symbol("mccloudRemedy") \ Symbol("chargeAmountReported")).json.copyFrom((__ \ "pensionSchemeDetails" \ 0 \ "amtOrRepLtaChg").json.pick)).reduce
  //       }
  //    }
  //  }

  private def readsScheme: Reads[JsObject] = {
    (__ \ Symbol("orLfChgPaidbyAnoPS")).read[Boolean].flatMap { areMoreSchemes =>
      val mcCloud: Reads[JsObject] = ((__ \ Symbol("mccloudRemedy") \ Symbol("isPublicServicePensionsRemedy")).json.put(JsTrue) and
        (__ \ Symbol("mccloudRemedy") \ Symbol("wasAnotherPensionScheme")).json.put(JsBoolean(areMoreSchemes))).reduce
      val schemeObj = if (areMoreSchemes) {
        (0 to 4).foldLeft[Reads[JsObject]](Reads.pure(Json.obj())) { (acc: Reads[JsObject], curr: Int) =>
          val tt: Reads[JsObject] = ((__ \ Symbol("mccloudRemedy") \ Symbol("schemes") \ Symbol("taxYearReportedAndPaid") \ Symbol("endDate"))
            .json.copyFrom((__ \ "pensionSchemeDetails" \ curr \ "repPeriodForLtac").json.pick) and
            (__ \ Symbol("mccloudRemedy") \ Symbol("schemes") \ Symbol("chargeAmountReported"))
              .json.copyFrom((__ \ "pensionSchemeDetails" \ curr \ "amtOrRepLtaChg").json.pick)).reduce
          (
            acc and (tt orElse doNothing)

            ).reduce
        }
      } else {
        (__ \ Symbol("mccloudRemedy") \ Symbol("taxYearReportedAndPaid") \ Symbol("endDate"))
          .json.copyFrom((__ \ "pensionSchemeDetails" \ 0 \ "repPeriodForLtac").json.pick)
        (__ \ Symbol("mccloudRemedy") \ Symbol("chargeAmountReported"))
          .json.copyFrom((__ \ "pensionSchemeDetails" \ 0 \ "amtOrRepLtaChg").json.pick)
      }
      (mcCloud and schemeObj).reduce
    }
  }

  def readsMcCloudDetails: Reads[JsObject] = {
    (__ \ Symbol("lfAllowanceChgPblSerRem")).read[Boolean].flatMap { isMcCloud =>
      if (isMcCloud) {
        readsScheme
      } else {
        (__ \ Symbol("mccloudRemedy") \ Symbol("isPublicServicePensionsRemedy")).json.put(JsFalse)
      }
    }
  }

  //    ((__ \ Symbol("memberDetails") \ Symbol("firstName")).json.copyFrom((__ \ Symbol("individualsDetails") \ Symbol("firstName")).json.pick) and
  //      (__ \ Symbol("memberDetails") \ Symbol("lastName")).json.copyFrom((__ \ Symbol("individualsDetails") \ Symbol("lastName")).json.pick) and
  //      (__ \ Symbol("memberDetails") \ Symbol("nino")).json.copyFrom((__ \ Symbol("individualsDetails") \ Symbol("nino")).json.pick)
  //      ).reduce

}

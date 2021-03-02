/*
 * Copyright 2021 HM Revenue & Customs
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

import com.google.inject.Inject
import play.api.libs.functional.syntax._
import play.api.libs.json.Reads._
import play.api.libs.json.{JsError, JsObject, JsPath, JsResultException, JsString, JsSuccess, JsValue, Reads, __}

import java.time.{LocalDate, LocalDateTime}

class AFTDetailsTransformer @Inject()(
                                       chargeATransformer: ChargeATransformer,
                                       chargeBTransformer: ChargeBTransformer,
                                       chargeCTransformer: ChargeCTransformer,
                                       chargeDTransformer: ChargeDTransformer,
                                       chargeETransformer: ChargeETransformer,
                                       chargeFTransformer: ChargeFTransformer,
                                       chargeGTransformer: ChargeGTransformer
                                     ) extends JsonTransformer {

  def transformToUserAnswers: Reads[JsObject] = (
    transformAFTDetails and
      transformSchemeDetails and
      transformChargeDetails and
      transformAftDeclarationDetails
    ).reduce

  private def transformAFTDetails: Reads[JsObject] =
    ((__ \ 'aftStatus).json.copyFrom((__ \ 'aftDetails \ 'aftStatus).json.pick) and
      (__ \ 'quarter \ 'startDate).json.copyFrom((__ \ 'aftDetails \ 'quarterStartDate).json.pick) and
      (__ \ 'quarter \ 'endDate).json.copyFrom((__ \ 'aftDetails \ 'quarterEndDate).json.pick)).reduce

  private def transformSchemeDetails: Reads[JsObject] =
    ((__ \ 'pstr).json.copyFrom((__ \ 'schemeDetails \ 'pstr).json.pick) and
      (__ \ 'schemeName).json.copyFrom((__ \ 'schemeDetails \ 'schemeName).json.pick)).reduce

  private def transformChargeDetails: Reads[JsObject] =
    (__ \ 'chargeDetails).read(
      (chargeATransformer.transformToUserAnswers and
        chargeBTransformer.transformToUserAnswers and
        chargeCTransformer.transformToUserAnswers and
        chargeDTransformer.transformToUserAnswers and
        chargeETransformer.transformToUserAnswers and
        chargeFTransformer.transformToUserAnswers and
        chargeGTransformer.transformToUserAnswers).reduce
    )

  private def transformAftDeclarationDetails: Reads[JsObject] = (
    receiptDateReads and
      (__ \ 'aftDeclarationDetails).read(
        ((__ \ 'submitterDetails \ 'submitterType).json.copyFrom((__ \ 'submittedBy).json.pick) and
          (__ \ 'submitterDetails \ 'submitterName).json.copyFrom((__ \ 'submitterName).json.pick) and

          (__ \ 'submittedBy).read[String].flatMap {
            case "PSP" =>
              ((__ \ 'submitterDetails \ 'submitterID).json.copyFrom((__ \ 'submitterId).json.pick) and
              (__ \ 'submitterDetails \ 'authorisingPsaId).json.copyFrom((__ \ 'psaId).json.pick)).reduce
            case _ => (__ \ 'submitterDetails \ 'submitterID).json.copyFrom((__ \ 'submitterID).json.pick)
          }).reduce
      )
    ).reduce

  private def receiptDateReads: Reads[JsObject] =
    (__ \ "aftDetails" \ "receiptDate").read[String].flatMap { dateTime =>
      (__ \ 'submitterDetails \ 'receiptDate).json.put(JsString(LocalDateTime.parse(dateTime.dropRight(1)).toLocalDate.toString))
    }
}

case object ReceiptDateNotInExpectedFormat extends Exception("Get AFT details returned a receipt date which was not in expected format")

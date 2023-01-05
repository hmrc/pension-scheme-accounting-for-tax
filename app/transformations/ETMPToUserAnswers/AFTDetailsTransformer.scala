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

import com.google.inject.Inject
import play.api.libs.functional.syntax._
import play.api.libs.json.Reads._
import play.api.libs.json._
import transformations.ETMPToUserAnswers.AFTDetailsTransformer.localDateDateReads

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
    ((__ \ Symbol("aftStatus")).json.copyFrom((__ \ Symbol("aftDetails") \ Symbol("aftStatus")).json.pick) and
      (__ \ Symbol("aftVersion")).json.copyFrom((__ \ Symbol("aftDetails") \ Symbol("aftVersion")).json.pick) and
      (__ \ Symbol("quarter") \ Symbol("startDate")).json.copyFrom((__ \ Symbol("aftDetails") \ Symbol("quarterStartDate")).json.pick) and
      (__ \ Symbol("quarter") \ Symbol("endDate")).json.copyFrom((__ \ Symbol("aftDetails") \ Symbol("quarterEndDate")).json.pick)).reduce

  private def transformSchemeDetails: Reads[JsObject] =
    ((__ \ Symbol("pstr")).json.copyFrom((__ \ Symbol("schemeDetails") \ Symbol("pstr")).json.pick) and
      (__ \ Symbol("schemeName")).json.copyFrom((__ \ Symbol("schemeDetails") \ Symbol("schemeName")).json.pick)).reduce

  private def transformChargeDetails: Reads[JsObject] =
    (__ \ Symbol("chargeDetails")).read(
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
      (__ \ Symbol("aftDeclarationDetails")).readNullable(
        ((__ \ Symbol("submitterDetails") \ Symbol("submitterType")).json.copyFrom((__ \ Symbol("submittedBy")).json.pick) and
          (__ \ Symbol("submitterDetails") \ Symbol("submitterName")).json.copyFrom((__ \ Symbol("submitterName")).json.pick) and

          (__ \ Symbol("submittedBy")).read[String].flatMap {
            case "PSP" =>
              ((__ \ Symbol("submitterDetails") \ Symbol("submitterID")).json.copyFrom((__ \ Symbol("submitterId")).json.pick) and
                (__ \ Symbol("submitterDetails") \ Symbol("authorisingPsaId")).json.copyFrom((__ \ Symbol("psaId")).json.pick)).reduce
            case _ => (__ \ Symbol("submitterDetails") \ Symbol("submitterID")).json.copyFrom((__ \ Symbol("submitterID")).json.pick)
          }).reduce
      ).map {
        _.getOrElse(Json.obj())
      }
    ).reduce

  def receiptDateReads: Reads[JsObject] =
    (__ \ "aftDetails" \ "receiptDate").read[LocalDate](localDateDateReads).flatMap { receiptDate =>
      (__ \ Symbol("submitterDetails") \ Symbol("receiptDate")).json.put(JsString(receiptDate.toString))
    }
}

object AFTDetailsTransformer {
  val localDateDateReads: Reads[LocalDate] = __.read[String].map { dateTime => LocalDateTime.parse(dateTime.dropRight(1)).toLocalDate }
}

case object ReceiptDateNotInExpectedFormat extends Exception("Get AFT details returned a receipt date which was not in expected format")

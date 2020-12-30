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

package transformations.userAnswersToETMP

import com.google.inject.Inject
import play.api.libs.functional.syntax._
import play.api.libs.json.Reads._
import play.api.libs.json.JsObject
import play.api.libs.json.Json
import play.api.libs.json.Reads
import play.api.libs.json.__

class AFTReturnTransformer @Inject()(chargeATransformer: ChargeATransformer,
                                     chargeBTransformer: ChargeBTransformer,
                                     chargeCTransformer: ChargeCTransformer,
                                     chargeDTransformer: ChargeDTransformer,
                                     chargeETransformer: ChargeETransformer,
                                     chargeFTransformer: ChargeFTransformer,
                                     chargeGTransformer: ChargeGTransformer
                                    ) extends JsonTransformer {

  lazy val transformToETMPFormat: Reads[JsObject] =
    (
      transformToAFTDetails and
      chargeATransformer.transformToETMPData and
      chargeBTransformer.transformToETMPData and
      chargeCTransformer.transformToETMPData and
      chargeDTransformer.transformToETMPData and
      chargeETransformer.transformToETMPData and
      chargeFTransformer.transformToETMPData and
      chargeGTransformer.transformToETMPData and
      transformDeclaration and
      ((__ \ 'aftDeclarationDetails \ 'psaid).json.copyFrom((__ \ 'enterPsaId).json.pick) orElse doNothing)
    ).reduce

  private def transformToAFTDetails: Reads[JsObject] = {
    ((__ \ 'aftDetails \ 'aftStatus).json.copyFrom((__ \ "aftStatus").json.pick) and
      (__ \ 'aftDetails \ 'quarterStartDate).json.copyFrom((__ \ "quarter" \ "startDate").json.pick) and
      (__ \ 'aftDetails \ 'quarterEndDate).json.copyFrom((__ \ "quarter" \ "endDate").json.pick)).reduce
  }

  private def transformDeclaration: Reads[JsObject] = {
    (__ \ 'declaration).readNullable {
      (__ \ 'submittedBy).read[String].flatMap {
        case "PSA" =>
          ((__ \ 'aftDeclarationDetails \ 'submittedBy).json.copyFrom((__ \ 'submittedBy).json.pick) and
            (__ \ 'aftDeclarationDetails \ 'submittedID).json.copyFrom((__ \ 'submittedID).json.pick) and
            (__ \ 'aftDeclarationDetails \ 'psaDeclarationDetails \ 'psaDeclaration1).json.copyFrom((__ \ 'hasAgreed).json.pick) and
            (__ \ 'aftDeclarationDetails \ 'psaDeclarationDetails \ 'psaDeclaration2).json.copyFrom((__ \ 'hasAgreed).json.pick)).reduce
        case "PSP" =>
          (
            (__ \ 'aftDeclarationDetails \ 'submittedBy).json.copyFrom((__ \ 'submittedBy).json.pick) and
              (__ \ 'aftDeclarationDetails \ 'submittedID).json.copyFrom((__ \ 'submittedID).json.pick) and
              (__ \ 'aftDeclarationDetails \ 'pspDeclarationDetails \ 'pspDeclaration1).json.copyFrom((__ \ 'hasAgreed).json.pick) and
              (__ \ 'aftDeclarationDetails \ 'pspDeclarationDetails \ 'pspDeclaration2).json.copyFrom((__ \ 'hasAgreed).json.pick)
            ).reduce
        case _ => doNothing
      }
    }.map {
      _.getOrElse(Json.obj())
    }
  }

}

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

package transformations.userAnswersToETMP

import com.google.inject.Inject
import play.api.libs.functional.syntax._
import play.api.libs.json.Reads._
import play.api.libs.json.{JsObject, Json, Reads, __}

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
        ((__ \ Symbol("aftDeclarationDetails") \ Symbol("psaid")).json.copyFrom((__ \ Symbol("enterPsaId")).json.pick) orElse doNothing)
      ).reduce: Reads[JsObject]

  private def transformToAFTDetails: Reads[JsObject] = {
    ((__ \ Symbol("aftDetails") \ Symbol("aftStatus")).json.copyFrom((__ \ "aftStatus").json.pick) and
      (__ \ Symbol("aftDetails") \ Symbol("quarterStartDate")).json.copyFrom((__ \ "quarter" \ "startDate").json.pick) and
      (__ \ Symbol("aftDetails") \ Symbol("quarterEndDate")).json.copyFrom((__ \ "quarter" \ "endDate").json.pick)).reduce: Reads[JsObject]
  }

  private def transformDeclaration: Reads[JsObject] = {
    (__ \ Symbol("declaration")).readNullable {
      (__ \ Symbol("submittedBy")).read[String].flatMap {
        case "PSA" =>
          ((__ \ Symbol("aftDeclarationDetails") \ Symbol("submittedBy")).json.copyFrom((__ \ Symbol("submittedBy")).json.pick) and
            (__ \ Symbol("aftDeclarationDetails") \ Symbol("submittedID")).json.copyFrom((__ \ Symbol("submittedID")).json.pick) and
            (__ \ Symbol("aftDeclarationDetails") \ Symbol("psaDeclarationDetails") \ Symbol("psaDeclaration1"))
              .json.copyFrom((__ \ Symbol("hasAgreed")).json.pick) and
            (__ \ Symbol("aftDeclarationDetails") \ Symbol("psaDeclarationDetails") \ Symbol("psaDeclaration2"))
              .json.copyFrom((__ \ Symbol("hasAgreed")).json.pick)).reduce: Reads[JsObject]
        case "PSP" =>
          (
            (__ \ Symbol("aftDeclarationDetails") \ Symbol("submittedBy")).json.copyFrom((__ \ Symbol("submittedBy")).json.pick) and
              (__ \ Symbol("aftDeclarationDetails") \ Symbol("submittedID")).json.copyFrom((__ \ Symbol("submittedID")).json.pick) and
              (__ \ Symbol("aftDeclarationDetails") \ Symbol("pspDeclarationDetails") \ Symbol("pspDeclaration1"))
                .json.copyFrom((__ \ Symbol("hasAgreed")).json.pick) and
              (__ \ Symbol("aftDeclarationDetails") \ Symbol("pspDeclarationDetails") \ Symbol("pspDeclaration2"))
                .json.copyFrom((__ \ Symbol("hasAgreed")).json.pick)
            ).reduce: Reads[JsObject]
        case _ => doNothing
      }
    }.map {
      _.getOrElse(Json.obj())
    }
  }

}

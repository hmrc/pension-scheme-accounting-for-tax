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
import play.api.libs.json.{JsObject, Reads, __}

class AFTReturnTransformer @Inject()(chargeATransformer: ChargeATransformer,
                                     chargeBTransformer: ChargeBTransformer,
                                     chargeETransformer: ChargeETransformer,
                                     chargeDTransformer: ChargeDTransformer,
                                     chargeFTransformer: ChargeFTransformer,
                                     chargeGTransformer: ChargeGTransformer
                                    ) {

  lazy val transformToETMPFormat: Reads[JsObject] =
    (transformToAFTDetails and
      chargeATransformer.transformToETMPData and
      chargeBTransformer.transformToETMPData and
      chargeETransformer.transformToETMPData and
      chargeDTransformer.transformToETMPData and
      chargeFTransformer.transformToETMPData and
      chargeGTransformer.transformToETMPData
    ).reduce

  private def transformToAFTDetails: Reads[JsObject] = {
    ((__ \ 'aftDetails \ 'aftStatus).json.copyFrom((__ \ "aftStatus").json.pick) and
      (__ \ 'aftDetails \ 'quarterStartDate).json.copyFrom((__ \ "quarter" \"startDate").json.pick) and
      (__ \ 'aftDetails \ 'quarterEndDate).json.copyFrom((__ \ "quarter" \"endDate").json.pick)).reduce
  }
}

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

package transformations.ETMPToUserAnswers

import com.google.inject.Inject
import play.api.libs.functional.syntax._
import play.api.libs.json.Reads._
import play.api.libs.json.{JsObject, Json, Reads, __}

class AFTDetailsTransformer @Inject()(
                                       chargeFTransformer: ChargeFTransformer
                                     ) {

  def transformToUserAnswers: Reads[JsObject] = (
    transformAFTDetails and
      transformSchemeDetails and
      transformChargeDetails
    ).reduce

  private def transformAFTDetails: Reads[JsObject] =
    ((__ \ 'aftStatus).json.copyFrom((__ \ 'aftDetails \ 'aftStatus).json.pick) and
      (__ \ 'quarter \ 'startDate).json.copyFrom((__ \ 'aftDetails \ 'quarterStartDate).json.pick) and
      (__ \ 'quarter \ 'endDate).json.copyFrom((__ \ 'aftDetails \ 'quarterEndDate).json.pick)).reduce

  private def transformSchemeDetails: Reads[JsObject] =
    ((__ \ 'pstr).json.copyFrom((__ \ 'schemeDetails \ 'pstr).json.pick) and
        (__ \ 'schemeName).json.copyFrom((__ \ 'schemeDetails \ 'schemeName).json.pick)).reduce

  private def transformChargeDetails: Reads[JsObject] =
    (__ \ 'chargeDetails).read(chargeFTransformer.transformToUserAnswers)
}

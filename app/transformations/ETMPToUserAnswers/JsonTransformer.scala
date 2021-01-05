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

import play.api.libs.functional.syntax._
import play.api.libs.json.Reads._
import play.api.libs.json._

trait JsonTransformer {

  val doNothing: Reads[JsObject] = __.json.put(Json.obj())

  def readsMemberDetails: Reads[JsObject] =
    ((__ \ 'memberDetails \ 'firstName).json.copyFrom((__ \ 'individualsDetails \ 'firstName).json.pick) and
      (__ \ 'memberDetails \ 'lastName).json.copyFrom((__ \ 'individualsDetails \ 'lastName).json.pick) and
      (__ \ 'memberDetails \ 'nino).json.copyFrom((__ \ 'individualsDetails \ 'nino).json.pick)
      ).reduce

}

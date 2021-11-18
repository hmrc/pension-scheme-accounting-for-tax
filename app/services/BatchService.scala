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

package services

import play.api.libs.json.{JsObject, JsArray, Json}

class BatchService {
  import BatchService._

  def split(payload: JsObject, batchSize: Int): Seq[BatchInfo] = Nil

  def extractNonMemberJson(payload: JsObject): JsObject = Json.obj()

  def join(batches: Seq[BatchInfo], nonMemberJson: JsObject): JsObject = Json.obj()
}

object BatchService {
  object ChargeType extends Enumeration {
    type ChargeType = Value
    val ChargeTypeA, ChargeTypeB, ChargeTypeC, ChargeTypeD, ChargeTypeE, ChargeTypeF, ChargeTypeG = Value
  }

  case class BatchInfo(chargeType: ChargeType.ChargeType, batchNo: Int, jsArray: JsArray)
}

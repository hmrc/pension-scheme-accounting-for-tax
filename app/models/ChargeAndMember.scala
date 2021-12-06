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

package models

import services.BatchService.BatchType
import models.ChargeType.{ChargeTypeLifetimeAllowance, ChargeTypeOverseasTransfer, ChargeTypeAnnualAllowance, ChargeTypeAuthSurplus}

case class ChargeAndMember(chargeType:ChargeType, memberNo:Option[Int]) {
  def batchType:BatchType = {
    (chargeType, memberNo) match {
      case (ChargeTypeAnnualAllowance, Some(_)) => BatchType.ChargeE
      case (ChargeTypeAuthSurplus, Some(_)) => BatchType.ChargeC
      case (ChargeTypeLifetimeAllowance, Some(_)) => BatchType.ChargeD
      case (ChargeTypeOverseasTransfer, Some(_)) => BatchType.ChargeG
      case _ => BatchType.Other
    }
  }
}

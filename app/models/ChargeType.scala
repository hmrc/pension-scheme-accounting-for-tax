/*
 * Copyright 2022 HM Revenue & Customs
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

import models.enumeration.{Enumerable, WithName}

sealed trait ChargeType

object ChargeType extends Enumerable.Implicits {

  def isMemberBasedChargeType(chargeType: ChargeType): Boolean =
    chargeType == ChargeTypeAnnualAllowance ||
      chargeType == ChargeTypeAuthSurplus ||
      chargeType == ChargeTypeLifetimeAllowance ||
      chargeType == ChargeTypeOverseasTransfer


  case object ChargeTypeAnnualAllowance extends WithName("annualAllowance") with ChargeType

  case object ChargeTypeAuthSurplus extends WithName("authSurplus") with ChargeType

  case object ChargeTypeDeRegistration extends WithName("deRegistration") with ChargeType

  case object ChargeTypeLifetimeAllowance extends WithName("lifeTimeAllowance") with ChargeType

  case object ChargeTypeOverseasTransfer extends WithName("overseasTransfer") with ChargeType

  case object ChargeTypeShortService extends WithName("shortService") with ChargeType

  case object ChargeTypeLumpSumDeath extends WithName("lumpSumDeath") with ChargeType

  case object ChargeTypeNone extends WithName("none") with ChargeType

  val values: Seq[ChargeType] = Seq(
    ChargeTypeAnnualAllowance,
    ChargeTypeAuthSurplus,
    ChargeTypeDeRegistration,
    ChargeTypeLifetimeAllowance,
    ChargeTypeOverseasTransfer,
    ChargeTypeShortService,
    ChargeTypeLumpSumDeath,
    ChargeTypeNone
  )

  def getChargeType(s: String): Option[ChargeType] = values.find(_.toString == s)
}

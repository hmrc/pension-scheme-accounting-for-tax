/*
 * Copyright 2025 HM Revenue & Customs
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

import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

class ChargeTypeSpec extends AnyFreeSpec with Matchers {

  "ChargeType" - {

    "isMemberBasedChargeType should return true for member-based charges" in {
      ChargeType.isMemberBasedChargeType(ChargeType.ChargeTypeAnnualAllowance) shouldBe true
      ChargeType.isMemberBasedChargeType(ChargeType.ChargeTypeAuthSurplus) shouldBe true
      ChargeType.isMemberBasedChargeType(ChargeType.ChargeTypeLifetimeAllowance) shouldBe true
      ChargeType.isMemberBasedChargeType(ChargeType.ChargeTypeOverseasTransfer) shouldBe true
    }

    "isMemberBasedChargeType should return false for non-member-based charges" in {
      ChargeType.isMemberBasedChargeType(ChargeType.ChargeTypeDeRegistration) shouldBe false
      ChargeType.isMemberBasedChargeType(ChargeType.ChargeTypeShortService) shouldBe false
      ChargeType.isMemberBasedChargeType(ChargeType.ChargeTypeLumpSumDeath) shouldBe false
      ChargeType.isMemberBasedChargeType(ChargeType.ChargeTypeNone) shouldBe false
    }

    "getChargeType should return the correct ChargeType from string" in {
      ChargeType.getChargeType("annualAllowance") shouldBe Some(ChargeType.ChargeTypeAnnualAllowance)
      ChargeType.getChargeType("lumpSumDeath") shouldBe Some(ChargeType.ChargeTypeLumpSumDeath)
    }

    "getChargeType should return None for invalid string" in {
      ChargeType.getChargeType("InvalidType") shouldBe None
    }
  }
}

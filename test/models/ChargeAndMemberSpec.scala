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
import services.BatchService.BatchType
import models.ChargeType._

class ChargeAndMemberSpec extends AnyFreeSpec with Matchers {

  "ChargeAndMember.batchType" - {

    "should return ChargeE for ChargeTypeAnnualAllowance with a member number" in {
      val result = ChargeAndMember(ChargeTypeAnnualAllowance, Some(1)).batchType
      result shouldBe BatchType.ChargeE
    }

    "should return ChargeC for ChargeTypeAuthSurplus with a member number" in {
      val result = ChargeAndMember(ChargeTypeAuthSurplus, Some(1)).batchType
      result shouldBe BatchType.ChargeC
    }

    "should return ChargeD for ChargeTypeLifetimeAllowance with a member number" in {
      val result = ChargeAndMember(ChargeTypeLifetimeAllowance, Some(1)).batchType
      result shouldBe BatchType.ChargeD
    }

    "should return ChargeG for ChargeTypeOverseasTransfer with a member number" in {
      val result = ChargeAndMember(ChargeTypeOverseasTransfer, Some(1)).batchType
      result shouldBe BatchType.ChargeG
    }

    "should return Other for known charge types with no member number" in {
      val result = ChargeAndMember(ChargeTypeAnnualAllowance, None).batchType
      result shouldBe BatchType.Other
    }
  }
}

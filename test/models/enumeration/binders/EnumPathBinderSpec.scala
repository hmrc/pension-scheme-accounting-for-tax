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

import models.enumeration.binders.EnumPathBinder
import org.scalatestplus.play.PlaySpec
import play.api.mvc.PathBindable

object TestEnum extends Enumeration {
  type TestEnum = Value
  val ValueOne, ValueTwo, ValueThree = Value
}

class EnumPathBinderSpec extends PlaySpec {

  implicit val testEnumBinder: PathBindable[TestEnum.Value] = EnumPathBinder.pathBinder(TestEnum)

  "EnumPathBinder" should {

    "bind a valid enum string to the correct enum value" in {
      val enumString = "ValueOne"

      val result = testEnumBinder.bind("key", enumString)

      result mustEqual Right(TestEnum.ValueOne)
    }

    "return an error if the enum string is not valid" in {
      val enumString = "InvalidValue"

      val result = testEnumBinder.bind("key", enumString)

      result mustEqual Left("Unknown Enum Type InvalidValue")
    }

    "unbind an enum value to the correct string" in {
      val enumValue = TestEnum.ValueTwo

      val result = testEnumBinder.unbind("key", enumValue)

      result mustEqual "ValueTwo"
    }
  }
}

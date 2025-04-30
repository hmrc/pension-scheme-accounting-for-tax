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

package helpers

import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers
import play.api.libs.json._
import helpers.JsonHelper._

class JsonHelperSpec extends AnyFreeSpec with Matchers {

  "JsonHelper" - {

    "RichJsObject" - {

      "setObject should correctly set a value in a JsObject" in {
        val jsObject = Json.obj("key1" -> "value1", "key2" -> "value2")
        val path = JsPath \ "key2"
        val value = JsString("newValue")

        val updatedObject = jsObject.setObject(path, value).get

        (updatedObject \ "key2").as[String] shouldBe "newValue"
      }

      "removeObject should correctly remove a key from a JsObject" in {
        val jsObject = Json.obj("key1" -> "value1", "key2" -> "value2")
        val path = JsPath \ "key2"

        val updatedObject = jsObject.removeObject(path).get

        (updatedObject \ "key2").isDefined shouldBe false
      }
    }

    "RichJsValue" - {

      "set should correctly set a key-value pair in a JsObject" in {
        val jsObject = Json.obj("key1" -> "value1")
        val path = JsPath \ "key2"
        val value = JsString("newValue")

        val updatedValue = jsObject.set(path, value).get

        (updatedValue \ "key2").as[String] shouldBe "newValue"
      }

      "set should correctly append to a JsArray" in {

        val jsArray = Json.arr(1, 2, 3)
        val result = jsArray.set(JsPath \ 3, JsNumber(4))

        result.get shouldBe Json.arr(1, 2, 3, 4)

      }

      "set should return an error if attempting to set a key on a non-Object value" in {
        val jsArray = Json.arr(1, 2, 3)
        val path = JsPath \ "key2"
        val value = JsString("newValue")

        val result = jsArray.set(path, value)

        result shouldBe a[JsError]
      }

      "remove should correctly remove a key from a JsObject" in {
        val jsObject = Json.obj("key1" -> "value1", "key2" -> "value2")
        val path = JsPath \ "key2"

        val updatedValue = jsObject.remove(path).get

        (updatedValue \ "key2").isDefined shouldBe false
      }

      "remove should correctly remove an element from a JsArray" in {
        val jsArray = Json.arr(1, 2, 3, 4)
        val result = jsArray.remove(JsPath \ 1)

        result.get shouldBe Json.arr(1, 3, 4)
      }


      "remove should return an error if the path is not found" in {
        val jsObject = Json.obj("key1" -> "value1")
        val result = jsObject.remove(JsPath \ "nonExistentKey")

        result shouldBe JsSuccess(jsObject)
      }

      "remove should return an error if attempting to remove from a non-Object or non-Array value" in {
        val jsString = JsString("some string")
        val path = JsPath \ "key1"

        val result = jsString.remove(path)

        result shouldBe a[JsError]
      }

      "set should return an error when path is empty" in {
        val jsObject = Json.obj("key1" -> "value1")
        val path = JsPath()

        val result = jsObject.set(path, JsString("newValue"))

        result shouldBe a[JsError]
      }
    }

    "Handling specific node types" - {

      "should handle an IdxPathNode correctly when setting a value in a JsArray" in {
        val jsArray = Json.arr(1, 2, 3)
        val result = jsArray.set(JsPath \ 1, JsNumber(42))

        result.get shouldBe Json.arr(1, 42, 3)
      }

      "should handle a KeyPathNode correctly when setting a value in a JsObject" in {
        val jsObject = Json.obj("key1" -> "value1")
        val path = JsPath \ "key2"
        val value = JsString("newValue")

        val updatedValue = jsObject.set(path, value).get

        (updatedValue \ "key2").as[String] shouldBe "newValue"
      }
    }
  }
}

/*
 * Copyright 2023 HM Revenue & Customs
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

package utils

import com.github.fge.jackson.JsonLoader
import com.github.fge.jsonschema.core.report.ListProcessingReport
import com.github.fge.jsonschema.main.JsonSchemaFactory
import play.api.libs.json._

case class ErrorReport(instance: String, errors: String)


class JSONPayloadSchemaValidator {
  type ValidationReport = Either[List[ErrorReport], Boolean]
  val basePath: String = System.getProperty("user.dir")

  def validateJsonPayload(jsonSchemaPath: String, data: JsValue): ValidationReport = {
    val deepValidationCheck = true
    val index = 0
    val factory = JsonSchemaFactory.byDefault()
    val schemaPath = JsonLoader.fromPath(s"$basePath/conf/$jsonSchemaPath")
    val schema = factory.getJsonSchema(schemaPath)
    val jsonDataAsString = JsonLoader.fromString(data.toString())
    val doValidation = schema.validate(jsonDataAsString, deepValidationCheck)
    val isSuccess = doValidation.isSuccess
    if (!isSuccess) {
      val jsArray = Json.parse(doValidation.asInstanceOf[ListProcessingReport].asJson().toString).asInstanceOf[JsArray].value
      val jsReport = Json.parse(jsArray.toList(index).toString()) \ "reports" \ "/oneOf/0"
      val errors = jsReport.asInstanceOf[JsDefined]
      val convertedListOutput = errors.get.asInstanceOf[JsArray].value.toList.map(element =>
        ErrorReport((element \ "instance").get.toString(), removeInputData((element \ "message").get.toString())))
      Left(convertedListOutput)
    }
    else {
      Right(true)
    }
  }

  private def removeInputData(data: String): String = {
    val index = data.indexOf("input")
    if (index == -1) {
      data
    }
    else {
      data.substring(0, index)
    }
  }

}

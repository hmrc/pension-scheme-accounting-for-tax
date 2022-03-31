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

package utils

import com.eclipsesource.schema.{JsonSource, SchemaValidator}
import play.api.libs.json._

import java.io.{File, FileInputStream}
import com.eclipsesource.schema.drafts.Version7._

import scala.collection.Seq

case class ErrorDetailsExtractor(schemaPath: Option[String], msgs: JsArray)

case class SchemaErrorPayload(errors: JsObject)

object ErrorDetailsExtractor {
  val schemaPath = "/resources/schemas/api-1538-file-aft-return-request-schema-0.1.0.json"
  val key = "/oneOf/0"

  def getErrors(error: Seq[(JsPath, Seq[JsonValidationError])]): String = {
    implicit val readSchemaErrorPayload = Json.reads[SchemaErrorPayload]
    implicit val readExtractErrorDetails = Json.reads[ErrorDetailsExtractor]

    val message = new StringBuilder("")
    error.flatMap(_._2).foldLeft(message){
      (stringBuilder, validationErrors) =>
        val errorAsJson: JsValue = Json.parse( validationErrors.args.mkString )
        val errorPayload = errorAsJson.as[SchemaErrorPayload].errors.value(key).result.asInstanceOf[JsDefined].value
        val errorsDetails = errorPayload.asInstanceOf[JsArray].value.map(element => Json.parse(element.toString()).as[ErrorDetailsExtractor])
        stringBuilder.append(errorsDetails.mkString(" "))
    }
    message.toString()
  }

}


class JSONPayloadSchemaValidator {


  def validateJsonPayload(jsonSchemaPath: String, data: JsValue): JsResult[JsValue] = {
    implicit val validator: SchemaValidator = SchemaValidator()
    val initialFile = new File(jsonSchemaPath)
    val targetStream = new FileInputStream(initialFile)
    val jsonSchema = JsonSource.schemaFromStream(targetStream).get
    validator.validate(jsonSchema, data)
  }

}


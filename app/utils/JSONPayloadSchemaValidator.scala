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

case class SchemaErrorDetails(schemaPath: Option[String], keyword: Option[String], msgs: JsArray)

case class SchemaErrorDetails2(schemaPath: String, keyword:String, instancePath: String, errors: JsObject)

object SchemaErrorDetails {

  implicit val reads = Json.reads[SchemaErrorDetails]
}

object SchemaErrorDetails2 {

  implicit val reads = Json.reads[SchemaErrorDetails2]
}

object SchemaPath {
  val schemaPath = "/resources/schemas/api-1538-file-aft-return-request-schema-0.1.0.json"

  def getErrors(error: Seq[(JsPath, Seq[JsonValidationError])]): String = {
    val message = new StringBuilder("")
    error.flatMap(x => x._2).foldLeft(message){
      (a, b) =>
        val errorAsJson: JsValue = Json.parse( b.args.mkString )
        val testError = errorAsJson.as[SchemaErrorDetails2]
        val extract = testError.errors.value("/oneOf/0").result.asInstanceOf[JsDefined].value
        val errorsDetails = extract.asInstanceOf[JsArray].value.map(element => Json.parse(element.toString()).as[SchemaErrorDetails])
        a.append(errorsDetails.mkString(" "))
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


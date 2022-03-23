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

import com.github.fge.jackson.JsonLoader
import com.github.fge.jsonschema.main.JsonSchemaFactory

import javax.inject.Inject
import play.api.{Environment, Logger}
import play.api.libs.json._

import scala.collection.JavaConverters._


class InvalidPayloadHandler @Inject()(environment: Environment) {

  def validateJson(jsonSchemaPath: String, data: JsValue): Option[String] = {
    val jsonSchemaFile = environment.getExistingFile(jsonSchemaPath)
    jsonSchemaFile match {
      case Some(schemaFile) =>
        val factory = JsonSchemaFactory.byDefault.getJsonSchema(schemaFile.toURI.toString)
        val json = JsonLoader.fromString(Json.stringify(data))
        val errors = factory.validate(json).iterator().asScala
        if(errors.isEmpty) None else Some(errors.mkString(","))
      case _ =>
        throw new RuntimeException("No Schema found")
    }
  }


}



case class ValidationFailure(failureType: String, message: String, value: Option[String])

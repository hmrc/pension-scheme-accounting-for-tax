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

package repository.model

import org.joda.time.{DateTime, DateTimeZone}
import play.api.libs.functional.syntax.toFunctionalBuilderOps
import play.api.libs.json.{Format, JsObject, JsString, Json, OFormat, Reads, __}
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats

import java.time.LocalDateTime

case class FileUploadStatus(_type: String, failureReason: Option[String]=None, message: Option[String]=None, downloadUrl: Option[String] = None, mimeType: Option[String] = None,
                            name: Option[String] = None, size: Option[Long] = None)

object FileUploadStatus {
  implicit val reads: OFormat[FileUploadStatus] = Json.format[FileUploadStatus]
}

case class FileUploadDataCache(uploadId: String, reference: String, status: FileUploadStatus, lastUpdated: LocalDateTime, expireAt: LocalDateTime)

object FileUploadDataCache {
 // implicit val dateFormat: Format[DateTime] = ReactiveMongoFormats.dateTimeFormats
  //implicit val reads: OFormat[FileUploadDataCache] = Json.format[FileUploadDataCache]
 // implicit val reads: OFormat[FileUploadDataCache] =
  implicit val reads : Reads[FileUploadDataCache] =
    (__ \ 'uploadId).read[String] and
      (__ \ 'reference).read[String] and
      (__ \ 'status).read[FileUploadStatus] and
      (__ \ 'lastUpdated).read[String].flatMap { dateTime =>
         (__ \ 'lastUpdated).json.put(JsString(LocalDateTime.parse(dateTime).toString))
      } and
      (__ \ 'expireAt).read[String].flatMap { dateTime =>
        (__ \ 'expireAt).json.put(JsString(LocalDateTime.parse(dateTime).toString))
      }

  def applyDataCache(uploadId: String,
                     reference: String,
                     status: FileUploadStatus,
                     lastUpdated: LocalDateTime = LocalDateTime.now(),
                     expireAt: LocalDateTime): FileUploadDataCache = {
    FileUploadDataCache(uploadId, reference, status, lastUpdated, expireAt)
  }
}



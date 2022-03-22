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

import play.api.libs.functional.syntax.{toFunctionalBuilderOps, unlift}
import play.api.libs.json._

import java.time.LocalDateTime

case class FileUploadStatus(_type: String, failureReason: Option[String]=None, message: Option[String]=None, downloadUrl: Option[String] = None, mimeType: Option[String] = None,
                            name: Option[String] = None, size: Option[Long] = None)

object FileUploadStatus {
  implicit val reads: OFormat[FileUploadStatus] = Json.format[FileUploadStatus]
}

case class FileUploadDataCache(uploadId: String, reference: String, status: FileUploadStatus,created: LocalDateTime,
                               lastUpdated: LocalDateTime, expireAt: LocalDateTime)

object FileUploadDataCache {
 implicit val reads : Reads[FileUploadDataCache] =(
(JsPath \  'uploadId).read[String] and
      (JsPath \ 'reference).read[String] and
      (JsPath \  'status).read[FileUploadStatus] and
        (JsPath \  'created).read[String] and
        (JsPath \  'lastUpdated).read[String] and
        (JsPath \  'expireAt).read[String]

  )((uploadId, reference, status,created, lastUpdated, expireAt)=>
  FileUploadDataCache(
    uploadId,
    reference,
    status,
    LocalDateTime.parse(created), LocalDateTime.parse(lastUpdated),
    LocalDateTime.parse(expireAt)
  ))   ///  reference & file size & uploadtime
  implicit val writes : Writes[FileUploadDataCache] =(
    (JsPath \  'uploadId).write[String] and
      (JsPath \ 'reference).write[String] and
      (JsPath \  'status).write[FileUploadStatus] and
      (JsPath \  'created).write[String] and
      (JsPath \  'lastUpdated).write[String] and
      (JsPath \  'expireAt).write[String]
    )(FileUploadDataCache => (FileUploadDataCache.uploadId,
    FileUploadDataCache.reference,
    FileUploadDataCache.status,
    FileUploadDataCache.created.toString,
    FileUploadDataCache.lastUpdated.toString,
    FileUploadDataCache.expireAt.toString))

  def applyDataCache(uploadId: String,
                     reference: String,
                     status: FileUploadStatus,
                     created: LocalDateTime = LocalDateTime.now(),
                     lastUpdated: LocalDateTime = LocalDateTime.now(),
                     expireAt: LocalDateTime): FileUploadDataCache = {
    FileUploadDataCache(uploadId, reference, status,created, lastUpdated, expireAt)
  }
}



/*
 * Copyright 2024 HM Revenue & Customs
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

import play.api.libs.functional.syntax.toFunctionalBuilderOps
import play.api.libs.json._

import java.time.Instant


case class FileUploadStatus(_type: String, failureReason: Option[String] = None, message: Option[String] = None,
                            downloadUrl: Option[String] = None, mimeType: Option[String] = None,
                            name: Option[String] = None, size: Option[Long] = None)

object FileUploadStatus {
  implicit val reads: OFormat[FileUploadStatus] = Json.format[FileUploadStatus]
}

case class FileUploadDataCache(uploadId: String, reference: String, status: FileUploadStatus, created: Instant,
                               lastUpdated: Instant, expireAt: Instant)

object FileUploadDataCache {
  implicit val reads: Reads[FileUploadDataCache] = (
    (JsPath \ Symbol("uploadId")).read[String] and
      (JsPath \ Symbol("reference")).read[String] and
      (JsPath \ Symbol("status")).read[FileUploadStatus] and
      (JsPath \ Symbol("created")).read[String] and
      (JsPath \ Symbol("lastUpdated")).read[String] and
      (JsPath \ Symbol("expireAt")).read[String]

    )((uploadId, reference, status, created, lastUpdated, expireAt) =>
    FileUploadDataCache(
      uploadId,
      reference,
      status,
      Instant.parse(created), Instant.parse(lastUpdated),
      Instant.parse(expireAt)
    ))
  implicit val writes: Writes[FileUploadDataCache] = (
    (JsPath \ Symbol("uploadId")).write[String] and
      (JsPath \ Symbol("reference")).write[String] and
      (JsPath \ Symbol("status")).write[FileUploadStatus] and
      (JsPath \ Symbol("created")).write[String] and
      (JsPath \ Symbol("lastUpdated")).write[String] and
      (JsPath \ Symbol("expireAt")).write[String]
    )(FileUploadDataCache => (FileUploadDataCache.uploadId,
    FileUploadDataCache.reference,
    FileUploadDataCache.status,
    FileUploadDataCache.created.toString,
    FileUploadDataCache.lastUpdated.toString,
    FileUploadDataCache.expireAt.toString))

  def applyDataCache(uploadId: String,
                     reference: String,
                     status: FileUploadStatus,
                     created: Instant = Instant.now(),
                     lastUpdated: Instant = Instant.now(),
                     expireAt: Instant): FileUploadDataCache = {
    FileUploadDataCache(uploadId, reference, status, created, lastUpdated, expireAt)
  }
}



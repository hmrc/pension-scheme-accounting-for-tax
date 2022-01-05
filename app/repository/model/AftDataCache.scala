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

import models.LockDetail
import org.joda.time.{DateTimeZone, DateTime}
import play.api.libs.json.{Format, Json, JsValue}
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats

case class SessionData(
  sessionId: String,
  lockDetail: Option[LockDetail],
  version: Int,
  accessMode: String,
  areSubmittedVersionsAvailable: Boolean
)
object SessionData {
  implicit val format: Format[SessionData] = Json.format[SessionData]
}

case class AftDataCache(id: String, sessionData: Option[SessionData], data: JsValue, lastUpdated: DateTime, expireAt: DateTime)

object AftDataCache {
  implicit val dateFormat: Format[DateTime] = ReactiveMongoFormats.dateTimeFormats
  implicit val format: Format[AftDataCache] = Json.format[AftDataCache]
  def applyDataCache(id: String,
                     sessionData: Option[SessionData],
                     data: JsValue,
                     lastUpdated: DateTime = DateTime.now(DateTimeZone.UTC),
                     expireAt: DateTime): AftDataCache = {
    AftDataCache(id, sessionData, data, lastUpdated, expireAt)
  }
}

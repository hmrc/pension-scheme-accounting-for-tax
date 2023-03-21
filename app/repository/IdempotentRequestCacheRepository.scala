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

package repository

import com.google.inject.{Inject, Singleton}
import uk.gov.hmrc.mongo.cache.{CacheIdType, MongoCacheRepository}
import uk.gov.hmrc.mongo.{MongoComponent, TimestampSupport}

import java.util.concurrent.TimeUnit
import scala.concurrent.ExecutionContext
import scala.concurrent.duration.Duration
@Singleton
class IdempotentRequestCacheRepository @Inject()(
                                   mongoComponent  : MongoComponent,
                                   timestampSupport: TimestampSupport
                                 )(implicit ec: ExecutionContext
                                 ) extends MongoCacheRepository(
  mongoComponent   = mongoComponent,
  collectionName   = "idempotentRequest",
  ttl              = Duration(1, TimeUnit.MINUTES),
  timestampSupport = timestampSupport,
  cacheIdType      = CacheIdType.SimpleCacheId
)
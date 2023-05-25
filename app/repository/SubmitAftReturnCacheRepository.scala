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

import com.google.inject.Inject
import org.mongodb.scala.MongoWriteException
import org.mongodb.scala.model._
import play.api.libs.json._
import play.api.{Configuration, Logging}
import repository.SubmitAftReturnCacheRepository._
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.PlayMongoRepository

import javax.inject.Singleton
import scala.concurrent.{ExecutionContext, Future}

object SubmitAftReturnCacheRepository {
  private val srnFieldName = "srn"
  private val externalUserIdFieldName = "externalUserId"
  private val quarterStartDateFieldName = "quarterStartDate"
  private val versionNumberFieldName = "versionNumber"
}

@Singleton
class SubmitAftReturnCacheRepository @Inject()(
                                                mongoComponent: MongoComponent,
                                                configuration: Configuration
                                              )(implicit val ec: ExecutionContext)
  extends PlayMongoRepository[JsValue](
    collectionName = configuration.get[String](path = "mongodb.aft-cache.submit-aft-return-cache.name"),
    mongoComponent = mongoComponent,
    domainFormat = implicitly,
    indexes = Seq(
      IndexModel(
        Indexes.ascending(srnFieldName),
        IndexOptions().name(srnFieldName).unique(true).background(true)),

      IndexModel(
        Indexes.ascending(externalUserIdFieldName),
        IndexOptions().name(externalUserIdFieldName).unique(true).background(true)),

      IndexModel(
        Indexes.ascending(quarterStartDateFieldName),
        IndexOptions().name(quarterStartDateFieldName).unique(true).background(true)),

      IndexModel(
        Indexes.ascending(versionNumberFieldName),
        IndexOptions().name(versionNumberFieldName).unique(true).background(true)),
    )
  ) with Logging {

  private lazy val documentExistsErrorCode = 11000

  def insertLockData(srn: String): Future[Boolean] = {
    collection.insertOne(JsString("POSTED: " + srn)).toFuture().map { _ => true }
      .recoverWith {
      case e: MongoWriteException if e.getCode == documentExistsErrorCode =>
                Future.successful(false)
    }
  }

  def getLockData(srn: String): Future[Option[JsValue]] = {
    collection.find(
      filter = Filters.eq(srnFieldName, srn)
    ).toFuture().map {
      _.headOption.map { jsValue =>
        (jsValue \ "data").as[JsValue]
      }
    }
  }
}

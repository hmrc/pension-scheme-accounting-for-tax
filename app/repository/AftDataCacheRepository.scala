/*
 * Copyright 2021 HM Revenue & Customs
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
import models.{ChargeAndMember, LockDetail}
import org.joda.time.{DateTimeZone, DateTime}
import org.slf4j.{Logger, LoggerFactory}
import play.api.Configuration
import play.api.libs.json._
import play.modules.reactivemongo.ReactiveMongoComponent
import reactivemongo.api.indexes.{Index, IndexType}
import reactivemongo.bson.{BSONDocument, BSONObjectID}
import reactivemongo.play.json.ImplicitBSONHandlers._
import repository.model.{SessionData, AftDataCache}
import services.BatchService
import services.BatchService.BatchType.Other
import services.BatchService.{BatchIdentifier, BatchInfo, BatchType}
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats

import scala.concurrent.{ExecutionContext, Future}

class AftDataCacheRepository @Inject()(
                                        mongoComponent: ReactiveMongoComponent,
                                        configuration: Configuration,
                                        batchService: BatchService
                                      )(implicit val ec: ExecutionContext)
  extends ReactiveRepository[JsValue, BSONObjectID](
    configuration.get[String](path = "mongodb.aft-cache.aft-journey.name"),
    mongoComponent.mongoConnector.db,
    implicitly
  ) {

  override val logger: Logger = LoggerFactory.getLogger("AftDataCacheRepository")

  private def expireInSeconds(batchType:BatchType): DateTime = {
    val ttlConfigItem = batchType match {
      case BatchType.SessionData => "mongodb.aft-cache.aft-journey.timeToLiveInSeconds"
      case _ => "mongodb.aft-cache.aft-journey.maxTimeToLiveInSeconds"
    }
    DateTime.now(DateTimeZone.UTC).plusSeconds(configuration.get[Int](path = ttlConfigItem))
  }

  val collectionIndexes = Seq(
    Index(
      Seq(
        ("uniqueAftId", IndexType.Ascending),
        ("batchType", IndexType.Ascending),
        ("batchNo", IndexType.Ascending)
      ), Some("unique-aft-batch"), unique = true, background = true),
    Index(Seq(("uniqueAftId", IndexType.Ascending)), Some("unique_Aft_Id"), unique = false, background = true),
    Index(Seq(("id", IndexType.Ascending)), Some("srn_startDt_key"), background = true),
    Index(Seq(("expireAt", IndexType.Ascending)), Some("dataExpiry"), unique = false, options = BSONDocument("expireAfterSeconds" -> 0))
  )

  (for {
    _ <- createIndex(collectionIndexes)
  } yield {
    ()
  }) recoverWith {
    case t: Throwable => Future.successful(logger.error(s"Error creating indexes on collection ${collection.name}", t))
  } andThen {
    case _ => CollectionDiagnostics.logCollectionInfo(collection)
  }

  private def createIndex(indexes: Seq[Index]): Future[Seq[Boolean]] = {
    Future.sequence(
      indexes.map { index =>
        collection.indexesManager.ensure(index) map { result =>
          logger.debug(s"Index $index was created successfully and result is: $result")
          result
        } recover {
          case e: Exception => logger.error(s"Failed to create index $index", e)
            false
        }
      }
    )
  }

  private def userDataBatchSize:Int = configuration.get[Int](path = "mongodb.aft-cache.aft-journey.userDataBatchSize")

  /*
  get session data
  if no session data then remove all docs
  if there is session data but no sessionData parameter specified then update the expireAt field
 */
  private def sessionDataHandler(
    id: String,
    sessionId: String,
    sessionData: Option[SessionData]
  ):Future[Option[BatchInfo]] = {
    getBatchesFromRepository(id, Some(sessionId), Some(BatchType.SessionData, 1)).map { optSessionDataBatch =>
      val ff = (sessionData, optSessionDataBatch) match {
        case (sd@Some(_), None) => remove(id, sessionId).map(_ => sd)
        case (_, None) => remove(id, sessionId).map(_ => None)
        case (None, Some(Seq(bi))) => Future.successful(Some(bi))
        case (_, Some(Seq(batchInfo))) =>
          sessionData.map{ sd =>
            Json.toJson(sd).as[JsObject]
          }
      }
      ff
    }
  }

  // scalastyle:off method.length
  // If batchIdentifier is none then recreate and save all batches, else just update the specified batch
  private def saveToRepository(
    id: String,
    sessionId: String,
    batchIdentifier: Option[BatchIdentifier],
    userData: JsValue,
    sessionData: Option[SessionData]
  )(implicit ec: ExecutionContext): Future[Boolean] = {
    logger.debug("Calling saveToRepository in AFT Data Cache Repository")
    println(s"\nSaveToRepository with batchIdentifier $batchIdentifier")

    sessionDataHandler(id, sessionId, sessionData).flatMap{ fosd =>
      val batches = batchIdentifier match {
        case None =>
          batchService.createBatches(
            userDataFullPayload = userData.as[JsObject],
            userDataBatchSize = userDataBatchSize,
            sessionDataPayload = sessionData.map{ sd =>
              Json.toJson(sd).as[JsObject]
            }
          )
        case Some(BatchIdentifier(Other, _)) =>
          Set(BatchInfo(Other, 1, batchService.getOtherJsObject(userData.as[JsObject])))
        case Some(BatchIdentifier(batchType, Some(batchNo))) =>
          batchService.getChargeTypeJsObjectForBatch(userData.as[JsObject], userDataBatchSize, batchType, batchNo).toSet[BatchInfo]
        case _ => throw new RuntimeException(s"Unable to update all members for a batch type")
      }

      def selector(batchType: BatchType, batchNo: Int): BSONDocument =
      BSONDocument("uniqueAftId" -> (id + sessionId), "batchType" -> batchType.toString, "batchNo" -> batchNo)

      println( s"\nSaveToRepository: updating/inserting batch(es) $batches")
      val setFutures = batches.map{ bi =>
        implicit val dateFormat: Format[DateTime] = ReactiveMongoFormats.dateTimeFormats

        val document: JsValue = Json.toJson(
          AftDataCache.applyDataCache(
            id = id,
            data = bi.jsValue,
            expireAt = expireInSeconds(bi.batchType)
          )
        )
        val modifier = BSONDocument("$set" -> document)

        collection.update.one(selector(bi.batchType, bi.batchNo), modifier, upsert = true)
      }
      // TODO: if updated ALL batches via upsert then we need to delete any documents which don't form part of this list of batches
      //val setRemovalFutures = if (batches.size > 1) { // If updating ALL batches then clean up by removing any other batches
      //  batches.map { b =>
      //    b.batchType
      //    b.batchNo
      //  }
      //} else {
      //  Set[Future]()
      //}
      Future.sequence(setFutures).map(_.forall(_.ok))
    }
  }

  private def removeNotSessionData(id: String, sessionId: String)(implicit ec: ExecutionContext): Future[Boolean] = {
    val selector = BSONDocument(
      "uniqueAftId" -> (id + sessionId),
      "batchType" -> BSONDocument("$ne" -> BatchType.SessionData.toString)
    )
    collection.delete.one(selector).map(_.ok)
  }

  def oldSave(id: String, userData: JsValue, sessionId: String)(implicit ec: ExecutionContext): Future[Boolean] = {
    logger.debug("Calling Save in AFT Cache")
    val document: JsValue = Json.toJson(AftDataCache.applyDataCache(
      id = id, None, data = userData, expireAt = expireInSeconds))
    val selector = BSONDocument("uniqueAftId" -> (id + sessionId))
    val modifier = BSONDocument("$set" -> document)
    collection.update.one(selector, modifier, upsert = true).map(_.ok)
  }

  def save(
    id: String,
    sessionId: String,
    chargeAndMember: Option[ChargeAndMember],
    userData: JsValue
  )(implicit ec: ExecutionContext): Future[Boolean] = {
    println( s"\nSAVE called with chargeAndMember $chargeAndMember")
    saveToRepository(
      id = id,
      sessionId = sessionId,
      batchIdentifier = batchService.batchIdentifierForChargeAndMember(chargeAndMember, userDataBatchSize),
      userData = userData,
      sessionData = None
    )
  }

  def setSessionData(
                      id: String,
                      lockDetail: Option[LockDetail],
                      userData: JsValue,
                      sessionId: String,
                      version: Int,
                      accessMode: String,
                      areSubmittedVersionsAvailable: Boolean
                    )(implicit ec: ExecutionContext): Future[Boolean] = {
    logger.debug(s"Calling setSessionData in AFT Cache")
    println( "\nsetSessionData called")

    saveToRepository(
      id = id,
      sessionId = sessionId,
      batchIdentifier = None,
      userData = userData,
      sessionData = Some(SessionData(sessionId, lockDetail, version, accessMode, areSubmittedVersionsAvailable))
    )
  }

  private def transformToBatchInfo(batchJsValue:JsValue):Option[BatchInfo] = {
    val optBatchType = (batchJsValue \ "batchType").asOpt[String].flatMap(BatchType.getBatchType)
    val optBatchNo = (batchJsValue \ "batchNo").asOpt[Int]
    val optJsValue = (batchJsValue \ "data").asOpt[JsValue]
    //println(s"\nTransformToBatchInfo: batchJsValue = $batchJsValue")
    //println(s"  ... parsed as: $optBatchType, $optBatchNo, $optJsValue")
    (optBatchType, optBatchNo, optJsValue) match {
      case (Some(batchType), Some(batchNo), Some(jsValue)) =>
        Some(BatchInfo(batchType, batchNo, jsValue))
      case _ => None
    }
  }

  private def getBatchesFromRepository(
    id: String,
    optSessionId: Option[String] = None,
    batchTypeAndNo: Option[(BatchType, Int)] = None
  )(implicit ec: ExecutionContext):Future[Option[Seq[BatchInfo]]] = {

    val findResults = (optSessionId, batchTypeAndNo) match {
      case (Some(sessionId), Some(Tuple2(bt, bn))) =>
        println(s"\nGetBatchesFromRepository: (1) found session id, batch type and no - sessionId=$sessionId, batchTypeAndNo=$batchTypeAndNo")
        find("uniqueAftId" -> (id + sessionId), "batchType" -> bt.toString, "batchNo" -> bn)
      case (None, Some(Tuple2(bt, bn))) =>
        println(s"\nGetBatchesFromRepository: (2) found batch type and no only - batchTypeAndNo=$batchTypeAndNo")
        find("batchType" -> bt.toString, "batchNo" -> bn)
      case (Some(sessionId), None) =>
        println(s"\nGetBatchesFromRepository: (3) found session id only, batch type and no - sessionId=$sessionId")
        find("uniqueAftId" -> (id + sessionId))
      case (None, None) =>
        println(s"\nGetBatchesFromRepository: (4) found id only $id")
        find("id" -> id)
    }
    findResults.map {
      case batches if batches.isEmpty =>
        println("\n  .. GetBatchesFromRepository: no batches found")
        None
      case batches =>
        println("\n  .. GetBatchesFromRepository: Batches found")
        val transformedBatches = batches.map{ batchJsValue =>
          transformToBatchInfo(batchJsValue).getOrElse(throw new RuntimeException(s"Unable to parse json:$batchJsValue"))
        }
        println(s"  ... GetBatchesFromRepository: Batches found (transformed): $transformedBatches")
        Some(transformedBatches)
    }
  }

  def getSessionData(sessionId: String, id: String)(implicit ec: ExecutionContext): Future[Option[SessionData]] = {
    logger.debug("Calling getSessionData in AFT Cache")
    println( "\nGET (session data)")
    // TODO: Could do this with only 1 retrieval from Mongo instead of 2
    getBatchesFromRepository(id, Some(sessionId), Some(BatchType.SessionData, 1)).flatMap {
      case Some(Seq(batchInfo)) =>
        println(s"  .. Some (sd) found - pulled back for $batchInfo")
        lockedBy(sessionId, id).map { lockDetail =>
          val sessionData = batchInfo.jsValue.asOpt[SessionData]
          sessionData.map{ _ copy(lockDetail = lockDetail)}
        }
      case _ =>
        println("  .. None (sd) found")
        Future.successful(None)
    }
  }

  def get(id: String, sessionId: String)(implicit ec: ExecutionContext): Future[Option[JsValue]] = {
    logger.debug("Calling get in AFT Cache")
    println( "\nGET (data)")
    getBatchesFromRepository(id, Some(sessionId)).map {
      case None =>
        println("  .. None found")
        None
      case Some(seqBatchInfo) =>
        println(s"  .. Some found - pulled back for $seqBatchInfo")
        Some(batchService.createUserDataFullPayload(seqBatchInfo))
    }
  }

  def lockedBy(sessionId: String, id: String)(implicit ec: ExecutionContext): Future[Option[LockDetail]] = {
    logger.debug("Calling lockedBy in AFT Cache")

    getBatchesFromRepository(
      id = id,
      batchTypeAndNo = Some(Tuple2(BatchType.SessionData, 1))
    ).map {
      case None => None
      case Some(seqBatchInfo) =>
        seqBatchInfo
          .flatMap(bi => (bi.jsValue \ "data").asOpt[SessionData].toSeq)
          .find(sd => sd.lockDetail.isDefined && sd.sessionId != sessionId)
          .flatMap(_.lockDetail)
    }
  }

  def remove(id: String, sessionId: String)(implicit ec: ExecutionContext): Future[Boolean] = {
    logger.warn(s"Removing document(s) from collection ${collection.name} id:$id")
    val selector = BSONDocument("uniqueAftId" -> (id + sessionId))
    collection.delete.one(selector).map(_.ok)
  }

  // TODO: Remove: I don't think this is used. It doesn't really make any sense. What is meant by lockedBy.sessionId? It isn't called from aft fe.
  def removeWithSessionId(sessionId: String)(implicit ec: ExecutionContext): Future[Boolean] = {
    logger.warn(s"Removing all document(s) with session id:$sessionId")

    // Remove lock

    val selector = BSONDocument("lockedBy.sessionId" -> sessionId)
    collection.delete.one(selector).map(_.ok)
  }
}

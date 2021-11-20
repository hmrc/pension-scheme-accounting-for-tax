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

package services

import models.enumeration.{WithName, Enumerable}
import play.api.libs.json._
import services.BatchService.BatchType.{ChargeE, Other, ChargeC, ChargeG, SessionData, ChargeD}
import helpers.JsonHelper._

class BatchService {
  import BatchService._

  def createBatches(
    userDataFullPayload: JsObject,
    userDataBatchSize: Int,
    sessionDataPayload:Option[JsObject] = None
  ): Set[BatchInfo] = {
    val batchInfoSessionDataSet = sessionDataPayload match {
      case Some(jsObject) => Set(BatchInfo(SessionData, 1, jsObject))
      case _ => Set()
    }
    Set(BatchInfo(Other, 1, getOtherJsObject(userDataFullPayload))) ++
      extractAndSplitMemberBasedCharges(userDataFullPayload, userDataBatchSize) ++
      batchInfoSessionDataSet
  }

  def createUserDataFullPayload(batches: Seq[BatchInfo]): JsObject = {
    val payloadForOtherBatch = batches
      .find(_.batchType == Other)
      .map(_.jsValue.as[JsObject])
      .getOrElse(Json.obj())

    nodeInfoSet.foldLeft[JsObject](payloadForOtherBatch) { (acc, ni) =>
      val allMembersForCharge = batches
        .filter(_.batchType == ni.batchType)
        .sortBy(_.batchNo)
        .foldLeft[JsArray](JsArray()){ (a, bi) => a ++ bi.jsValue.as[JsArray] }

      val payloadForChargePlusMembers = (acc \ ni.nodeNameCharge).asOpt[JsObject].map { jsObjForChargeNode =>
        val jsObjForChargeNodePlusMembers = jsObjForChargeNode ++ Json.obj(ni.nodeNameMembers -> allMembersForCharge)
        Json.obj(ni.nodeNameCharge -> jsObjForChargeNodePlusMembers)
      }.getOrElse(Json.obj())
      acc ++ payloadForChargePlusMembers
    }
  }

  def createSessionDataPayload(batches: Seq[BatchInfo]): Option[JsObject] =
    batches.find(_.batchType == SessionData).map(_.jsValue.as[JsObject])

  private def splitJsArrayIntoBatches(jsArray:JsArray, batchSize: Int, batchType: BatchType):Set[BatchInfo] = {
    val lastItem = jsArray.value.size - 1
    val maxBatch = (jsArray.value.size.toFloat / batchSize).ceil.toInt - 1
    (0 to maxBatch).map{ index =>
      val start = index * batchSize
      val end = Math.min(start + batchSize, lastItem + 1)
      BatchInfo(batchType, index + 1, JsArray(jsArray.value.slice(start, end)))
    }.toSet
  }

  private def extractAndSplitMemberBasedCharges(payload: JsObject, batchSize: Int):Set[BatchInfo] = {
    nodeInfoSet.flatMap { ni =>
      getChargeJsArray(payload, ni.nodeNameCharge, ni.nodeNameMembers) match {
        case None => Nil
        case Some(jsArray) => splitJsArrayIntoBatches(jsArray, batchSize, ni.batchType)
      }
    }
  }

  private def getOtherJsObject(payload: JsObject): JsObject = {
    nodeInfoSet.foldLeft[JsObject](payload){ case (acc, ni) =>
      (acc \ ni.nodeNameCharge).toOption match {
        case None => acc
        case _ => acc.removeObject( JsPath \ ni.nodeNameCharge \ ni.nodeNameMembers ).getOrElse(acc)
      }
    }
  }

  private def getChargeJsArray(payload: JsObject, node:String, arrayNode:String):Option[JsArray] =
    (payload \ node).toOption.flatMap{ jsValue => (jsValue.as[JsObject] \ arrayNode).asOpt[JsArray]}
}

object BatchService {
  // scalastyle:off magic.number

  private val nodeNameChargeC = "chargeCDetails"
  private val nodeNameChargeD = "chargeDDetails"
  private val nodeNameChargeE = "chargeEDetails"
  private val nodeNameChargeG = "chargeGDetails"

  private val nodeNameEmployers = "employers"
  private val nodeNameMembers = "members"

  sealed trait BatchType
  object BatchType extends Enumerable.Implicits {
    case object SessionData extends WithName("sessionData") with BatchType
    case object Other extends WithName("other") with BatchType
    case object ChargeC extends WithName("chargeC") with BatchType
    case object ChargeD extends WithName("chargeD") with BatchType
    case object ChargeE extends WithName("chargeE") with BatchType
    case object ChargeG extends WithName("chargeG") with BatchType
    private val batchTypes = Seq(SessionData, Other, ChargeC, ChargeD, ChargeE, ChargeG)
    def getBatchType(s:String):Option[BatchType] = batchTypes.find( _.toString == s)
  }

  case class BatchInfo(batchType: BatchType, batchNo: Int, jsValue: JsValue)

  private case class NodeInfo(nodeNameCharge:String, nodeNameMembers:String, batchType:BatchType)

  private val nodeInfoSet = Set(
    NodeInfo(nodeNameChargeC, nodeNameEmployers, ChargeC),
    NodeInfo(nodeNameChargeD, nodeNameMembers, ChargeD),
    NodeInfo(nodeNameChargeE, nodeNameMembers, ChargeE),
    NodeInfo(nodeNameChargeG, nodeNameMembers, ChargeG)
  )
}

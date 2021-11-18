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
import services.BatchService.BatchType.{ChargeE, Other, ChargeC, ChargeG, ChargeD}
import helpers.JsonHelper._

class BatchService {
  import BatchService._

  def split(payload: JsObject, batchSize: Int): Seq[BatchInfo] = {
    val batchesHeader = Seq(
      BatchInfo(Other, 1, getHeaderJsObject(payload))
    )
    val batchesChargeC = getChargeJsArray(payload, nodeNameChargeC, nodeNameEmployers) match {
      case None => Nil
      case Some(jsArray) => Seq(BatchInfo(ChargeC, 1, jsArray))
    }

    val batchesChargeD = getChargeJsArray(payload, nodeNameChargeD, nodeNameMembers) match {
      case None => Nil
      case Some(jsArray) => Seq(BatchInfo(ChargeD, 1, jsArray))
    }

    val batchesChargeE = getChargeJsArray(payload, nodeNameChargeE, nodeNameMembers) match {
      case None => Nil
      case Some(jsArray) => Seq(BatchInfo(ChargeE, 1, jsArray))
    }

    val batchesChargeG = getChargeJsArray(payload, nodeNameChargeG, nodeNameMembers) match {
      case None => Nil
      case Some(jsArray) => Seq(BatchInfo(ChargeG, 1, jsArray))
    }

    batchesHeader ++ batchesChargeC ++ batchesChargeD ++ batchesChargeE ++ batchesChargeG
  }

  private val chargeNodes: Seq[(String, String)] = Seq(
    (nodeNameChargeC, nodeNameEmployers),
    (nodeNameChargeD, nodeNameMembers),
    (nodeNameChargeE, nodeNameMembers),
    (nodeNameChargeG, nodeNameMembers),
  )

  private def getHeaderJsObject(payload: JsObject): JsObject = {
    removeChargeNodes( payload, chargeNodes)
  }

  private def removeChargeNodes(payload: JsObject, nodeInfo: Seq[(String, String)]):JsObject = {
    nodeInfo.foldLeft[JsObject](payload){ case (acc, Tuple2(nodeNameCharge, nodeNameJsArray)) =>
      (acc \ nodeNameCharge).toOption match {
        case None => acc
        case _ => acc.removeObject( JsPath \ nodeNameCharge \ nodeNameJsArray ).getOrElse(acc)
      }
    }
  }

  private def getChargeJsArray(payload: JsObject, node:String, arrayNode:String):Option[JsArray] = {
    (payload \ node).toOption.flatMap{ jsValue => (jsValue.as[JsObject] \ arrayNode).asOpt[JsArray]}
  }

  def join(batches: Seq[BatchInfo], nonMemberJson: JsObject): JsObject = Json.obj()
}

object BatchService {
  // scalastyle.off: magic.number

  private val nodeNameChargeC = "chargeCDetails"
  private val nodeNameChargeD = "chargeDDetails"
  private val nodeNameChargeE = "chargeEDetails"
  private val nodeNameChargeG = "chargeGDetails"

  private val nodeNameEmployers = "employers"
  private val nodeNameMembers = "members"

  sealed trait BatchType
  object BatchType extends Enumerable.Implicits {
    case object Other extends WithName("other") with BatchType
    case object ChargeC extends WithName("chargeC") with BatchType
    case object ChargeD extends WithName("chargeD") with BatchType
    case object ChargeE extends WithName("chargeE") with BatchType
    case object ChargeG extends WithName("chargeG") with BatchType
  }

  case class BatchInfo(batchType: BatchType, batchNo: Int, jsValue: JsValue)
}

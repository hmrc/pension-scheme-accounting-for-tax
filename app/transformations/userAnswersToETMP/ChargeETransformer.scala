/*
 * Copyright 2019 HM Revenue & Customs
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

package transformations.userAnswersToETMP

import play.api.libs.functional.syntax._
import play.api.libs.json.Reads._
import play.api.libs.json.{__, _}

import scala.annotation.tailrec

class ChargeETransformer {

  val doNothing: Reads[JsObject] = __.json.put(Json.obj())

  def transformToETMPData: Reads[JsObject] = {

    (__ \ 'chargeEDetails).readNullable {
      __.read(
        ((__ \ 'chargeDetails \ 'chargeTypeEDetails \ 'memberDetails).json.copyFrom((__ \ 'members).read(readsMembers)) and
          (__ \ 'chargeDetails \ 'chargeTypeEDetails \ 'totalAmount).json.copyFrom((__ \ 'totalChargeAmount).json.pick)).reduce
      )
    }.map {
      _.getOrElse(Json.obj())
    }
  }

 // def readsMembers: Reads[JsArray] = __.read(Reads.seq(getMemberDetails)).map(JsArray(_))
  def readsMembers: Reads[JsArray] = readsFiltered(_ \ "memberDetails", readsMember, "memberDetails").map(JsArray(_))

  def readsMember: Reads[JsObject] =
    (__ \ 'individualsDetails \ 'firstName).json.copyFrom((__ \ 'memberDetails \ 'firstName).json.pick) and
      (__ \ 'individualsDetails \ 'lastName).json.copyFrom((__ \ 'memberDetails \ 'lastName).json.pick) and
      (__ \ 'individualsDetails \ 'nino).json.copyFrom((__ \ 'memberDetails \ 'nino).json.pick) and
      (__ \ 'amountOfCharge).json.copyFrom((__ \ 'chargeDetails \ 'chargeAmount).json.pick) and
      (__ \ 'dateOfNotice).json.copyFrom((__ \ 'chargeDetails \ 'dateNoticeReceived).json.pick) and
      getPaidUnder237b and
      (__ \ 'taxYearEnding).json.copyFrom((__ \ 'annualAllowanceYear).json.pick) and
      (__ \ 'memberStatus).json.put(JsString("New")) reduce

  def getPaidUnder237b: Reads[JsObject] =
    (__ \ 'chargeDetails \ 'isPaymentMandatory).read[Boolean].flatMap { flag =>
      (__ \ 'paidUnder237b).json.put(if (flag) JsString("Yes") else JsString("No"))
    } orElse doNothing



  private def readsFiltered[T](isA: JsValue => JsLookupResult, readsA: Reads[T], detailsType: String): Reads[Seq[T]] = new Reads[Seq[T]] {
    override def reads(json: JsValue): JsResult[Seq[T]] = {
      json match {
        case JsArray(members) =>
          readFilteredSeq(JsSuccess(Nil), filterDeleted(members, detailsType), isA, readsA)
        case _ => JsSuccess(Nil)
      }
    }
  }

  private def filterDeleted(jsValueSeq: Seq[JsValue], detailsType: String): Seq[JsValue] = {
    jsValueSeq.filterNot { json =>
      (json \ detailsType \ "isDeleted").validate[Boolean] match {
        case JsSuccess(e, _) => e
        case _ => false
      }
    }
  }

  @tailrec
  private def readFilteredSeq[T](result: JsResult[Seq[T]], js: Seq[JsValue], isA: JsValue => JsLookupResult, reads: Reads[T]): JsResult[Seq[T]] = {
    js match {
      case Seq(h, t@_*) =>
        isA(h) match {
          case JsDefined(_) =>
            reads.reads(h) match {
              case JsSuccess(individual, _) => readFilteredSeq(JsSuccess(result.get :+ individual), t, isA, reads)
              case error@JsError(_) => error
            }
          case _ => readFilteredSeq(result, t, isA, reads)
        }
      case Nil => result
    }
  }

}

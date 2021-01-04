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

package transformations.userAnswersToETMP

import play.api.libs.functional.syntax._
import play.api.libs.json.Reads._
import play.api.libs.json._

import scala.annotation.tailrec

trait JsonTransformer {

  val doNothing: Reads[JsObject] = __.json.put(Json.obj())

  def readsMemberDetails: Reads[JsObject] =
    (__ \ 'individualsDetails \ 'firstName).json.copyFrom((__ \ 'memberDetails \ 'firstName).json.pick) and
      (__ \ 'individualsDetails \ 'lastName).json.copyFrom((__ \ 'memberDetails \ 'lastName).json.pick) and
      (__ \ 'individualsDetails \ 'nino).json.copyFrom((__ \ 'memberDetails \ 'nino).json.pick) reduce

  def readsFiltered[T](isA: JsValue => JsLookupResult, readsA: Reads[T]): Reads[Seq[T]] = new Reads[Seq[T]] {
    override def reads(json: JsValue): JsResult[Seq[T]] = {
      json match {
        case JsArray(members) =>
          readFilteredSeq(JsSuccess(Nil), members, isA, readsA)
        case _ => JsSuccess(Nil)
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

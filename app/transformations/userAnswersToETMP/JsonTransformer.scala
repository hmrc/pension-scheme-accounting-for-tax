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

package transformations.userAnswersToETMP

import play.api.libs.functional.syntax._
import play.api.libs.json.Reads._
import play.api.libs.json._

import scala.annotation.tailrec

trait JsonTransformer {

  private val pathMissingError = "error.path.missing"

  val doNothing: Reads[JsObject] = __.json.put(Json.obj())

  implicit class JsObjectReadsOps(rds: Reads[JsObject]) {
    val orElseEmptyOnMissingFields: Reads[JsObject] = Reads[JsObject](json =>
      rds.reads(json) match {
        case JsError(errs) if errs.forall { case (_, jerrs) => jerrs.forall(_.message == pathMissingError) } =>
          JsSuccess(JsObject.empty)
        case other => other
      }
    )
  }

  def removeEmptyObjects(arr: JsArray): JsArray = JsArray(arr.value.filter {
    case a: JsObject if a.keys.isEmpty => false
    case _ => true
  })

  def readsMemberDetails: Reads[JsObject] =
    ((__ \ Symbol("individualsDetails") \ Symbol("firstName")).json.copyFrom((__ \ Symbol("memberDetails") \ Symbol("firstName")).json.pick) and
      (__ \ Symbol("individualsDetails") \ Symbol("lastName")).json.copyFrom((__ \ Symbol("memberDetails") \ Symbol("lastName")).json.pick) and
      (__ \ Symbol("individualsDetails") \ Symbol("nino")).json.copyFrom((__ \ Symbol("memberDetails") \ Symbol("nino")).json.pick)).reduce

  def readsFiltered[T](isA: JsValue => JsLookupResult, readsA: Reads[T]): Reads[Seq[T]] = {
    case JsArray(members) =>
      readFilteredSeq(JsSuccess(Nil), members.toSeq, isA, readsA)
    case _ => JsSuccess(Nil)
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
      case _ => result
    }
  }

}

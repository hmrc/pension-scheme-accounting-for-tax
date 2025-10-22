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

package transformations.ETMPToUserAnswers

import play.api.libs.functional.syntax._
import play.api.libs.json.Reads._
import play.api.libs.json._


trait JsonTransformer {

  val doNothing: Reads[JsObject] = __.json.put(Json.obj())

  def readsMemberDetails: Reads[JsObject] =
    ((__ \ Symbol("memberDetails") \ Symbol("firstName")).json.copyFrom((__ \ Symbol("individualDetails") \ Symbol("firstName")).json.pick) and
      (__ \ Symbol("memberDetails") \ Symbol("lastName")).json.copyFrom((__ \ Symbol("individualDetails") \ Symbol("lastName")).json.pick) and
      (__ \ Symbol("memberDetails") \ Symbol("nino")).json.copyFrom((__ \ Symbol("individualDetails") \ Symbol("ninoRef")).json.pick)
      ).reduce: Reads[JsObject]

  protected val readsVersionRemovingZeroes: Reads[JsValue] = Reads {
    case JsString(s) => JsSuccess(JsNumber(s.toInt))
    case e => JsError(s"Not a Json string value: $e")
  }

  protected def ninoOf(memDetails: JsObject): String =
    (memDetails \ "memberDetails" \ "nino").asOpt[String].getOrElse("").trim

  protected def memberVersion(memVer: JsObject): Int =
    (memVer \ "memberAFTVersion").asOpt[Int].getOrElse(0)

  protected def deDupeAndSortMembers(
                                      members: Seq[JsObject],
                                      key: JsObject => (String, String, String)
                                    ): JsArray = {
    val grouped = members.groupBy(key)

    val deduped = grouped.values.map { group =>
      group.maxBy(memberVersion)
    }.toSeq

    val ordered = members.foldLeft(Seq.empty[JsObject]) { (acc, member) =>
      val k = key(member)
      if (acc.exists(account => key(account) == k)) acc
      else acc :+ deduped.find(duplicate => key(duplicate) == k).get
    }

    JsArray(ordered)
  }

  def defaultReadsMembers(readsMember: Reads[JsObject], chargeTypeCode: String): Reads[JsArray] = __.read(Reads.seq(readsMember)).map { seq =>
    deDupeAndSortMembers(seq, (member: JsObject) => (ninoOf(member), (member \ "memberStatus").asOpt[String].getOrElse(""), chargeTypeCode))
  }
}

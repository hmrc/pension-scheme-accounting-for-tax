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

package helpers

import play.api.libs.json.{JsString, JsValue}

import java.time.{LocalDate, LocalDateTime}
import java.time.format.DateTimeFormatter

object DateHelper {

  // scalastyle:off magic.number
  def getQuarterStartDate(endDate: String): String = {
    val monthAndDate = endDate.substring(5, 10) match {
      case "03-31" => "01-01"
      case "06-30" => "04-01"
      case "09-30" => "07-01"
      case "12-31" => "10-01"
      case _ => throw new RuntimeException(s"Invalid Date: $endDate")
    }
    endDate.substring(0,5) + monthAndDate
  }

  val dateFormatterYMD: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

  def formatDateDMYString(date: String): LocalDateTime = LocalDate.parse(date, dateFormatterYMD).atStartOfDay()

  val extractTaxYear: JsValue => JsString = dateString => JsString(formatDateDMYString(dateString.as[JsString].value).getYear.toString)

}

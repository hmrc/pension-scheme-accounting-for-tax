/*
 * Copyright 2020 HM Revenue & Customs
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

import org.scalatest.FreeSpec
import play.api.libs.json.{JsLookupResult, JsObject}
import transformations.generators.AFTETMPResponseGenerators

class ChargeGTransformerSpec extends FreeSpec with AFTETMPResponseGenerators {

  "A Charge G Transformer" - {
    "must transform ChargeGDetails from ETMP ChargeGDetails to UserAnswers" in {
      forAll(chargeGETMPGenerator) {
        etmpResponseJson =>
          val transformer = new ChargeGTransformer
          val transformedJson = etmpResponseJson.transform(transformer.transformToUserAnswers).asOpt.value

          def membersUAPath(i: Int): JsLookupResult = transformedJson \ "chargeGDetails" \ "members" \ i

          def membersETMPPath(i: Int): JsLookupResult = etmpResponseJson \ "chargeTypeGDetails" \ "memberDetails" \ i


          membersUAPath(0) \ "memberDetails" \ "firstName" mustBe membersETMPPath(0) \ "individualsDetails" \ "firstName"
          membersUAPath(0) \ "memberDetails" \ "lastName" mustBe membersETMPPath(0) \ "individualsDetails" \ "lastName"
          membersUAPath(0) \ "memberDetails" \ "dob" mustBe membersETMPPath(0) \ "individualsDetails" \ "dateOfBirth"

          membersUAPath(0) \ "chargeDetails" \ "qropsReferenceNumber" mustBe membersETMPPath(0) \ "qropsReference"
          membersUAPath(0) \ "chargeDetails" \ "qropsTransferDate" mustBe membersETMPPath(0) \ "dateOfTransfer"

          membersUAPath(0) \ "chargeAmounts" \ "amountTransferred" mustBe membersETMPPath(0) \ "amountTransferred"
          membersUAPath(0) \ "chargeAmounts" \ "amountTaxDue" mustBe membersETMPPath(0) \ "amountOfTaxDeducted"

          transformedJson \ "chargeGDetails" \ "totalChargeAmount" mustBe etmpResponseJson \ "chargeTypeGDetails" \ "totalOTCAmount"

          membersUAPath(1) \ "memberDetails" \ "firstName" mustBe membersETMPPath(1) \ "individualsDetails" \ "firstName"

          (transformedJson \ "chargeGDetails" \ "members").as[Seq[JsObject]].size mustBe 2

      }
    }
  }

}

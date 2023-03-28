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

package transformations.ETMPToUserAnswers

import play.api.libs.functional.syntax._
import play.api.libs.json.Reads._
import play.api.libs.json._

class ChargeCTransformer extends JsonTransformer {

  def transformToUserAnswers: Reads[JsObject] =
    (__ \ Symbol("chargeTypeC")).readNullable(__.read(
      ((__ \ Symbol("chargeCDetails") \ Symbol("amendedVersion")).json
        .copyFrom((__ \ Symbol("amendedVersion")).json.pick(readsVersionRemovingZeroes)) and
        (__ \ Symbol("chargeCDetails") \ Symbol("employers")).json.copyFrom((__ \ Symbol("memberDetails")).read(readsMembers)) and
        (__ \ Symbol("chargeCDetails") \ Symbol("totalChargeAmount")).json.copyFrom((__ \ Symbol("totalAmount")).json.pick)).reduce
    )).map(_.getOrElse(Json.obj()))

  def readsMembers: Reads[JsArray] = __.read(Reads.seq(readsMember)).map(JsArray(_))

  def readsMember: Reads[JsObject] =
    ((__ \ Symbol("memberStatus")).json.copyFrom((__ \ Symbol("memberStatus")).json.pick) and
      (__ \ Symbol("memberAFTVersion")).json.copyFrom((__ \ Symbol("memberAFTVersion"))
        .json.pick(readsVersionRemovingZeroes)) and
      __.read(readsEmployerTypeDetails) and
      (__ \ Symbol("addressDetails")).read(readsCorrespondenceAddressDetails) and
      (__ \ Symbol("chargeDetails") \ Symbol("paymentDate")).json.copyFrom((__ \ Symbol("dateOfPayment")).json.pick) and
      (__ \ Symbol("chargeDetails") \ Symbol("amountTaxDue")).json.copyFrom((__ \ Symbol("totalAmountOfTaxDue")).json.pick)
      ).reduce

  def readsEmployerTypeDetails: Reads[JsObject] =
    (__ \ Symbol("memberType")).read[String].flatMap {
      case "Individual" =>
        (
          (__ \ Symbol("whichTypeOfSponsoringEmployer")).json.put(JsString("individual")) and
            (__ \ Symbol("sponsoringIndividualDetails") \ Symbol("firstName"))
              .json.copyFrom((__ \ Symbol("individualDetails") \ Symbol("firstName")).json.pick) and
            (__ \ Symbol("sponsoringIndividualDetails") \ Symbol("lastName"))
              .json.copyFrom((__ \ Symbol("individualDetails") \ Symbol("lastName")).json.pick) and
            (__ \ Symbol("sponsoringIndividualDetails") \ Symbol("nino"))
              .json.copyFrom((__ \ Symbol("individualDetails") \ Symbol("ninoRef")).json.pick)
          ).reduce
      case _ =>
        (
          (__ \ Symbol("whichTypeOfSponsoringEmployer")).json.put(JsString("organisation")) and
            (__ \ Symbol("sponsoringOrganisationDetails") \ Symbol("name")).json
              .copyFrom((__ \ Symbol("organisationDetails") \ Symbol("compOrOrgName")).json.pick) and
            (__ \ Symbol("sponsoringOrganisationDetails") \ Symbol("crn")).json
              .copyFrom((__ \ Symbol("organisationDetails") \ Symbol("crnNumber")).json.pick)
          ).reduce
    }

  def readsCorrespondenceAddressDetails: Reads[JsObject] =
    ((__ \ Symbol("sponsoringEmployerAddress") \ Symbol("line1")).json.copyFrom((__ \ Symbol("addressLine1")).json.pick) and
      (__ \ Symbol("sponsoringEmployerAddress") \ Symbol("line2")).json.copyFrom((__ \ Symbol("addressLine2")).json.pick) and
      ((__ \ Symbol("sponsoringEmployerAddress") \ Symbol("line3")).json.copyFrom((__ \ Symbol("addressLine3")).json.pick) orElse doNothing) and
      ((__ \ Symbol("sponsoringEmployerAddress") \ Symbol("line4")).json.copyFrom((__ \ Symbol("addressLine4")).json.pick) orElse doNothing) and
      ((__ \ Symbol("sponsoringEmployerAddress") \ Symbol("postcode")).json.copyFrom((__ \ Symbol("postCode")).json.pick) orElse doNothing) and
      (__ \ Symbol("sponsoringEmployerAddress") \ Symbol("country")).json.copyFrom((__ \ Symbol("country")).json.pick)).reduce
}


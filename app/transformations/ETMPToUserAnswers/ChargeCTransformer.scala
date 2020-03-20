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

import play.api.libs.functional.syntax._
import play.api.libs.json.Reads._
import play.api.libs.json.{__, _}

class ChargeCTransformer extends JsonTransformer {

  def transformToUserAnswers: Reads[JsObject] =
    (__ \ 'chargeTypeCDetails).readNullable(__.read(
      ((__ \ 'chargeCDetails \ 'employers).json.copyFrom((__ \ 'memberDetails).read(readsMembers)) and
        (__ \ 'chargeCDetails \ 'totalChargeAmount).json.copyFrom((__ \ 'totalAmount).json.pick)).reduce
    )).map(_.getOrElse(Json.obj()))

  def readsMembers: Reads[JsArray] = __.read(Reads.seq(readsMember)).map(JsArray(_))

  def readsMember: Reads[JsObject] =
    (
      (__ \ 'memberTypeDetails).read(readsEmployerTypeDetails) and
        (__ \ 'correspondenceAddressDetails).read(readsCorrespondenceAddressDetails) and
        (__ \ 'chargeDetails \ 'paymentDate).json.copyFrom((__ \ 'dateOfPayment).json.pick) and
        (__ \ 'chargeDetails \ 'amountTaxDue).json.copyFrom((__ \ 'totalAmountOfTaxDue).json.pick)
      ).reduce

  def readsEmployerTypeDetails: Reads[JsObject] =
    (__ \ 'memberType).read[String].flatMap {
      case "Individual" =>
        (
          (__ \ 'whichTypeOfSponsoringEmployer).json.put(JsString("individual")) and
            (__ \ 'sponsoringIndividualDetails \ 'firstName).json.copyFrom((__ \ 'individualDetails \ 'firstName).json.pick) and
            (__ \ 'sponsoringIndividualDetails \ 'lastName).json.copyFrom((__ \ 'individualDetails \ 'lastName).json.pick) and
            (__ \ 'sponsoringIndividualDetails \ 'nino).json.copyFrom((__ \ 'individualDetails \ 'nino).json.pick) and
            (__ \ 'sponsoringIndividualDetails \ 'isDeleted).json.put(JsBoolean(false))
          ).reduce
      case _ =>
        (
          (__ \ 'whichTypeOfSponsoringEmployer).json.put(JsString("organisation")) and
            (__ \ 'sponsoringOrganisationDetails \ 'name).json.copyFrom((__ \ 'comOrOrganisationName).json.pick) and
            (__ \ 'sponsoringOrganisationDetails \ 'crn).json.copyFrom((__ \ 'crnNumber).json.pick) and
            (__ \ 'sponsoringOrganisationDetails \ 'isDeleted).json.put(JsBoolean(false))
          ).reduce
    }

  def readsCorrespondenceAddressDetails: Reads[JsObject] =
    ((__ \ 'sponsoringEmployerAddress \ 'line1).json.copyFrom((__ \ 'addressLine1).json.pick) and
      (__ \ 'sponsoringEmployerAddress \ 'line2).json.copyFrom((__ \ 'addressLine2).json.pick) and
      ((__ \ 'sponsoringEmployerAddress \ 'line3).json.copyFrom((__ \ 'addressLine3).json.pick) orElse doNothing) and
      ((__ \ 'sponsoringEmployerAddress \ 'line4).json.copyFrom((__ \ 'addressLine4).json.pick) orElse doNothing) and
      ((__ \ 'sponsoringEmployerAddress \ 'postcode).json.copyFrom((__ \ 'postalCode).json.pick) orElse doNothing) and
      (__ \ 'sponsoringEmployerAddress \ 'country).json.copyFrom((__ \ 'countryCode).json.pick)).reduce
}


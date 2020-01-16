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

package transformations.userAnswersToETMP

import play.api.libs.json.Reads._
import play.api.libs.json.{__, _}
import play.api.libs.functional.syntax._

class ChargeCTransformer extends JsonTransformer {

  def transformToETMPData: Reads[JsObject] =
    (__ \ 'chargeCDetails).readNullable(__.read(readsChargeC)).map(_.getOrElse(Json.obj()))

  def readsChargeC: Reads[JsObject] =
    (__ \ 'chargeDetails \ 'amountTaxDue).read[BigDecimal].flatMap { totalCharge =>
      if (!totalCharge.equals(0)) {
        ((__ \ 'chargeDetails \ 'chargeTypeCDetails \ 'totalAmount).json.copyFrom((__ \ 'chargeDetails \ 'amountTaxDue).json.pick) and
          (__ \ 'chargeDetails \ 'chargeTypeCDetails \ 'memberDetails).json.copyFrom(__.read(readsMembers))).reduce
      }
      else {
        doNothing
      }
    }

  def readsMembers: Reads[JsArray] = __.read[JsObject].flatMap(_ => readsMember.map(Json.arr(_)))

  def readsMember: Reads[JsObject] = (
    (__ \ 'memberStatus).json.put(JsString("New")) and
      readsMemberTypeDetails and
      ((__ \ 'correspondenceAddressDetails).json.copyFrom(__.read(readsCorrespondenceAddress)) and
      (__ \ 'dateOfPayment).json.copyFrom((__ \ 'chargeDetails \ 'paymentDate).json.pick) and
      (__ \ 'totalAmountOfTaxDue).json.copyFrom((__ \ 'chargeDetails \ 'amountTaxDue).json.pick)).reduce
    ).reduce

  def readsMemberTypeDetails: Reads[JsObject] =
    (__ \ 'isSponsoringEmployerIndividual).read[Boolean].flatMap {
      case true =>
        ((__ \ 'memberTypeDetails \ 'memberType).json.put(JsString("Individual")) and
          (__ \ 'memberTypeDetails \ 'individualDetails \ 'firstName).json.copyFrom((__ \ 'sponsoringIndividualDetails \ 'firstName).json.pick) and
          (__ \ 'memberTypeDetails \ 'individualDetails \ 'lastName).json.copyFrom((__ \ 'sponsoringIndividualDetails \ 'lastName).json.pick) and
          (__ \ 'memberTypeDetails \ 'individualDetails \ 'nino).json.copyFrom((__ \ 'sponsoringIndividualDetails \ 'nino).json.pick)).reduce
      case false =>
        ((__ \ 'memberTypeDetails \ 'memberType).json.put(JsString("Organisation")) and
          (__ \ 'memberTypeDetails \ 'comOrOrganisationName).json.copyFrom((__ \ 'sponsoringOrganisationDetails \ 'name).json.pick) and
          (__ \ 'memberTypeDetails \ 'crnNumber).json.copyFrom((__ \ 'sponsoringOrganisationDetails \ 'crn).json.pick)).reduce
    }

  def readsCorrespondenceAddress: Reads[JsObject] =
    (
      readsPostalCode and
        (__ \ 'addressLine1).json.copyFrom((__ \ 'sponsoringEmployerAddress \ 'line1).json.pick) and
        (__ \ 'addressLine2).json.copyFrom((__ \ 'sponsoringEmployerAddress \ 'line2).json.pick) and
        ((__ \ 'addressLine3).json.copyFrom((__ \ 'sponsoringEmployerAddress \ 'line3).json.pick) orElse doNothing) and
        ((__ \ 'addressLine4).json.copyFrom((__ \ 'sponsoringEmployerAddress \ 'line4).json.pick) orElse doNothing) and
        (__ \ 'countryCode).json.copyFrom((__ \ 'sponsoringEmployerAddress \ 'country).json.pick)
      ).reduce

  def readsPostalCode: Reads[JsObject] =
    (__ \ 'sponsoringEmployerAddress \ 'country).read[String].flatMap {
      case "GB" =>
        ((__ \ 'nonUKAddress).json.put(JsString("False")) and
          (__ \ 'postCode).json.copyFrom((__ \ 'sponsoringEmployerAddress \ 'postcode).json.pick)).reduce
      case _ =>
        ((__ \ 'nonUKAddress).json.put(JsString("True")) and
          ((__ \ 'postCode).json.copyFrom((__ \ 'sponsoringEmployerAddress \ 'postcode).json.pick) orElse doNothing)).reduce
    }
}

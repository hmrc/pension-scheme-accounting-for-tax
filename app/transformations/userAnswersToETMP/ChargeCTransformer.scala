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
import play.api.libs.json.{__, _}

class ChargeCTransformer extends JsonTransformer {

  def transformToETMPData: Reads[JsObject] =
    (__ \ 'chargeCDetails).readNullable(__.read(readsChargeC)).map(_.getOrElse(Json.obj())).orElseEmptyOnMissingFields

  private def readsChargeC: Reads[JsObject] =
    (__ \ 'totalChargeAmount).read[BigDecimal].flatMap { _ =>
        (((__ \ 'chargeDetails \ 'chargeTypeCDetails \ 'amendedVersion).json.copyFrom((__ \ 'amendedVersion).json.pick)
          orElse doNothing) and
          (__ \ 'chargeDetails \ 'chargeTypeCDetails \ 'memberDetails).json.copyFrom((__ \ 'employers).read(readsEmployers)) and
          (__ \ 'chargeDetails \ 'chargeTypeCDetails \ 'totalAmount).json.copyFrom((__ \ 'totalChargeAmount).json.pick)).reduce
    }


  private def readsEmployers: Reads[JsArray] =
    readsFiltered(_ \ "sponsoringIndividualDetails", readsEmployer).map(JsArray(_)).flatMap {
      filteredIndividuals =>
        readsFiltered(_ \ "sponsoringOrganisationDetails", readsEmployer).map(JsArray(_)).map {
          filteredOrganisations =>
            filteredIndividuals ++ filteredOrganisations
        }
    }.map(removeEmptyObjects)

  private def readsEmployer: Reads[JsObject] = (
    ((__ \ 'memberStatus).json.copyFrom((__ \ 'memberStatus).json.pick)
      orElse (__ \ 'memberStatus).json.put(JsString("New"))) and
      ((__ \ 'memberAFTVersion).json.copyFrom((__ \ 'memberAFTVersion).json.pick)
        orElse doNothing) and
      readsEmployerTypeDetails and
      ((__ \ 'correspondenceAddressDetails).json.copyFrom(__.read(readsCorrespondenceAddress)) and
        (__ \ 'dateOfPayment).json.copyFrom((__ \ 'chargeDetails \ 'paymentDate).json.pick) and
        (__ \ 'totalAmountOfTaxDue).json.copyFrom((__ \ 'chargeDetails \ 'amountTaxDue).json.pick)).reduce
    ).reduce.orElseEmptyOnMissingFields

  private def readsEmployerTypeDetails: Reads[JsObject] =
    (__ \ 'whichTypeOfSponsoringEmployer).read[String].flatMap {
      case "individual" =>
        ((__ \ 'memberTypeDetails \ 'memberType).json.put(JsString("Individual")) and
          (__ \ 'memberTypeDetails \ 'individualDetails \ 'firstName).json.copyFrom((__ \ 'sponsoringIndividualDetails \ 'firstName).json.pick) and
          (__ \ 'memberTypeDetails \ 'individualDetails \ 'lastName).json.copyFrom((__ \ 'sponsoringIndividualDetails \ 'lastName).json.pick) and
          (__ \ 'memberTypeDetails \ 'individualDetails \ 'nino).json.copyFrom((__ \ 'sponsoringIndividualDetails \ 'nino).json.pick)).reduce
      case "organisation" =>
        ((__ \ 'memberTypeDetails \ 'memberType).json.put(JsString("Organisation")) and
          (__ \ 'memberTypeDetails \ 'comOrOrganisationName).json.copyFrom((__ \ 'sponsoringOrganisationDetails \ 'name).json.pick) and
          (__ \ 'memberTypeDetails \ 'crnNumber).json.copyFrom((__ \ 'sponsoringOrganisationDetails \ 'crn).json.pick)).reduce
    }

  private def readsCorrespondenceAddress: Reads[JsObject] =
    (
      readsPostalCode and
        (__ \ 'addressLine1).json.copyFrom((__ \ 'sponsoringEmployerAddress \ 'line1).json.pick) and
        (__ \ 'addressLine2).json.copyFrom((__ \ 'sponsoringEmployerAddress \ 'line2).json.pick) and
        ((__ \ 'addressLine3).json.copyFrom((__ \ 'sponsoringEmployerAddress \ 'line3).json.pick) orElse doNothing) and
        ((__ \ 'addressLine4).json.copyFrom((__ \ 'sponsoringEmployerAddress \ 'line4).json.pick) orElse doNothing) and
        (__ \ 'countryCode).json.copyFrom((__ \ 'sponsoringEmployerAddress \ 'country).json.pick) and
        ((__ \ 'postalCode).json.copyFrom((__ \ 'sponsoringEmployerAddress \ 'postcode).json.pick) orElse doNothing)
      ).reduce

  private def readsPostalCode: Reads[JsObject] =
    (__ \ 'sponsoringEmployerAddress \ 'country).read[String].flatMap {
      case "GB" =>
        (__ \ 'nonUKAddress).json.put(JsString("False"))
      case _ =>
        (__ \ 'nonUKAddress).json.put(JsString("True"))
    }
}


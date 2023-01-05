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

package transformations.userAnswersToETMP

import play.api.libs.functional.syntax._
import play.api.libs.json.Reads._
import play.api.libs.json._

class ChargeCTransformer extends JsonTransformer {

  def transformToETMPData: Reads[JsObject] =
    (__ \ Symbol("chargeCDetails")).readNullable(__.read(readsChargeC)).map(_.getOrElse(Json.obj())).orElseEmptyOnMissingFields

  private def readsChargeC: Reads[JsObject] =
    (__ \ Symbol("totalChargeAmount")).read[BigDecimal].flatMap { _ =>
      (((__ \ Symbol("chargeDetails") \ Symbol("chargeTypeCDetails") \ Symbol("amendedVersion")).json.copyFrom((__ \ Symbol("amendedVersion")).json.pick)
        orElse doNothing) and
        (__ \ Symbol("chargeDetails") \ Symbol("chargeTypeCDetails") \ Symbol("memberDetails"))
          .json.copyFrom((__ \ Symbol("employers")).read(readsEmployers)) and
        (__ \ Symbol("chargeDetails") \ Symbol("chargeTypeCDetails") \ Symbol("totalAmount"))
          .json.copyFrom((__ \ Symbol("totalChargeAmount")).json.pick)).reduce
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
    ((__ \ Symbol("memberStatus")).json.copyFrom((__ \ Symbol("memberStatus")).json.pick)
      orElse (__ \ Symbol("memberStatus")).json.put(JsString("New"))) and
      ((__ \ Symbol("memberAFTVersion")).json.copyFrom((__ \ Symbol("memberAFTVersion")).json.pick)
        orElse doNothing) and
      readsEmployerTypeDetails and
      ((__ \ Symbol("correspondenceAddressDetails")).json.copyFrom(__.read(readsCorrespondenceAddress)) and
        (__ \ Symbol("dateOfPayment")).json.copyFrom((__ \ Symbol("chargeDetails") \ Symbol("paymentDate")).json.pick) and
        (__ \ Symbol("totalAmountOfTaxDue")).json.copyFrom((__ \ Symbol("chargeDetails") \ Symbol("amountTaxDue")).json.pick)).reduce
    ).reduce.orElseEmptyOnMissingFields

  private def readsEmployerTypeDetails: Reads[JsObject] =
    (__ \ Symbol("whichTypeOfSponsoringEmployer")).read[String].flatMap {
      case "individual" =>
        ((__ \ Symbol("memberTypeDetails") \ Symbol("memberType")).json.put(JsString("Individual")) and
          (__ \ Symbol("memberTypeDetails") \ Symbol("individualDetails") \ Symbol("firstName"))
            .json.copyFrom((__ \ Symbol("sponsoringIndividualDetails") \ Symbol("firstName")).json.pick) and
          (__ \ Symbol("memberTypeDetails") \ Symbol("individualDetails") \ Symbol("lastName"))
            .json.copyFrom((__ \ Symbol("sponsoringIndividualDetails") \ Symbol("lastName")).json.pick) and
          (__ \ Symbol("memberTypeDetails") \ Symbol("individualDetails") \ Symbol("nino"))
            .json.copyFrom((__ \ Symbol("sponsoringIndividualDetails") \ Symbol("nino")).json.pick)).reduce
      case "organisation" =>
        ((__ \ Symbol("memberTypeDetails") \ Symbol("memberType")).json.put(JsString("Organisation")) and
          (__ \ Symbol("memberTypeDetails") \ Symbol("comOrOrganisationName"))
            .json.copyFrom((__ \ Symbol("sponsoringOrganisationDetails") \ Symbol("name")).json.pick) and
          (__ \ Symbol("memberTypeDetails") \ Symbol("crnNumber")).
            json.copyFrom((__ \ Symbol("sponsoringOrganisationDetails") \ Symbol("crn")).json.pick)).reduce
    }

  private def readsCorrespondenceAddress: Reads[JsObject] =
    (
      readsPostalCode and
        (__ \ Symbol("addressLine1")).json.copyFrom((__ \ Symbol("sponsoringEmployerAddress") \ Symbol("line1")).json.pick) and
        (__ \ Symbol("addressLine2")).json.copyFrom((__ \ Symbol("sponsoringEmployerAddress") \ Symbol("line2")).json.pick) and
        ((__ \ Symbol("addressLine3")).json.copyFrom((__ \ Symbol("sponsoringEmployerAddress") \ Symbol("line3")).json.pick) orElse doNothing) and
        ((__ \ Symbol("addressLine4")).json.copyFrom((__ \ Symbol("sponsoringEmployerAddress") \ Symbol("line4")).json.pick) orElse doNothing) and
        (__ \ Symbol("countryCode")).json.copyFrom((__ \ Symbol("sponsoringEmployerAddress") \ Symbol("country")).json.pick) and
        ((__ \ Symbol("postalCode")).json.copyFrom((__ \ Symbol("sponsoringEmployerAddress") \ Symbol("postcode")).json.pick) orElse doNothing)
      ).reduce

  private def readsPostalCode: Reads[JsObject] =
    (__ \ Symbol("sponsoringEmployerAddress") \ Symbol("country")).read[String].flatMap {
      case "GB" =>
        (__ \ Symbol("nonUKAddress")).json.put(JsString("False"))
      case _ =>
        (__ \ Symbol("nonUKAddress")).json.put(JsString("True"))
    }
}


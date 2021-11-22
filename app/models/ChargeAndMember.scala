package models

import services.BatchService.BatchType
import models.ChargeType.{ChargeTypeLifetimeAllowance, ChargeTypeOverseasTransfer, ChargeTypeAnnualAllowance, ChargeTypeAuthSurplus}

case class ChargeAndMember(chargeType:ChargeType, memberNo:Option[Int]) {
  def batchType:BatchType = {
    chargeType match {
      case ChargeTypeAnnualAllowance => BatchType.ChargeE
      case ChargeTypeAuthSurplus => BatchType.ChargeC
      case ChargeTypeLifetimeAllowance => BatchType.ChargeD
      case ChargeTypeOverseasTransfer => BatchType.ChargeG
      case _ => BatchType.Other
    }
  }
}

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

package uk.gov.hmrc.agentclientmandate.models

import org.joda.time.DateTime
import play.api.libs.json._
import uk.gov.hmrc.agentclientmandate.models
import uk.gov.hmrc.agentclientmandate.models.PartyType.PartyType
import uk.gov.hmrc.agentclientmandate.models.Status.Status

case class ContactDetails(email: String, phone: Option[String] = None)

object ContactDetails {
  implicit val formats: OFormat[ContactDetails] = Json.format[ContactDetails]
}

object PartyType extends Enumeration {
  type PartyType = Value

  val Individual: models.PartyType.Value = Value
  val Organisation: models.PartyType.Value = Value

  implicit val enumFormat: Format[PartyType] = new Format[PartyType] {
    def reads(json: JsValue): JsSuccess[models.PartyType.Value] = JsSuccess(PartyType.withName(json.as[String]))

    def writes(`enum`: PartyType): JsString = JsString(enum.toString)
  }
}

case class Party(id: String, name: String, `type`: PartyType, contactDetails: ContactDetails)

object Party {
  implicit val formats: OFormat[Party] = Json.format[Party]
}

object Status extends Enumeration {
  type Status = Value

  val New: models.Status.Value = Value
  val Approved: models.Status.Value = Value
  val Active: models.Status.Value = Value
  val Rejected: models.Status.Value = Value
  val Expired: models.Status.Value = Value
  val PendingCancellation: models.Status.Value = Value
  val PendingActivation: models.Status.Value = Value
  val Cancelled: models.Status.Value = Value

  implicit val enumFormat: Format[Status] = new Format[Status] {
    def reads(json: JsValue): JsSuccess[models.Status.Value] = JsSuccess(Status.withName(json.as[String]))

    def writes(`enum`: Status): JsString = JsString(enum.toString)
  }
}

case class MandateStatus(status: Status, timestamp: DateTime, updatedBy: String)

object MandateStatus {
  val statusWrites: Writes[MandateStatus] = new Writes[MandateStatus] {
    override def writes(o: MandateStatus): JsValue = {
      val status: JsValue = Json.toJson(o.status)
      val updatedBy: JsValue = Json.toJson(o.updatedBy)
      val timestamp: Long = o.timestamp.getMillis

      Json.obj(
        "status" -> status,
        "timestamp" -> timestamp,
        "updatedBy" -> updatedBy
      )
    }
  }

  val reads: Reads[MandateStatus] = new Reads[MandateStatus] {
    override def reads(json: JsValue): JsResult[MandateStatus] = {
      val status = (json \ "status").asOpt[Status]
      val updatedBy = (json \ "updatedBy").asOpt[String]
      val timestamp = (json \ "timestamp").asOpt[Long] map { number =>
        new DateTime(number.longValue())
      }

      (status, updatedBy, timestamp) match {
        case (Some(st), Some(ub), Some(ts)) => JsSuccess(MandateStatus(st, ts, ub))
        case _                              => JsError("Could not parse MandateStatus")
      }
    }
  }

  implicit val formats: Format[MandateStatus] = Format(reads, statusWrites)
}

case class Service(id: String, name: String)

object Service {
  implicit val formats: OFormat[Service] = Json.format[Service]
}

case class Subscription(referenceNumber: Option[String] = None, service: Service)

object Subscription {
  implicit val formats: OFormat[Subscription] = Json.format[Subscription]
}

case class User(credId: String, name: String, groupId: Option[String] = None)

object User {
  implicit val formats: OFormat[User] = Json.format[User]
}

case class Mandate(id: String,
                   createdBy: User,
                   approvedBy: Option[User] = None,
                   assignedTo: Option[User] = None,
                   agentParty: Party,
                   clientParty: Option[Party] = None,
                   currentStatus: MandateStatus,
                   statusHistory: Seq[MandateStatus] = Nil,
                   subscription: Subscription,
                   clientDisplayName: String) {

  def updateStatus(newStatus: MandateStatus):Mandate = {
    new Mandate(id, createdBy, approvedBy, assignedTo, agentParty, clientParty, newStatus, statusHistory :+ currentStatus, subscription, clientDisplayName)
  }
}

object Mandate {
  implicit val formats: OFormat[Mandate] = Json.format[Mandate]
}

case class OldMandateReference(mandateId: String, atedRefNumber: String)

object OldMandateReference {
  implicit val formats: OFormat[OldMandateReference] = Json.format[OldMandateReference]
}

case class UserGroupIDs(principalGroupIds: List[String] = List(), delegatedGroupIds: List[String] = List())

object UserGroupIDs {
  implicit val formats: OFormat[UserGroupIDs] = Json.format[UserGroupIDs]
}
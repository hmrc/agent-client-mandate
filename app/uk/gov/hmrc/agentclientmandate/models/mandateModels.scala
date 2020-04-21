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

package uk.gov.hmrc.agentclientmandate.models

import org.joda.time.DateTime
import play.api.libs.json._
import uk.gov.hmrc.agentclientmandate.models.PartyType.PartyType
import uk.gov.hmrc.agentclientmandate.models.Status.Status

case class ContactDetails(email: String, phone: Option[String] = None)

object ContactDetails {
  implicit val formats = Json.format[ContactDetails]
}

object PartyType extends Enumeration {
  type PartyType = Value

  val Individual = Value
  val Organisation = Value

  implicit val enumFormat = new Format[PartyType] {
    def reads(json: JsValue) = JsSuccess(PartyType.withName(json.as[String]))

    def writes(enum: PartyType) = JsString(enum.toString)
  }
}

case class Party(id: String, name: String, `type`: PartyType, contactDetails: ContactDetails)

object Party {
  implicit val formats = Json.format[Party]
}

object Status extends Enumeration {
  type Status = Value

  val New = Value
  val Approved = Value
  val Active = Value
  val Rejected = Value
  val Expired = Value
  val PendingCancellation = Value
  val PendingActivation = Value
  val Cancelled = Value

  implicit val enumFormat = new Format[Status] {
    def reads(json: JsValue) = JsSuccess(Status.withName(json.as[String]))

    def writes(enum: Status) = JsString(enum.toString)
  }
}

case class MandateStatus(status: Status, timestamp: DateTime, updatedBy: String)

object MandateStatus {
  val statusWrites: Writes[MandateStatus] = new Writes[MandateStatus] {
    override def writes(o: MandateStatus): JsValue = {
      val status: JsValue = Json.toJson(o.status)
      val updatedBy: JsValue = Json.toJson(o.updatedBy)
      val timestamp: JsValue = Json.toJson(o.timestamp.getMillis)

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
      val timestamp = (json \ "timestamp").asOpt[JsNumber] map { number =>
        new DateTime(number.value.longValue())
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
  implicit val formats = Json.format[Service]
}

case class Subscription(referenceNumber: Option[String] = None, service: Service)

object Subscription {
  implicit val formats = Json.format[Subscription]
}

case class User(credId: String, name: String, groupId: Option[String] = None)

object User {
  implicit val formats = Json.format[User]
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
  implicit val formats = Json.format[Mandate]
}

case class OldMandateReference(mandateId: String, atedRefNumber: String)

object OldMandateReference {
  implicit val formats = Json.format[OldMandateReference]
}

case class UserGroupIDs(principalGroupIds: List[String] = List(), delegatedGroupIds: List[String] = List())

object UserGroupIDs {
  implicit val formats = Json.format[UserGroupIDs]
}
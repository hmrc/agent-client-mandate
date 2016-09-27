/*
 * Copyright 2016 HM Revenue & Customs
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

  implicit val enumFormat = new Format[Status] {
    def reads(json: JsValue) = JsSuccess(Status.withName(json.as[String]))

    def writes(enum: Status) = JsString(enum.toString)
  }
}

case class MandateStatus(status: Status, timestamp: DateTime, updatedBy: String)

object MandateStatus {
  implicit val formats = Json.format[MandateStatus]
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
                   subscription: Subscription)

object Mandate {
  implicit val formats = Json.format[Mandate]
}

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
import uk.gov.hmrc.agentclientmandate.models.Status.Status

case class ContactDetails(email: String, phone: String)

object ContactDetails {
  implicit val formats = Json.format[ContactDetails]
}

case class Party(id: String, name: String, `type`: String, contactDetails: ContactDetails)

object Party {
  implicit val formats = Json.format[Party]
}

object Status extends Enumeration {
  type Status = Value

  val Pending = Value

  implicit val enumFormat = new Format[Status] {
    def reads(json: JsValue) = JsSuccess(Status.withName(json.as[String]))
    def writes(enum: Status) = JsString(enum.toString)
  }
}

case class MandateStatus(status: Status, timestamp: DateTime, updatedBy: String)

object MandateStatus {
  implicit val formats = Json.format[MandateStatus]
}

case class Service(id: Option[String], name: String)

object Service {
  implicit val formats = Json.format[Service]
}

case class ClientMandate(id: String, createdBy: String, party: Party, currentStatus: MandateStatus, statusHistory: Option[Seq[MandateStatus]], service: Service)

object ClientMandate {
  implicit val formats = Json.format[ClientMandate]
}

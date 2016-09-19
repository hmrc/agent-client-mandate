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

import play.api.libs.json.Json
import uk.gov.hmrc.agentclientmandate.models.Status.Status

case class PartyDto(id: String, name: String, `type`: String)

object PartyDto {
  implicit val formats = Json.format[PartyDto]
}

case class ContactDetailsDto(email: String, phone: String)

object ContactDetailsDto {
  implicit val formats = Json.format[ContactDetailsDto]
}

case class ServiceDto(id: Option[String] = None, name: String)

object ServiceDto {
  implicit val formats = Json.format[ServiceDto]
}

case class MandateDto(party: PartyDto, contactDetails: ContactDetailsDto, service: ServiceDto)

object MandateDto {
  implicit val formats = Json.format[MandateDto]
}

case class CreateMandateResponse(mandateId: String)

object CreateMandateResponse {
  implicit val formats = Json.format[CreateMandateResponse]
}

case class StatusDto(status: Status)

object StatusDto {
  implicit val formats = Json.format[StatusDto]
}

case class SubscriptionDto(referenceNumber: String)

object SubscriptionDto {
  implicit val formats = Json.format[SubscriptionDto]
}

case class MandateUpdatedDto(mandateId: String, party: Option[PartyDto], subscription: Option[SubscriptionDto], status: Option[Status])

object MandateUpdatedDto {
  implicit val formats = Json.format[MandateUpdatedDto]
}
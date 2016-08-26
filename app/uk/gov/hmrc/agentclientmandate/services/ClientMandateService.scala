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

package uk.gov.hmrc.agentclientmandate.services

import play.api.libs.json.Json
import reactivemongo.bson.Macros
import uk.gov.hmrc.agentclientmandate.ClientMandateRepository
import uk.gov.hmrc.agentclientmandate.controllers.ClientMandateDto

import scala.concurrent.ExecutionContext.Implicits.global

case class Party(id: String, name: String, `type`: String)

object Party {
  implicit val formats = Json.format[Party]
}

case class ContactDetails(email: String, phone: String)

object ContactDetails {
  implicit val formats = Json.format[ContactDetails]
}

case class ClientMandate(id: String, createdBy: String, party: Party, contactDetails: ContactDetails)

object ClientMandate {
  implicit val formats = Json.format[ClientMandate]
}

trait ClientMandateService {

  def clientMandateRepository: ClientMandateRepository

  def createBananas(clientMandateDto: ClientMandateDto): ClientMandate = {
    ClientMandate(
      "123",
      "credid",
      Party(
        clientMandateDto.party.id,
        clientMandateDto.party.name,
        clientMandateDto.party.`type`
      ),
      ContactDetails(
        clientMandateDto.contactDetails.email,
        clientMandateDto.contactDetails.phone
      )
    )
  }

  def createMandate(clientMandateDto: ClientMandateDto) = {

    val clientMandate = createBananas(clientMandateDto)

    clientMandateRepository.insertMandate(clientMandate).map {
      a =>
        a.clientMandate.id
    }
  }

}

object ClientMandateService extends ClientMandateService {
  val clientMandateRepository = ClientMandateRepository()
}

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

import org.joda.time.DateTime
import uk.gov.hmrc.agentclientmandate.models._
import uk.gov.hmrc.agentclientmandate.repositories.ClientMandateRepository
import uk.gov.hmrc.play.http.HeaderCarrier
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

trait ClientMandateCreateService {

  def clientMandateRepository: ClientMandateRepository

  def generateClientMandate(clientMandateDto: ClientMandateDto)(implicit hc: HeaderCarrier): ClientMandate = {
    val credId = hc.gaUserId.getOrElse("credid")

    ClientMandate(
      id = createMandateId,
      createdBy = credId,
      agentParty = Party(
        clientMandateDto.party.id,
        clientMandateDto.party.name,
        clientMandateDto.party.`type`,
        ContactDetails(
          clientMandateDto.contactDetails.email,
          clientMandateDto.contactDetails.phone
        )
      ),
      clientParty = None,
      currentStatus = createPendingStatus(credId),
      statusHistory = None,
      subscription = Subscription(None, service = Service(clientMandateDto.service.name.toLowerCase, clientMandateDto.service.name))
      //service = Service(None, clientMandateDto.service.name)
    )
  }

  def createMandateId: String = {
    val tsRef = new DateTime().getMillis.toString.takeRight(8)
    s"AS$tsRef"
  }

  def createPendingStatus(credId: String): MandateStatus = MandateStatus(Status.Pending, DateTime.now(), credId)

  def createMandate(clientMandateDto: ClientMandateDto)(implicit hc: HeaderCarrier): Future[String] = {

    val clientMandate = generateClientMandate(clientMandateDto)

    clientMandateRepository.insertMandate(clientMandate).map {
      a =>
        a.clientMandate.id
    }
  }

}

object ClientMandateCreateService extends ClientMandateCreateService {
  val clientMandateRepository = ClientMandateRepository()
}

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
import uk.gov.hmrc.agentclientmandate.repositories.MandateRepository
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait MandateCreateService {

  def mandateRepository: MandateRepository

  def generateMandate(agentCode: String, createMandateDto: CreateMandateDto)(implicit hc: HeaderCarrier): Mandate = {

    val credId = hc.gaUserId.getOrElse("credid")

    Mandate(
      id = createMandateId,
      createdBy = User(credId, createMandateDto.agentParty.name, Some(agentCode)),
      agentParty = Party(
        createMandateDto.agentParty.id,
        createMandateDto.agentParty.name,
        createMandateDto.agentParty.`type`,
        ContactDetails(
          createMandateDto.agentParty.contactDetails.email,
          createMandateDto.agentParty.contactDetails.phone
        )
      ),
      clientParty = None,
      currentStatus = createNewStatus(credId),
      statusHistory = None,
      subscription = Subscription(None, service = Service(createMandateDto.service.name.toLowerCase, createMandateDto.service.name))
    )
  }

  def createMandateId: String = {
    val tsRef = new DateTime().getMillis.toString.takeRight(8)
    s"AS$tsRef"
  }

  def createNewStatus(credId: String): MandateStatus = MandateStatus(Status.New, DateTime.now(), credId)

  def createMandate(agentCode: String, createMandateDto: CreateMandateDto)(implicit hc: HeaderCarrier): Future[String] = {

    val clientMandate = generateMandate(agentCode, createMandateDto)

    mandateRepository.insertMandate(clientMandate).map(_.mandate.id)
  }

}

object MandateCreateService extends MandateCreateService {
  // $COVERAGE-OFF$
  val mandateRepository = MandateRepository()
  // $COVERAGE-ON$
}

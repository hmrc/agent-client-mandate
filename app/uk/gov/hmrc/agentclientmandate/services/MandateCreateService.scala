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
import uk.gov.hmrc.agentclientmandate.config.ApplicationConfig._
import uk.gov.hmrc.agentclientmandate.connectors.{AuthConnector, EtmpConnector}
import uk.gov.hmrc.agentclientmandate.models._
import uk.gov.hmrc.agentclientmandate.repositories.MandateRepository
import uk.gov.hmrc.play.http.HeaderCarrier
import play.api.Logger
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait MandateCreateService {

  def mandateRepository: MandateRepository

  def authConnector: AuthConnector

  def etmpConnector: EtmpConnector

  def createMandateId: String = {
    val tsRef = new DateTime().getMillis.toString.takeRight(8)
    s"AS$tsRef"
  }

  def createNewStatus(credId: String): MandateStatus = MandateStatus(Status.New, DateTime.now(), credId)

  def createMandate(agentCode: String, createMandateDto: CreateMandateDto)(implicit hc: HeaderCarrier): Future[String] = {

    Logger.debug(s"[MandateController][createMandate][agentCode] ${agentCode}")

    authConnector.getAuthority().flatMap { authority =>

      val agentPartyId = (authority \ "accounts" \ "agent" \ "agentBusinessUtr").as[String]
      val credId = (authority \ "credentials" \ "gatewayId").as[String]

      etmpConnector.getDetailsFromEtmp(agentPartyId).flatMap { etmpDetails =>
        val partyType = if ((etmpDetails \ "isAnIndividual").as[Boolean]) PartyType.Individual
                        else PartyType.Organisation

        val serviceName = createMandateDto.serviceName.toLowerCase

        val mandate = Mandate(
          id = createMandateId,
          createdBy = User(credId, agentPartyId, Some(agentCode)),
          agentParty = Party(
            agentPartyId,
            agentPartyId,
            partyType,
            ContactDetails(createMandateDto.email, None)
          ),
          clientParty = None,
          currentStatus = createNewStatus(credId),
          statusHistory = None,
          subscription = Subscription(None, Service(identifiers.getString(s"${serviceName}.serviceId"), serviceName))
        )

        mandateRepository.insertMandate(mandate).map(_.mandate.id)
      }
    }
  }

}

object MandateCreateService extends MandateCreateService {
  // $COVERAGE-OFF$
  val mandateRepository = MandateRepository()
  val authConnector = AuthConnector
  val etmpConnector = EtmpConnector
  // $COVERAGE-ON$
}

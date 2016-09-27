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
import play.api.Logger
import uk.gov.hmrc.agentclientmandate.config.ApplicationConfig._
import uk.gov.hmrc.agentclientmandate.connectors.{AuthConnector, EtmpConnector}
import uk.gov.hmrc.agentclientmandate.models._
import uk.gov.hmrc.agentclientmandate.repositories.MandateRepository
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait MandateCreateService {

  def mandateRepository: MandateRepository

  def authConnector: AuthConnector

  def etmpConnector: EtmpConnector

  def createMandateId: String = {
    val Eight = 8
    val tsRef = new DateTime().getMillis.toString.takeRight(Eight)
    s"AS$tsRef"
  }

  def createNewStatus(credId: String): MandateStatus = MandateStatus(Status.New, DateTime.now(), credId)

  def createMandate(agentCode: String, createMandateDto: CreateMandateDto)(implicit hc: HeaderCarrier): Future[String] = {

    authConnector.getAuthority().flatMap { authority =>

      val agentPartyId = (authority \ "accounts" \ "agent" \ "agentBusinessUtr").as[String]
      val credId = (authority \ "credentials" \ "gatewayId").as[String]

      etmpConnector.getAgentDetailsFromEtmp(agentPartyId).flatMap { etmpDetails =>

        val isAnIndividual = (etmpDetails \ "isAnIndividual").as[Boolean]

        val agentPartyName = if (isAnIndividual) {
          s"""${(etmpDetails \ "individual" \ "firstName").as[String]} ${(etmpDetails \ "individual" \ "lastName").as[String]}"""
        } else {
          s"""${(etmpDetails \ "organisation" \ "organisationName").as[String]}"""
        }

        val partyType = if (isAnIndividual) PartyType.Individual else PartyType.Organisation

        val serviceName = createMandateDto.serviceName.toLowerCase

        val currentStatus = createNewStatus(credId)

        val mandate = Mandate(
          id = createMandateId,
          createdBy = User(credId, agentPartyName, Some(agentCode)),
          agentParty = Party(
            agentPartyId,
            agentPartyName,
            partyType,
            ContactDetails(createMandateDto.email, None)
          ),
          clientParty = None,
          currentStatus = currentStatus,
          statusHistory = Seq(currentStatus),
          subscription = Subscription(None, Service(identifiers.getString(s"$serviceName.serviceId"), serviceName))
        )
        Logger.info(s"[MandateCreateService][createMandate] - mandate = $mandate")
        val x = mandateRepository.insertMandate(mandate).map(_.mandate.id)
        x
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

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
import play.api.libs.json.JsValue
import uk.gov.hmrc.agentclientmandate.config.ApplicationConfig._
import uk.gov.hmrc.agentclientmandate.connectors.{AuthConnector, EtmpConnector}
import uk.gov.hmrc.agentclientmandate.models._
import uk.gov.hmrc.agentclientmandate.repositories.{MandateCreated, MandateRepository}
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

        val agentPartyName: String = getAgentPartyName(etmpDetails, isAnIndividual)

        val partyType = getAgentPartyType(isAnIndividual)

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
          statusHistory = Nil,
          subscription = Subscription(None, Service(identifiers.getString(s"$serviceName.serviceId"), serviceName))
        )
        Logger.info(s"[MandateCreateService][createMandate] - mandate = $mandate")
        mandateRepository.insertMandate(mandate).map {
          case MandateCreated(mandate) => mandate.id
          case _ => throw new RuntimeException("Mandate not created")
        }
      }
    }
  }

  def createMandateForExistingRelationships(exsitingMandateDto: ExistingMandateDto): Future[Boolean] = {

    etmpConnector.getAtedSubscriptionDetails(exsitingMandateDto.clientSubscriptionId) flatMap { subscriptionJson =>
      val clientPartyId = (subscriptionJson \ "safeId").as[String]
      val clientPartyName = (subscriptionJson \ "organisationName").as[String]

      etmpConnector.getAgentDetailsFromEtmp(exsitingMandateDto.agentPartyId).flatMap { etmpDetails =>

        val isAnIndividual = (etmpDetails \ "isAnIndividual").as[Boolean]

        val agentPartyName: String = getAgentPartyName(etmpDetails, isAnIndividual)

        val agentPartyType = getAgentPartyType(isAnIndividual)

        val mandate = Mandate(
          id = createMandateId,
          createdBy = User(exsitingMandateDto.credId, clientPartyName),
          agentParty = Party(
            exsitingMandateDto.agentPartyId,
            agentPartyName,
            agentPartyType,
            ContactDetails("", None)
          ),
          clientParty = Some(Party(
            clientPartyId,
            clientPartyName,
            PartyType.Organisation,
            ContactDetails("", None)
          )),
          currentStatus = MandateStatus(Status.Active, DateTime.now, ""),
          statusHistory = Nil,
          subscription = Subscription(None, Service(identifiers.getString(s"${exsitingMandateDto.serviceName}.serviceId"), exsitingMandateDto.serviceName))
        )

        Logger.info(s"[MandateCreateService][createMandateForExistingRelationships] - mandate = $mandate")
        mandateRepository.insertMandate(mandate).map {
          case MandateCreated(mandate) => true
          case _ => false
        }
      }
    }
  }

  def getAgentPartyType(isAnIndividual: Boolean): PartyType.Value = {
    if (isAnIndividual) PartyType.Individual else PartyType.Organisation
  }

  def getAgentPartyName(etmpDetails: JsValue, isAnIndividual: Boolean): String = {
    if (isAnIndividual) {
      s"""${(etmpDetails \ "individual" \ "firstName").as[String]} ${(etmpDetails \ "individual" \ "lastName").as[String]}"""
    } else {
      s"""${(etmpDetails \ "organisation" \ "organisationName").as[String]}"""
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

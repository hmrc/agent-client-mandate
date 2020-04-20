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

package uk.gov.hmrc.agentclientmandate.services

import com.typesafe.config.{Config, ConfigFactory}
import javax.inject.Inject
import org.joda.time.DateTime
import play.api.libs.json.JsValue
import uk.gov.hmrc.agentclientmandate.Auditable
import uk.gov.hmrc.agentclientmandate.auth.AuthRetrieval
import uk.gov.hmrc.agentclientmandate.connectors.EtmpConnector
import uk.gov.hmrc.agentclientmandate.models._
import uk.gov.hmrc.agentclientmandate.repositories._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.audit.http.connector.AuditConnector

class DefaultMandateCreateService @Inject()(val etmpConnector: EtmpConnector,
                                            val relationshipService: RelationshipService,
                                            val mandateFetchService: MandateFetchService,
                                            val auditConnector: AuditConnector,
                                            val mandateRepo: MandateRepo) extends MandateCreateService {
  val mandateRepository: MandateRepository = mandateRepo.repository
  val identifiers: Config = ConfigFactory.load("identifiers.properties")
}

trait MandateCreateService extends Auditable {

  val identifiers: Config

  def mandateRepository: MandateRepository
  def etmpConnector: EtmpConnector
  def mandateFetchService: MandateFetchService
  def relationshipService: RelationshipService

  def createMandateId: String = {
    java.util.UUID.randomUUID.toString.take(8).toUpperCase()
  }

  def createNewStatus(credId: String): MandateStatus = {
    MandateStatus(Status.New, DateTime.now(), credId)
  }

  def createMandate(agentCode: String, createMandateDto: CreateMandateDto)(implicit hc: HeaderCarrier, ar: AuthRetrieval): Future[String] = {
    val agentPartyId = ar.agentBusinessUtr.value
    val credId = ar.govGatewayId

      etmpConnector.getRegistrationDetails(agentPartyId, "arn").flatMap { etmpDetails =>

        val isAnIndividual = (etmpDetails \ "isAnIndividual").as[Boolean]

        val agentPartyName: String = getPartyName(etmpDetails, isAnIndividual)

        val partyType = getPartyType(isAnIndividual)

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
          clientDisplayName = createMandateDto.displayName,
          clientParty = None,
          currentStatus = currentStatus,
          statusHistory = Nil,
          subscription = Subscription(None, Service(identifiers.getString(s"$serviceName.serviceId"), serviceName))
        )

        mandateRepository.insertMandate(mandate).map {
          case MandateCreated(m) =>
            doAudit("createMandate", agentCode, m)
            m.id
          case _ => throw new RuntimeException("Mandate not created")
        }
      }
  }

  def isIndividual(etmpDetails: JsValue): Boolean = {
    (etmpDetails \ "isAnIndividual").as[Boolean]
  }

  def getPartyType(isAnIndividual: Boolean): PartyType.Value = {
    if (isAnIndividual) PartyType.Individual else PartyType.Organisation
  }

  def getPartyName(etmpDetails: JsValue, isAnIndividual: Boolean): String = {
    if (isAnIndividual) {
      s"""${(etmpDetails \ "individual" \ "firstName").as[String]} ${(etmpDetails \ "individual" \ "lastName").as[String]}"""
    } else {
      s"""${(etmpDetails \ "organisation" \ "organisationName").as[String]}"""
    }
  }

  def createMandateForNonUKClient(ac: String, dto: NonUKClientDto)(implicit hc: HeaderCarrier, ar: AuthRetrieval): Future[Unit] = {
    val agentDetailsJsonFuture = etmpConnector.getRegistrationDetails(dto.arn, "arn")
    val nonUKClientDetailsJsonFuture = etmpConnector.getRegistrationDetails(dto.safeId, "safeid")

    def createMandateToSave(agentDetails: JsValue, clientDetails: JsValue): Mandate = {
      val isAgentAnIndividual = isIndividual(agentDetails)
      val agentPartyName: String = getPartyName(agentDetails, isAgentAnIndividual)
      val agentPartyType = getPartyType(isAgentAnIndividual)
      val agentCredId = ar.govGatewayId

      val isClientAnIndividual = isIndividual(clientDetails)
      val clientPartyName: String = getPartyName(clientDetails, isClientAnIndividual)
      val clientPartyType = getPartyType(isClientAnIndividual)

      Mandate(
        id = createMandateId,
        createdBy = User(agentCredId, agentPartyName, groupId = Some(ac)),
        approvedBy = Some(User(agentCredId, agentPartyName, groupId = Some(ac))),
        assignedTo = None,
        agentParty = Party(dto.arn, agentPartyName, agentPartyType, ContactDetails(dto.agentEmail)),
        clientParty = Some(Party(dto.safeId, clientPartyName, clientPartyType, ContactDetails(dto.clientEmail))),
        currentStatus = MandateStatus(Status.PendingActivation, DateTime.now(), updatedBy = agentCredId),
        statusHistory = Nil,
        subscription = Subscription(referenceNumber = Some(dto.subscriptionReference), service = Service(dto.service, dto.service)),
        clientDisplayName = dto.clientDisplayName
      )
    }

    for {
      agentDetails <- agentDetailsJsonFuture
      nonUKClientDetails <- nonUKClientDetailsJsonFuture
      m <- mandateRepository.insertMandate(createMandateToSave(agentDetails, nonUKClientDetails))
    } yield { m match {
      case MandateCreated(m) =>
        relationshipService.createAgentClientRelationship(m, ac)
        doAudit("createMandateNonUKClient", ac, m)
      case _ => throw new RuntimeException("Mandate not created for non-uk")
    }
    }
  }

  def updateMandateForNonUKClient(ac: String, dto: NonUKClientDto)(implicit hc: HeaderCarrier, ar: AuthRetrieval): Future[Unit] = {
    val agentDetailsJsonFuture = etmpConnector.getRegistrationDetails(dto.arn, "arn")
    val mandateFuture = mandateFetchService.fetchClientMandate(dto.mandateRef.getOrElse(throw new RuntimeException("No Old Non-UK Mandate ID recieved for updating mandate")))

    def updatedExistingNonUKMandateWithNewAgentDetails(mandate: Mandate, agentDetails: JsValue): Mandate = {
      val isAgentAnIndividual = isIndividual(agentDetails)
      val agentPartyName: String = getPartyName(agentDetails, isAgentAnIndividual)
      val agentPartyType = getPartyType(isAgentAnIndividual)
      val agentCredId = ar.govGatewayId

      mandate.copy(approvedBy = Some(User(agentCredId, agentPartyName, groupId = Some(ac))),
        agentParty = Party(dto.arn, agentPartyName, agentPartyType, ContactDetails(dto.agentEmail)),
        statusHistory = mandate.statusHistory :+ mandate.currentStatus,
        currentStatus = MandateStatus(Status.PendingActivation, DateTime.now(), updatedBy = agentCredId),
        clientDisplayName = dto.clientDisplayName)
    }

    for {
      mandateFetched <- mandateFuture
      agentDetails <- agentDetailsJsonFuture
      mu <- mandateRepository.updateMandate(updatedExistingNonUKMandateWithNewAgentDetails(getMandateStatus(mandateFetched), agentDetails))
    } yield {
        mu match {
            case MandateUpdated(m)=>

            relationshipService.createAgentClientRelationship (m, ac)
            doAudit ("updateMandateNonUKClient", ac, m)

            case _ => throw new RuntimeException ("Mandate not updated for non-uk")
        }
      }
    }

    def getMandateStatus(mfs: MandateFetchStatus): Mandate = {
      mfs match {
        case MandateFetched(mandate) => mandate
        case _ => throw new RuntimeException("No existing non-uk mandate details found for mandate id")
      }
    }

}
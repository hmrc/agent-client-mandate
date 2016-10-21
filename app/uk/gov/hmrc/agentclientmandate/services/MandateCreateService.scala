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
import uk.gov.hmrc.agentclientmandate.repositories._
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait MandateCreateService {

  def mandateRepository: MandateRepository

  def authConnector: AuthConnector

  def etmpConnector: EtmpConnector

  def relationshipService: RelationshipService

  def createMandateId: String = {
    val Eight = 8
    val tsRef = new DateTime().getMillis.toString.takeRight(Eight)
    s"AS$tsRef"
  }

  def createNewStatus(credId: String): MandateStatus = MandateStatus(Status.New, DateTime.now(), credId)

  def createMandate(agentCode: String, createMandateDto: CreateMandateDto)(implicit hc: HeaderCarrier): Future[String] = {

    authConnector.getAuthority().flatMap { authority =>

      val agentPartyId = (authority \ "accounts" \ "agent" \ "agentBusinessUtr").as[String]
      val credId = getCredId(authority)

      etmpConnector.getDetails(agentPartyId, "arn").flatMap { etmpDetails =>

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
          clientParty = None,
          currentStatus = currentStatus,
          statusHistory = Nil,
          subscription = Subscription(None, Service(identifiers.getString(s"$serviceName.serviceId"), serviceName))
        )
        Logger.info(s"[MandateCreateService][createMandate] - mandate = $mandate")
        mandateRepository.insertMandate(mandate).map {
          case MandateCreated(m) => m.id
          case _ => throw new RuntimeException("Mandate not created")
        }
      }
    }
  }

  def createMandateForExistingRelationships(ggRelationshipDto: GGRelationshipDto): Future[Boolean] = {

    etmpConnector.getAtedSubscriptionDetails(ggRelationshipDto.clientSubscriptionId) flatMap { subscriptionJson =>
      val clientPartyId = (subscriptionJson \ "safeId").as[String]
      val clientPartyName = (subscriptionJson \ "organisationName").as[String]

      etmpConnector.getDetails(ggRelationshipDto.agentPartyId, "arn").flatMap { etmpDetails =>

        val isAnIndividual = isIndividual(etmpDetails)

        val agentPartyName: String = getPartyName(etmpDetails, isAnIndividual)

        val agentPartyType = getPartyType(isAnIndividual)

        val mandate = Mandate(
          id = createMandateId,
          createdBy = User(ggRelationshipDto.credId, clientPartyName),
          agentParty = Party(
            ggRelationshipDto.agentPartyId,
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
          subscription = Subscription(None, Service(identifiers.getString(s"${ggRelationshipDto.serviceName}.serviceId"), ggRelationshipDto.serviceName))
        )

        Logger.info(s"[MandateCreateService][createMandateForExistingRelationships] - mandate = $mandate")
        mandateRepository.insertMandate(mandate).flatMap {
          case MandateCreated(m) =>
            mandateRepository.existingRelationshipProcessed(ggRelationshipDto).map {
              case ExistingRelationshipProcessed => true
              case ExistingRelationshipProcessError => false
            }
          case _ => Future.successful(false)
        }
      }
    }
  }

  def getCredId(authorityJson: JsValue): String = (authorityJson \ "credentials" \ "gatewayId").as[String]

  def isIndividual(etmpDetails: JsValue): Boolean = (etmpDetails \ "isAnIndividual").as[Boolean]

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

  def createMandateForNonUKClient(ac: String, dto: NonUKClientDto)(implicit hc: HeaderCarrier): Future[String] = {

    val agentDetailsJsonFuture = etmpConnector.getDetails(dto.arn, "arn")
    val nonUKClientDetailsJsonFuture = etmpConnector.getDetails(dto.safeId, "safeid")
    val authorityJsonFuture = authConnector.getAuthority()
    val mandateId = createMandateId

    def createMandateToSave(agentDetails: JsValue, clientDetails: JsValue, authorityJson: JsValue): Mandate = {
      val isAgentAnIndividual = isIndividual(agentDetails)
      val agentPartyName: String = getPartyName(agentDetails, isAgentAnIndividual)
      val agentPartyType = getPartyType(isAgentAnIndividual)
      val agentCredId = getCredId(authorityJson)

      val isClientAnIndividual = isIndividual(clientDetails)
      val clientPartyName: String = getPartyName(clientDetails, isClientAnIndividual)
      val clientPartyType = getPartyType(isClientAnIndividual)

      Mandate(
        id = mandateId,
        createdBy = User(agentCredId, agentPartyName, groupId = Some(ac)),
        approvedBy = Some(User(agentCredId, agentPartyName, groupId = Some(ac))),
        assignedTo = None,
        agentParty = Party(dto.arn, agentPartyName, agentPartyType, ContactDetails(dto.agentEmail)),
        clientParty = Some(Party(dto.safeId, clientPartyName, clientPartyType, ContactDetails(dto.clientEmail))),
        currentStatus = MandateStatus(Status.Active, DateTime.now(), updatedBy = agentCredId),
        statusHistory = Nil,
        subscription = Subscription(referenceNumber = Some(dto.subscriptionReference), service = Service(dto.service, dto.service))
      )
    }

    for {
      agentDetails <- agentDetailsJsonFuture
      clientDetails <- nonUKClientDetailsJsonFuture
      authorityJson <- authorityJsonFuture
      etmpRelationshipResponse <- {
        val mandateToSave = createMandateToSave(agentDetails, clientDetails, authorityJson)
        relationshipService.maintainRelationship(mandateToSave, ac, "Authorise")
      }
      mandateCreate <- {
        val mandateToSave = createMandateToSave(agentDetails, clientDetails, authorityJson)
        mandateRepository.insertMandate(mandateToSave)
      }
    } yield mandateCreate match {
      case MandateCreated(m) => m.id
      case _ => throw new RuntimeException("Mandate not created")
    }
  }

  def insertExistingRelationships(ggRelationshipDtos: Seq[GGRelationshipDto]): Future[ExistingRelationshipsInsert] = {
    mandateRepository.insertExistingRelationships(ggRelationshipDtos)
  }
}

object MandateCreateService extends MandateCreateService {
  // $COVERAGE-OFF$
  val mandateRepository = MandateRepository()
  val authConnector = AuthConnector
  val etmpConnector = EtmpConnector
  val relationshipService: RelationshipService = RelationshipService
  // $COVERAGE-ON$
}

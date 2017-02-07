/*
 * Copyright 2017 HM Revenue & Customs
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

package uk.gov.hmrc.agentclientmandate.controllers

import play.api.libs.json.Json
import play.api.mvc.Action
import uk.gov.hmrc.agentclientmandate._
import uk.gov.hmrc.agentclientmandate.models.{CreateMandateDto, GGRelationshipDto, Mandate, NonUKClientDto}
import uk.gov.hmrc.agentclientmandate.repositories._
import uk.gov.hmrc.agentclientmandate.services._
import uk.gov.hmrc.agentclientmandate.utils.MandateConstants
import uk.gov.hmrc.play.microservice.controller.BaseController

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

//scalastyle:off public.methods.have.type
trait MandateController extends BaseController with Auditable {

  def createService: MandateCreateService

  def relationshipService: RelationshipService

  def fetchService: MandateFetchService

  def updateService: MandateUpdateService

  def agentDetailsService: AgentDetailsService

  def emailNotificationService: NotificationEmailService

  def userType: String

  def create(agentCode: String) = Action.async(parse.json) { implicit request =>
    request.body.asOpt[CreateMandateDto] match {
      case Some(x) =>
        createService.createMandate(agentCode, x).map { mandateId =>
          Created(Json.parse(s"""{"mandateId": "$mandateId"}"""))
        }
      case None => Future.successful(BadRequest)
    }
  }

  def fetch(authId: String, mandateId: String) = Action.async { implicit request =>
    fetchService.fetchClientMandate(mandateId).map {
      case MandateFetched(x) => Ok(Json.toJson(x))
      case MandateNotFound => NotFound
    }
  }

  def fetchByClient(authId: String, clientId: String, service: String) = Action.async { implicit request =>
    fetchService.fetchClientMandate(clientId, service).map {
      case MandateFetched(x) => Ok(Json.toJson(x))
      case MandateNotFound => NotFound
    }
  }

  def fetchAll(agentCode: String, arn: String, serviceName: String) = Action.async { implicit request =>
    fetchService.getAllMandates(arn, serviceName).map {
      case Nil => NotFound
      case mandateList => Ok(Json.toJson(mandateList))
    }
  }

  def approve(org: String) = Action.async(parse.json) { implicit request =>
    request.body.asOpt[Mandate] match {
      case Some(newMandate) =>
        updateService.approveMandate(newMandate) map {
          case MandateUpdated(m) => {
            val agentEmail = m.agentParty.contactDetails.email
            val service = m.subscription.service.id
            emailNotificationService.sendMail(agentEmail, models.Status.Approved, service = service)
            doAudit("approved", "", m)
            Ok(Json.toJson(m))
          }
          case MandateUpdateError => InternalServerError
        }
      case None => Future.successful(BadRequest)
    }
  }

  def activate(agentCode: String, mandateId: String) = Action.async { implicit request =>
    fetchService.fetchClientMandate(mandateId).flatMap {
      case MandateFetched(mandate) if mandate.currentStatus.status == models.Status.Approved =>
        updateService.updateMandate(mandate, Some(models.Status.PendingActivation)).flatMap {
          case MandateUpdated(x) =>
            relationshipService.createAgentClientRelationship(x, agentCode)
            Future.successful(Ok)
          case MandateUpdateError => Future.successful(NotFound)
        }
      case MandateFetched(mandate) if mandate.currentStatus.status != models.Status.Approved =>
        throw new RuntimeException(s"Mandate with status ${mandate.currentStatus.status} cannot be activated")
      case MandateNotFound => Future.successful(NotFound)
    }
  }

  def remove(authCode: String, mandateId: String) = Action.async { implicit request =>
    fetchService.fetchClientMandate(mandateId).flatMap {
      case MandateFetched(mandate) if mandate.currentStatus.status == models.Status.Active || mandate.currentStatus.status == models.Status.Approved =>
        val agentCode = mandate.createdBy.groupId.getOrElse(throw new RuntimeException("agent code not found!"))
        updateService.updateMandate(mandate, Some(models.Status.PendingCancellation)).flatMap {
          case MandateUpdated(x) =>
            relationshipService.breakAgentClientRelationship(x, agentCode, userType)
            Future.successful(Ok)
          case MandateUpdateError => Future.successful(NotFound)
        }
      case MandateFetched(mandate) if mandate.currentStatus.status != models.Status.Active =>
        throw new RuntimeException(s"Mandate with status ${mandate.currentStatus.status} cannot be removed")
      case MandateNotFound => Future.successful(NotFound)
    }
  }

  def agentRejectsClient(ac: String, mandateId: String) = Action.async { implicit request =>
    fetchService.fetchClientMandate(mandateId).flatMap {
      case MandateFetched(mandate) => updateService.updateMandate(mandate, Some(models.Status.Rejected)).map {
        case MandateUpdated(m) => {
          val clientEmail = m.clientParty.map(_.contactDetails.email).getOrElse("")
          val service = m.subscription.service.id
          emailNotificationService.sendMail(clientEmail, models.Status.Rejected, service = service)
          doAudit("rejected", ac, m)
          Ok
        }
        case MandateUpdateError => InternalServerError
      }
      case MandateNotFound => Future.successful(NotFound)
    }
  }

  def getAgentDetails(agentCode: String) = Action.async { implicit request =>
    agentDetailsService.getAgentDetails(agentCode).map { agentDetails =>
      Ok(Json.toJson(agentDetails))
    }
  }

  def importExistingRelationships(agentCode: String) = Action.async(parse.json) { implicit request =>
    request.body.asOpt[Seq[GGRelationshipDto]] match {
      case Some(x) =>
        val ggdtoList = x map ( _.copy(agentCode = Some(agentCode)))
        createService.insertExistingRelationships(ggdtoList).map {
          case ExistingRelationshipsInserted | ExistingRelationshipsAlreadyExist => Ok
          case ExistingRelationshipsInsertError => throw new RuntimeException("Could not insert existing relationships")
        }
      case None => Future.successful(BadRequest)
    }
  }


  def createRelationship(ac: String) = Action.async(parse.json) { implicit request =>
    withJsonBody[NonUKClientDto] { nonUKClientDto =>
      createService.createMandateForNonUKClient(ac, nonUKClientDto) map { mandateId =>
        Created
      }
    }
  }

  def editMandate(agentCode: String) = Action.async(parse.json) { implicit request =>
    withJsonBody[Mandate] { updatedMandate =>
      updateService.updateMandate(updatedMandate) map {
        case MandateUpdated(mandate) =>  doAudit("edited", agentCode, mandate); Ok(Json.toJson(mandate))
        case MandateUpdateError => InternalServerError
      }
    }
  }

}

object MandateAgentController extends MandateController {
  // $COVERAGE-OFF$
  val createService = MandateCreateService
  val fetchService = MandateFetchService
  val updateService = MandateUpdateService
  val relationshipService = RelationshipService
  val agentDetailsService = AgentDetailsService
  val emailNotificationService = NotificationEmailService
  val userType = "agent"
  // $COVERAGE-ON$
}

object MandateClientController extends MandateController {
  // $COVERAGE-OFF$
  val createService = MandateCreateService
  val fetchService = MandateFetchService
  val updateService = MandateUpdateService
  val relationshipService = RelationshipService
  val agentDetailsService = AgentDetailsService
  val emailNotificationService = NotificationEmailService
  val userType = "client"
  // $COVERAGE-ON$
}

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

package uk.gov.hmrc.agentclientmandate.controllers

import akka.actor.Props
import play.api.Logger
import play.api.libs.json.Json
import play.api.mvc.Action
import play.libs.Akka
import uk.gov.hmrc.agentclientmandate._
import uk.gov.hmrc.agentclientmandate.actors.ImportExistingMandateActor
import uk.gov.hmrc.agentclientmandate.models.{CreateMandateDto, ExistingMandateDto, Mandate}
import uk.gov.hmrc.agentclientmandate.repositories.{MandateFetched, MandateNotFound, MandateUpdateError, MandateUpdated}
import uk.gov.hmrc.agentclientmandate.services._
import uk.gov.hmrc.play.microservice.controller.BaseController

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

//scalastyle:off public.methods.have.type
trait MandateController extends BaseController {

  def createService: MandateCreateService

  def relationshipService: RelationshipService

  def fetchService: MandateFetchService

  def updateService: MandateUpdateService

  def agentDetailsService: AgentDetailsService

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
          case MandateUpdated(y) => Ok(Json.toJson(y))
          case MandateUpdateError => InternalServerError
        }
      case None => Future.successful(BadRequest)
    }
  }

  def activate(agentCode: String, mandateId: String) = Action.async { implicit request =>
    fetchService.fetchClientMandate(mandateId).flatMap {
      case MandateFetched(mandate) if mandate.currentStatus.status == models.Status.Approved =>
        updateService.updateStatus(mandate, models.Status.PendingActivation).flatMap {
          case MandateUpdated(x) =>
            relationshipService.maintainRelationship(mandate, agentCode, "Authorise").flatMap { response =>
              response.status match {
                case OK =>
                  updateService.updateStatus(mandate, models.Status.Active).map {
                    case MandateUpdated(y) => Ok(Json.toJson(y))
                    case MandateUpdateError => InternalServerError
                  }
                case BAD_REQUEST => Future.successful(BadRequest)
                case _ => Future.successful(InternalServerError)
              }
            }
          case MandateUpdateError => Future.successful(NotFound)
        }
      case MandateFetched(mandate) if mandate.currentStatus.status != models.Status.Approved =>
        Logger.warn(s"[MandateController][remove] - mandate status not APPROVED")
        throw new RuntimeException(s"Mandate with status ${mandate.currentStatus.status} cannot be activated")
      case MandateNotFound => Future.successful(NotFound)
    }
  }

  def remove(authCode: String, mandateId: String) = Action.async { implicit request =>
    fetchService.fetchClientMandate(mandateId).flatMap {
      case MandateFetched(mandate) if mandate.currentStatus.status == models.Status.Active =>
        updateService.updateStatus(mandate, models.Status.PendingCancellation).flatMap {
          case MandateUpdated(x) =>
            val agentCode = mandate.createdBy.groupId.getOrElse(throw new RuntimeException("agent code not found!"))
            relationshipService.maintainRelationship(mandate, agentCode, "Deauthorise").flatMap { response =>
              response.status match {
                case OK =>
                  updateService.updateStatus(mandate, models.Status.Cancelled).map {
                    case MandateUpdated(y) => Ok(Json.toJson(y))
                    case MandateUpdateError => InternalServerError
                  }
                case BAD_REQUEST => Future.successful(BadRequest)
                case _ => Future.successful(InternalServerError)
              }
            }
          case MandateUpdateError => Future.successful(NotFound)
        }
      case MandateFetched(mandate) if mandate.currentStatus.status != models.Status.Active =>
        Logger.warn(s"[MandateController][remove] - mandate status not ACTIVE")
        throw new RuntimeException(s"Mandate with status ${mandate.currentStatus.status} cannot be removed")
      case MandateNotFound => Future.successful(NotFound)
    }
  }

  def agentRejectsClient(ac: String, mandateId: String) = Action.async { implicit request =>
    fetchService.fetchClientMandate(mandateId).flatMap {
      case MandateFetched(mandate) => updateService.updateStatus(mandate, models.Status.PendingCancellation).map {
        case MandateUpdated(y) => Ok
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
    request.body.asOpt[Seq[ExistingMandateDto]] match {
      case Some(x) => {
        lazy val existingRelationshipsImporter = Akka.system.actorOf(Props[ImportExistingMandateActor], "existing-relationships-importer")

        x.par.map {
          existingRelationshipsImporter ! _
        }

        Future.successful(Ok)
      }
      case None => Future.successful(BadRequest)
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
  // $COVERAGE-ON$
}

object MandateClientController extends MandateController {
  // $COVERAGE-OFF$
  val createService = MandateCreateService
  val fetchService = MandateFetchService
  val updateService = MandateUpdateService
  val relationshipService = RelationshipService
  val agentDetailsService = AgentDetailsService
  // $COVERAGE-ON$
}

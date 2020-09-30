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

package uk.gov.hmrc.agentclientmandate.controllers

import javax.inject.{Inject, Singleton}
import play.api.libs.json.{JsValue, Json}
import play.api.mvc.{Action, AnyContent, ControllerComponents}
import uk.gov.hmrc.agentclientmandate._
import uk.gov.hmrc.agentclientmandate.auth.AuthFunctionality
import uk.gov.hmrc.agentclientmandate.models._
import uk.gov.hmrc.agentclientmandate.repositories._
import uk.gov.hmrc.agentclientmandate.services._
import uk.gov.hmrc.agentclientmandate.utils.LoggerUtil.{logError, logWarn}
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.bootstrap.auth.DefaultAuthConnector
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class MandateAgentController @Inject()(val createService: MandateCreateService,
                                       val updateService: MandateUpdateService,
                                       val relationshipService: RelationshipService,
                                       val agentDetailsService: AgentDetailsService,
                                       val auditConnector: AuditConnector,
                                       val emailNotificationService: NotificationEmailService,
                                       val fetchService: MandateFetchService,
                                       val authConnector: DefaultAuthConnector,
                                       val cc: ControllerComponents) extends BackendController(cc) with MandateController {
  val userType = "agent"
}

@Singleton
class MandateClientController @Inject()(val createService: MandateCreateService,
                                        val updateService: MandateUpdateService,
                                        val relationshipService: RelationshipService,
                                        val agentDetailsService: AgentDetailsService,
                                        val auditConnector: AuditConnector,
                                        val emailNotificationService: NotificationEmailService,
                                        val fetchService: MandateFetchService,
                                        val authConnector: DefaultAuthConnector,
                                        val cc: ControllerComponents) extends BackendController(cc) with MandateController {
  val userType = "client"
}

trait MandateController extends BackendController with Auditable with AuthFunctionality {

  def createService: MandateCreateService
  def relationshipService: RelationshipService
  def fetchService: MandateFetchService
  def updateService: MandateUpdateService
  def agentDetailsService: AgentDetailsService
  def emailNotificationService: NotificationEmailService
  def userType: String

  implicit lazy val executionContext: ExecutionContext = defaultExecutionContext

  def create(agentCode: String): Action[JsValue] = Action.async(parse.json) { implicit request =>
    request.body.asOpt[CreateMandateDto] match {
      case Some(x) =>
        authRetrieval{ implicit ar =>
          createService.createMandate(agentCode, x) map { mandateId =>
            Created(Json.parse(s"""{"mandateId": "$mandateId"}"""))
          } recover {
            case e =>
              logError(s"[MandateController][create] Error trying to create mandate - ${e.getMessage} - ${e.getStackTrace.mkString("\n")}")
              NotFound
          }
        }
      case None =>
        logWarn("Could not parse request to create mandate")
        Future.successful(BadRequest)
    }
  }

  def fetch(authId: String, mandateId: String): Action[AnyContent] = Action.async { _ =>
    fetchService.fetchClientMandate(mandateId).map {
      case MandateFetched(x)  => Ok(Json.toJson(x))
      case MandateNotFound    =>
        logWarn("Could not find mandate: " + mandateId)
        NotFound
      case _ => throw new Exception("Unknown mandate status")
    }
  }

  def fetchByClient(authId: String, clientId: String, service: String): Action[AnyContent] = Action.async { _ =>
    fetchService.fetchClientMandate(clientId, service).map {
      case MandateFetched(x)  => Ok(Json.toJson(x))
      case MandateNotFound    => NotFound
      case _                  => throw new Exception("Unknown mandate status")
    }
  }

  def fetchAll(agentCode: String, arn: String, serviceName: String, credId: Option[String], displayName: Option[String]): Action[AnyContent] =
    Action.async { implicit request =>
      authRetrieval { implicit ar =>
        fetchService.getAllMandates(arn, serviceName, credId, displayName).map {
          case Nil => NotFound
          case mandateList => Ok(Json.toJson(mandateList))
        } recover {
          case e =>
            logError(s"[MandateController][fetchAll] Auth Retrieval error - ${e.getMessage} - ${e.getStackTrace.mkString("\n")}")
            NotFound
        }
      }
    }

  def approve(org: String): Action[JsValue] = Action.async(parse.json) { implicit request =>
    request.body.asOpt[Mandate] match {
      case Some(newMandate) =>
        authRetrieval { implicit ar =>
          updateService.approveMandate(newMandate) map {
            case MandateUpdated(m) =>
              val agentEmail = m.agentParty.contactDetails.email
              val service = m.subscription.service.id
              emailNotificationService.sendMail(agentEmail, models.Status.Approved, service = service, userType = Some("client"), recipient = Some("agent"))
              doAudit("approved", "", m)
              Ok(Json.toJson(m))
            case MandateUpdateError =>
              logWarn("Could not approve mandate to activate: " + newMandate.id)
              InternalServerError
            case _ => throw new Exception("Unknown mandate status")
          } recover {
            case e =>
              logError(s"[MandateController][approve] Auth Retrieval error - ${e.getMessage} - ${e.getStackTrace.mkString("\n")}")
              NotFound
          }
        }

      case None =>
        logWarn("Could not parse request to approve mandate")
        Future.successful(BadRequest)
    }
  }

  def activate(agentCode: String, mandateId: String): Action[AnyContent] = Action.async { implicit request =>
    logWarn("Attempting to activate mandate:" + mandateId)

    fetchService.fetchClientMandate(mandateId).flatMap {
      case MandateFetched(mandate) if mandate.currentStatus.status == models.Status.Approved =>
        authRetrieval { implicit ar =>
          updateService.updateMandate(mandate, Some(models.Status.PendingActivation)).flatMap {
          case MandateUpdated(x) =>
            relationshipService.createAgentClientRelationship(x, agentCode)
            Future.successful(Ok)
          case MandateUpdateError =>
            logWarn("Could not find mandate to activate after fetching: " + mandateId)
            Future.successful(NotFound)
          case _ => throw new Exception("Unknown mandate status")
          } recover {
            case e =>
              logError(s"[MandateController][activate] Auth Retrieval error - ${e.getMessage} - ${e.getStackTrace.mkString("\n")}")
              NotFound
          }
        }
      case MandateFetched(mandate) if mandate.currentStatus.status != models.Status.Approved =>
        throw new RuntimeException(s"Mandate with status ${mandate.currentStatus.status} cannot be activated")
      case MandateNotFound =>
        logWarn("Could not find mandate to activate: " + mandateId)
        Future.successful(NotFound)
      case _ => throw new Exception("Unknown mandate status")
    }
  }

  def remove(authCode: String, mandateId: String): Action[AnyContent] = Action.async { implicit request =>
    authRetrieval { implicit ar =>
    fetchService.fetchClientMandate(mandateId).flatMap {
      case MandateFetched(mandate) if mandate.currentStatus.status == models.Status.Active =>
        val agentCode = mandate.createdBy.groupId.getOrElse(throw new RuntimeException("agent code not found!"))
        updateService.updateMandate(mandate, Some(models.Status.PendingCancellation)).flatMap {
          case MandateUpdated(x) =>
            val service = x.subscription.service.id
            val agentEmail = x.agentParty.contactDetails.email
            val clientEmail = x.clientParty.map(_.contactDetails.email).getOrElse("")
            emailNotificationService.sendMail(agentEmail, models.Status.Cancelled, service = service, userType = Some("agent"), recipient = Some("agent"), prevStatus = Some(models.Status.Approved))
            emailNotificationService.sendMail(clientEmail, models.Status.Cancelled, service = service,userType = Some("agent"), recipient = Some("client"), prevStatus = Some(models.Status.Approved))
            relationshipService.breakAgentClientRelationship(x, agentCode, userType)
            Future.successful(Ok)
          case MandateUpdateError => {
            logWarn("Could not find mandate to remove after fetching: " + mandate.id)
            Future.successful(NotFound)
          }
          case _ => throw new Exception("Unknown mandate status")
        }
      case MandateFetched(mandate) if mandate.currentStatus.status == models.Status.Approved =>
        updateService.updateMandate(mandate, Some(models.Status.Cancelled)).flatMap {
          case MandateUpdated(x) =>
            val service = x.subscription.service.id
            emailNotificationService.sendMail(x.agentParty.contactDetails.email, models.Status.Cancelled, service = service, userType = Some("agent"), recipient = Some("agent"), prevStatus = Some(models.Status.Approved))
            doAudit("removed", "", x)
            Future.successful(Ok)
          case MandateUpdateError => {
            logWarn("Could not find mandate to remove after fetching: " + mandate.id)
            Future.successful(NotFound)
          }
          case _ => throw new Exception("Unknown mandate status")
        }
      case MandateFetched(mandate) if mandate.currentStatus.status == models.Status.New =>
        updateService.updateMandate(mandate, Some(models.Status.Cancelled)).flatMap {
          case MandateUpdated(x) =>
            doAudit("removed", "", x)
            Future.successful(Ok)
          case MandateUpdateError => {
            logWarn("Could not find mandate to remove after fetching: " + mandate.id)
            Future.successful(NotFound)
          }
          case _ => throw new Exception("Unknown mandate status")
        }
      case MandateFetched(mandate) =>
        throw new RuntimeException(s"Mandate with status ${mandate.currentStatus.status} cannot be removed")
      case MandateNotFound => {
        logWarn("Could not find mandate to remove: " + mandateId)
        Future.successful(NotFound)
      }
      case _ => throw new Exception("Unknown mandate status")
    } recover {
      case e =>
        logError(s"[MandateController][remove] Recover Error - ${e.getMessage} - ${e.getStackTrace.mkString("\n")}")
        NotFound
    }
    }
  }

  def agentRejectsClient(ac: String, mandateId: String): Action[AnyContent] = Action.async { implicit request =>
    fetchService.fetchClientMandate(mandateId).flatMap {
      case MandateFetched(mandate) =>
        authRetrieval { implicit ar =>
          updateService.updateMandate(mandate, Some(models.Status.Rejected)).map {
            case MandateUpdated(m) =>
              val clientEmail = m.clientParty.map(_.contactDetails.email).getOrElse("")
              val service = m.subscription.service.id
              emailNotificationService.sendMail(clientEmail, models.Status.Rejected, service = service, userType = Some("client"), recipient = Some("client"))
              doAudit("rejected", ac, m)
              Ok
            case MandateUpdateError => InternalServerError
            case _ => throw new Exception("Unknown update mandate status")
          } recover {
            case e =>
              logError(s"[MandateController][agentRejectsClient] Auth Retrieval error - ${e.getMessage} - ${e.getStackTrace.mkString("\n")}")
              NotFound
          }
        }
      case MandateNotFound =>
        logWarn("Could not find mandate for agent rejecting client: " + mandateId)
        Future.successful(NotFound)
    }
  }

  def getAgentDetails(agentCode: String): Action[AnyContent] = Action.async { implicit request =>
    authRetrieval { implicit ar =>
      agentDetailsService.getAgentDetails.map { agentDetails =>
        Ok(Json.toJson(agentDetails))
      } recover {
        case e =>
          logError(s"[MandateController][getAgentDetails] No AgentBusinessUtr found - ${e.getMessage} - ${e.getStackTrace.mkString("\n")}")
          NotFound
      }
    }
  }

  def createRelationship(ac: String): Action[JsValue] = Action.async(parse.json) { implicit request =>
    withJsonBody[NonUKClientDto] { nonUKClientDto =>
      authRetrieval{ implicit ar =>
        createService.createMandateForNonUKClient(ac, nonUKClientDto) map { _ => Created } recover {
          case e =>
            logError(s"[MandateController][createRelationship] Auth Retrieval error - ${e.getMessage} - ${e.getStackTrace.mkString("\n")}")
            NotFound
        }
      }
    }
  }

  def editMandate(agentCode: String): Action[JsValue] = Action.async(parse.json) { implicit request =>
    withJsonBody[Mandate] { updatedMandate =>
      authRetrieval { implicit ar =>
        updateService.updateMandate(updatedMandate) map {
          case MandateUpdated(mandate) =>  doAudit("edited", agentCode, mandate); Ok(Json.toJson(mandate))
          case MandateUpdateError => InternalServerError
          case _ => throw new Exception("Unknown update mandate status")
        } recover {
          case e =>
            logError(s"[MandateController][editMandate] Auth Retrieval error - ${e.getMessage} - ${e.getStackTrace.mkString("\n")}")
            NotFound
        }
      }
    }
  }

  def isAgentMissingEmail(agentCode: String, arn: String, service: String): Action[AnyContent] = Action.async { _ =>
    fetchService.getMandatesMissingAgentsEmails(arn, service).map {
      case Nil => NoContent
      case _ => Ok
    }
  }

  def updateAgentEmail(agentCode: String, arn: String, service: String): Action[JsValue] = Action.async(parse.json) { implicit request =>
    request.body.asOpt[String] match {
      case Some(x) if x.trim.length > 0 =>
        updateService.updateAgentEmail(arn, x, service).map {
          case MandateUpdatedEmail => Ok
          case MandateUpdateError => InternalServerError
          case _ => throw new Exception("Unknown update mandate status")
        }
      case _ =>
        logWarn("Could not find agent email address")
        Future.successful(BadRequest)
    }
  }

  def updateClientEmail(authCode: String, mandateId: String): Action[JsValue] = Action.async(parse.json) { implicit request =>
    request.body.asOpt[String] match {
      case Some(x) if x.trim.length > 0 =>
        updateService.updateClientEmail(mandateId, x).map {
          case MandateUpdatedEmail => Ok
          case MandateUpdateError => InternalServerError
          case _ => throw new Exception("Unknown update mandate status")
        }
      case _ =>
        logWarn("Could not find client email address")
        Future.successful(BadRequest)
    }
  }

  def updateAgentCredId(authCode: String): Action[JsValue] = Action.async(parse.json) { implicit request =>
    request.body.asOpt[String] match {
      case Some(x) if x.trim.length > 0 =>
        authRetrieval { implicit ar =>
          updateService.updateAgentCredId(x).map {
            case MandateUpdatedCredId => Ok
            case MandateUpdateError =>
              logWarn("Error updating cred id")
              InternalServerError
            case _ => throw new Exception("Unknown update mandate status")
          }
        } recover {
          case e =>
            logError(s"[MandateController][updateAgentCredId] Auth Retrieval error - ${e.getMessage} - ${e.getStackTrace.mkString("\n")}")
            NotFound
        }
      case _ =>
        logWarn("Could not find cred id")
        Future.successful(BadRequest)
    }
  }

  def getClientsThatCancelled(agentCode: String, arn: String, serviceName: String): Action[AnyContent] = Action.async { _ =>
    fetchService.fetchClientCancelledMandates(arn, serviceName).map {
      case Nil => NotFound
      case mandateList => Ok(Json.toJson(mandateList))
    }
  }

  def updateRelationship(ac: String): Action[JsValue] = Action.async(parse.json) { implicit request =>
    withJsonBody[NonUKClientDto] { oldMandate =>
      authRetrieval { implicit ar =>
        createService.updateMandateForNonUKClient(ac, oldMandate) map { _ => Created } recover {
          case e =>
            logError(s"[MandateController][updateRelationship] Auth Retrieval error - ${e.getMessage} - ${e.getStackTrace.mkString("\n")}")
            NotFound
        }
      }
    }
  }
}
/*
 * Copyright 2023 HM Revenue & Customs
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
import uk.gov.hmrc.agentclientmandate.models.Status.{Status => MandateStatus}
import uk.gov.hmrc.agentclientmandate.models._
import uk.gov.hmrc.agentclientmandate.repositories._
import uk.gov.hmrc.agentclientmandate.services._
import uk.gov.hmrc.agentclientmandate.utils.LoggerUtil.{logError, logWarn}
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.bootstrap.auth.DefaultAuthConnector
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class AgentController @Inject()(val createService: MandateCreateService,
                                val updateService: MandateUpdateService,
                                val relationshipService: RelationshipService,
                                val agentDetailsService: AgentDetailsService,
                                val auditConnector: AuditConnector,
                                val emailNotificationService: NotificationEmailService,
                                val fetchService: MandateFetchService,
                                val authConnector: DefaultAuthConnector,
                                val cc: ControllerComponents) extends BackendController(cc) with Auditable with AuthFunctionality {

  implicit lazy val executionContext: ExecutionContext = defaultExecutionContext

  def create(agentCode: String): Action[JsValue] = Action.async(parse.json) { implicit request =>
    request.body.asOpt[CreateMandateDto] match {
      case Some(x) =>
        authRetrieval { implicit ar =>
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

  def fetchAll(arn: String, serviceName: String, credId: Option[String], displayName: Option[String]): Action[AnyContent] =
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

  def activate(agentCode: String, mandateId: String): Action[AnyContent] = Action.async { implicit request =>
    logWarn("Attempting to activate mandate:" + mandateId)

    fetchService.fetchClientMandate(mandateId).flatMap {
      case MandateFetched(mandate) =>
        if (mandate.currentStatus.status == models.Status.Approved) {
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
        }
        else {
          logWarn(s"Mandate with status ${mandate.currentStatus.status} cannot be activated")
          Future.successful(UnprocessableEntity)
        }

      case MandateNotFound =>
        logWarn("Could not find mandate to activate: " + mandateId)
        Future.successful(NotFound)
    }
  }

  def agentRejectsClient(ac: String, mandateId: String): Action[AnyContent] = Action.async { implicit request =>
    fetchService.fetchClientMandate(mandateId).flatMap {
      case MandateFetched(mandate) =>
        authRetrieval { implicit ar =>
          val previousStatus: Option[MandateStatus] = mandate.statusHistory.lastOption.fold[Option[MandateStatus]](None)(mandateStatus => Some(mandateStatus.status))

          updateService.updateMandate(mandate, Some(models.Status.Rejected)).map {
            case MandateUpdated(m) =>
              val clientEmail = m.clientParty.map(_.contactDetails.email).getOrElse("")
              val service = m.subscription.service.id
              emailNotificationService.sendMail(clientEmail, models.Status.Rejected, service = service,
                userType = Some("agent"), recipient = Some("client"), recipientName = mandate.clientParty.fold("")(_.name), prevStatus = previousStatus)
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

  def getAgentDetails(): Action[AnyContent] = Action.async { implicit request =>
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
      authRetrieval { implicit ar =>
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
          case MandateUpdated(mandate) => doAudit("edited", agentCode, mandate); Ok(Json.toJson(mandate))
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

  def isAgentMissingEmail(arn: String, service: String): Action[AnyContent] = Action.async { _ =>
    fetchService.getMandatesMissingAgentsEmails(arn, service).map {
      case Nil => NoContent
      case _ => Ok
    }
  }

  def updateAgentEmail(arn: String, service: String): Action[JsValue] = Action.async(parse.json) { implicit request =>
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

  def updateAgentCredId(): Action[JsValue] = Action.async(parse.json) { implicit request =>
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

  def getClientsThatCancelled(arn: String, serviceName: String): Action[AnyContent] = Action.async { _ =>
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

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

import javax.inject.Inject
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, ControllerComponents, Result}
import uk.gov.hmrc.agentclientmandate._
import uk.gov.hmrc.agentclientmandate.auth._
import uk.gov.hmrc.agentclientmandate.connectors.{DefaultTaxEnrolmentConnector, UsersGroupSearchConnector}
import uk.gov.hmrc.agentclientmandate.models.Mandate
import uk.gov.hmrc.agentclientmandate.models.Status._
import uk.gov.hmrc.agentclientmandate.repositories._
import uk.gov.hmrc.agentclientmandate.services._
import uk.gov.hmrc.agentclientmandate.utils.LoggerUtil.{logError, logWarn}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.bootstrap.auth.DefaultAuthConnector
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import scala.concurrent.{ExecutionContext, Future}

class MandateController @Inject()(val createService: MandateCreateService,
                                  val updateService: MandateUpdateService,
                                  val relationshipService: RelationshipService,
                                  val agentDetailsService: AgentDetailsService,
                                  val auditConnector: AuditConnector,
                                  val emailNotificationService: NotificationEmailService,
                                  val fetchService: MandateFetchService,
                                  val authConnector: DefaultAuthConnector,
                                  val taxEnrolmentConnector: DefaultTaxEnrolmentConnector,
                                  val userGroupSearchConnector: UsersGroupSearchConnector,
                                  val cc: ControllerComponents) extends BackendController(cc) with Auditable with AuthFunctionality {


  implicit lazy val executionContext: ExecutionContext = defaultExecutionContext

  def fetch(mandateId: String): Action[AnyContent] = Action.async { _ =>
    fetchService.fetchClientMandate(mandateId).map {
      case MandateFetched(x) => Ok(Json.toJson(x))
      case MandateNotFound =>
        logWarn("Could not find mandate: " + mandateId)
        NotFound
      case _ => throw new Exception("Unknown mandate status")
    }
  }

  def remove(mandateId: String): Action[AnyContent] = Action.async { implicit request =>
    authRetrieval { implicit ar =>
      fetchService.fetchClientMandate(mandateId).flatMap {
        case MandateFetched(mandate) if mandate.currentStatus.status == models.Status.Active =>
          if (ar.userType == "agent") {
            ar.agentInformation.agentCode match {
              case Some(agentCode) =>
                breakRelationship(agentCode, mandate)
              case _ =>
                throw new RuntimeException("agent code not found (agent case)!")
            }
          } else {
            taxEnrolmentConnector.getGroupsWithEnrolmentDelegatedAted(ar.atedUtr.value).flatMap {
              case Some(groupId) =>
                userGroupSearchConnector.fetchAgentCode(groupId).flatMap {
                  case Some(agentCode) =>
                    breakRelationship(agentCode, mandate)
                  case _ =>
                    throw new RuntimeException("agent code not found (client case)!")
                }
              case _ =>
                logWarn("Delegated enrolment Allocation not found for the given Enrolment Key")
                cancelMandate(Active, mandate)
            }
          }
        case MandateFetched(mandate) if List(Approved, models.Status.New).contains(mandate.currentStatus.status) =>
          cancelMandate(mandate.currentStatus.status, mandate)
        case MandateFetched(mandate) =>
          throw new RuntimeException(s"Mandate with status ${mandate.currentStatus.status} cannot be removed")
        case MandateNotFound =>
          logWarn("Could not find mandate to remove: " + mandateId)
          Future.successful(NotFound)
        case _ => throw new Exception("Unknown mandate status")
      } recover {
        case e =>
          logError(s"[MandateController][remove] Recover Error - ${e.getMessage} - ${e.getStackTrace.mkString("\n")}")
          NotFound
      }
    }
  }

  private def breakRelationship(agentCode: String, mandate: Mandate)(implicit ar: AuthRetrieval, hc: HeaderCarrier): Future[Result] = {
    updateService.updateMandate(mandate, Some(PendingCancellation)).flatMap {
      case MandateUpdated(x) =>
        relationshipService.breakAgentClientRelationship(x, agentCode, ar.userType)
        Future.successful(Ok)
      case MandateUpdateError =>
        logWarn("Could not find mandate to remove after fetching: " + mandate.id)
        Future.successful(NotFound)
      case _ =>
        throw new Exception("Unknown mandate status")
    }
  }

  private def cancelMandate(status: models.Status.Value, mandate: Mandate)(implicit ar: AuthRetrieval, hc: HeaderCarrier): Future[Result] = {
    updateService.updateMandate(mandate, Some(Cancelled)).flatMap {
      case MandateUpdated(x) =>
        val service = x.subscription.service.id
        if (status != models.Status.New) {
          emailNotificationService.sendMail(x.agentParty.contactDetails.email, Cancelled,
            service = service, userType = Some(ar.userType), recipient = Some("agent"),
            prevStatus = Some(status), recipientName = mandate.agentParty.name)
        }
        doAudit("removed", "", x)
        Future.successful(Ok)
      case MandateUpdateError =>
        logWarn("Could not find mandate to remove after fetching: " + mandate.id)
        Future.successful(NotFound)
      case _ =>
        throw new Exception("Unknown mandate status")
    }
  }
}
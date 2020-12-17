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
class ClientController @Inject()(val createService: MandateCreateService,
                                  val updateService: MandateUpdateService,
                                  val relationshipService: RelationshipService,
                                  val agentDetailsService: AgentDetailsService,
                                  val auditConnector: AuditConnector,
                                  val emailNotificationService: NotificationEmailService,
                                  val fetchService: MandateFetchService,
                                  val authConnector: DefaultAuthConnector,
                                  val cc: ControllerComponents) extends BackendController(cc) with Auditable with AuthFunctionality {


  implicit lazy val executionContext: ExecutionContext = defaultExecutionContext

  def fetchByClient(clientId: String, service: String): Action[AnyContent] = Action.async { _ =>
    fetchService.fetchClientMandate(clientId, service).map {
      case MandateFetched(x)  => Ok(Json.toJson(x))
      case MandateNotFound    => NotFound
      case _                  => throw new Exception("Unknown mandate status")
    }
  }

  def approve(): Action[JsValue] = Action.async(parse.json) { implicit request =>
    request.body.asOpt[Mandate] match {
      case Some(newMandate) =>
        authRetrieval { implicit ar =>
          updateService.approveMandate(newMandate) map {
            case MandateUpdated(m) =>
              val agentEmail = m.agentParty.contactDetails.email
              val service = m.subscription.service.id
              emailNotificationService.sendMail(agentEmail, models.Status.Approved, service = service,
                userType = Some("client"), recipient = Some("agent"), recipientName = m.agentParty.name)
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

  def updateClientEmail(mandateId: String): Action[JsValue] = Action.async(parse.json) { implicit request =>
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
}
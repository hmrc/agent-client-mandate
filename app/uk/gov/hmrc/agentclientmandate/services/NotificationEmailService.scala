/*
 * Copyright 2018 HM Revenue & Customs
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

import uk.gov.hmrc.agentclientmandate.connectors.{EmailConnector, EmailStatus}
import uk.gov.hmrc.agentclientmandate.models.Status
import uk.gov.hmrc.agentclientmandate.models.Status.Status

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

import play.api.Logger
import uk.gov.hmrc.http.HeaderCarrier


trait NotificationEmailService {

  def emailConnector: EmailConnector

  def sendMail(emailString: String, action: Status, userType: Option[String] = None, service: String, prevStatus: Option[Status] = None)(implicit hc: HeaderCarrier): Future[EmailStatus] = {
    def template: String = {
      (action, userType, prevStatus) match {
        case (Status.Approved, _, _) => "client_approves_mandate"
        case (Status.Active, Some("agent"), _) => "agent_self_auth_activates_mandate"
        case (Status.Active, _, _) => "agent_activates_mandate"
        case (Status.Rejected, _, _) => "agent_rejects_mandate"
        case (Status.Cancelled, Some("agent"), _) => "agent_self_auth_deactivates_mandate"
        case (Status.Cancelled, Some("client"), Some(Status.Approved)) => "client_removes_mandate"
        case (Status.Cancelled, Some("client"), Some(Status.PendingCancellation)) => "client_cancels_active_mandate"
        case (Status.Cancelled, Some("client"), _) => "agent_removes_mandate"
      }
    }
    def serviceString: String = {
      service.toUpperCase match {
        case "ATED" => "Annual Tax on Enveloped Dwellings"
        case _ => "[Service Name]"
      }
    }
    emailConnector.sendTemplatedEmail(emailString, template, serviceString)
  }

}

object NotificationEmailService extends NotificationEmailService {
  // $COVERAGE-OFF$
  val emailConnector = EmailConnector
  // $COVERAGE-ON$
}

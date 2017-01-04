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

package uk.gov.hmrc.agentclientmandate.services

import uk.gov.hmrc.agentclientmandate.connectors.{EmailConnector, EmailNotSent, EmailStatus}
import uk.gov.hmrc.agentclientmandate.models.{Mandate, Status}
import uk.gov.hmrc.agentclientmandate.models.Status.Status
import uk.gov.hmrc.agentclientmandate.repositories.{MandateFetchStatus, MandateFetched, MandateNotFound}
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future


trait NotificationEmailService {

  def mandateFetchService: MandateFetchService

  def emailConnector: EmailConnector

  def sendMail(emailString: String, action: Status, userType: Option[String] = None, service: String)(implicit hc: HeaderCarrier): Future[EmailStatus] = {
    def getTemplate: String = {
      (action, userType) match {
        case (Status.Approved, _) => "client_approves_mandate"
        case (Status.Active, _) => "agent_activates_mandate"
        case (Status.Rejected, _) => "agent_rejects_mandate"
        case (Status.Cancelled, Some("agent")) => "agent_removes_mandate"
        case (Status.Cancelled, Some("client")) => "client_removes_mandate"
      }
    }
    def createServiceString: String = {
      service.toUpperCase match {
        case "ATED" => "Annual Tax Enveloped Dwelling"
        case _ => "[Service Name]"
      }
    }
    emailConnector.sendTemplatedEmail(emailString, getTemplate, createServiceString)
  }

}

object NotificationEmailService extends NotificationEmailService {
  // $COVERAGE-OFF$
  val emailConnector = EmailConnector
  val mandateFetchService = MandateFetchService
  // $COVERAGE-ON$
}

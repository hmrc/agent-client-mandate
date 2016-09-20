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

import uk.gov.hmrc.agentclientmandate.connectors.{EmailStatus, EmailNotSent, EmailConnector}
import uk.gov.hmrc.agentclientmandate.models.Mandate
import uk.gov.hmrc.play.http.HeaderCarrier
import uk.gov.hmrc.agentclientmandate.repositories.{MandateFetchStatus, MandateFetched, MandateNotFound}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future


trait NotificationEmailService {

  def mandateFetchService: MandateFetchService

  def emailConnector: EmailConnector

  def sendMail(mandateId: String, userType: String)(implicit hc: HeaderCarrier): Future[EmailStatus] = {
    def emailAddress(userType: String, mandate: Mandate): String = {
      if (userType == "client") mandate.clientParty.map(_.contactDetails.email).getOrElse("")
      else mandate.agentParty.contactDetails.email
    }

    fetchMandateDetails(mandateId) flatMap {
      case MandateFetched(clientMandate) =>
        emailConnector.sendTemplatedEmail(emailAddress(userType, clientMandate))
      case MandateNotFound => Future.successful(EmailNotSent)
    }
  }

  private def fetchMandateDetails(mandateId: String): Future[MandateFetchStatus] = {
    mandateFetchService.fetchClientMandate(mandateId)
  }

}

object NotificationEmailService extends NotificationEmailService {
  val emailConnector = EmailConnector
  val mandateFetchService = MandateFetchService
}

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

import play.api.http.Status._
import uk.gov.hmrc.agentclientmandate.connectors.EmailConnector
import uk.gov.hmrc.play.http.{HeaderCarrier, HttpResponse}
import uk.gov.hmrc.agentclientmandate.repositories.{ClientMandateFetchStatus, ClientMandateFetched, ClientMandateNotFound}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future


  trait NotificationEmailService {

    def clientMandateFetchService: ClientMandateFetchService

    def emailConnector: EmailConnector

    def sendMail(mandateId: String, userType: String)(implicit hc: HeaderCarrier): Future[HttpResponse] = {
      fetchMandateDetails(mandateId) flatMap {
        case ClientMandateFetched(clientMandate) =>
          val email = clientMandate.party.contactDetails.email
          emailConnector.sendTemplatedEmail(email)
        case ClientMandateNotFound => Future.successful(HttpResponse(NOT_FOUND, None))
      }
    }

    private def fetchMandateDetails(mandateId: String): Future[ClientMandateFetchStatus] = {
      clientMandateFetchService.fetchClientMandate(mandateId)
    }

  }

  object NotificationEmailService extends NotificationEmailService {
    val emailConnector = EmailConnector
    val clientMandateFetchService = ClientMandateFetchService
  }

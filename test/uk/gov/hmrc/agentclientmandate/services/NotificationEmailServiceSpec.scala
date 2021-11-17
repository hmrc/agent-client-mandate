/*
 * Copyright 2021 HM Revenue & Customs
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

import org.joda.time.DateTime
import org.mockito.ArgumentMatchers._
import org.mockito.{ArgumentMatchers, MockitoSugar}
import org.scalatest.BeforeAndAfterEach
import org.scalatestplus.play.PlaySpec
import play.api.libs.json.{JsValue, Json}
import play.api.test.Helpers._
import uk.gov.hmrc.agentclientmandate.connectors.{EmailConnector, EmailSent, EmailStatus}
import uk.gov.hmrc.agentclientmandate.models._
import uk.gov.hmrc.agentclientmandate.utils.Generators._
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.Future

class NotificationEmailServiceSpec extends PlaySpec with MockitoSugar with BeforeAndAfterEach {

  trait Setup {
    class TestNotificationEmailService extends NotificationEmailService {
      override val emailConnector: EmailConnector = mockEmailConnector
    }

    val service = new TestNotificationEmailService
  }

  "NotificationEmailService" should {

     "send the correct email/emails to the correct recipients" when {

      "client approves mandate" in new Setup {
        val email = "agent_email@email.com"
        when(mockEmailConnector.sendTemplatedEmail(ArgumentMatchers.eq(email), any(), any(), any(), any())(any())) thenReturn Future.successful(EmailSent)
        val response: Future[EmailStatus] = service.sendMail(
          email, Status.Approved, Some("client"), Some("agent"), "Agent name", service = "ATED", Some(Status.New))
        await(response) must be(EmailSent)
        verify(mockEmailConnector).sendTemplatedEmail(email, "client_approves_mandate", "Annual Tax on Enveloped Dwellings", None, "Agent name")
      }

      "agent activates mandate" in new Setup {
        val email = "client_email@email.com"
        when(mockEmailConnector.sendTemplatedEmail(ArgumentMatchers.eq(email), any(), any(), any(), any())(any())) thenReturn Future.successful(EmailSent)
        val response: Future[EmailStatus] = service.sendMail(email, Status.Active, Some("agent"), Some("client"), "Client name", "ATED", Some(Status.Approved))
        await(response) must be(EmailSent)
        verify(mockEmailConnector).sendTemplatedEmail(email, "agent_activates_mandate", "Annual Tax on Enveloped Dwellings", None, "Client name")
      }

       "agent self-auth non-uk mandate" in new Setup {
         val email = "agent_email@email.com"
         when(mockEmailConnector.sendTemplatedEmail(ArgumentMatchers.eq(email), any(), any(), any(), any())(any())) thenReturn Future.successful(EmailSent)
         val response: Future[EmailStatus] = service.sendMail(email, Status.Active, Some("agent"), Some("agent"), "Agent name", "ATED", None)
         await(response) must be(EmailSent)
         verify(mockEmailConnector).sendTemplatedEmail(email, "agent_self_auth_activates_mandate", "Annual Tax on Enveloped Dwellings", None, "Agent name")
       }

       "agent rejects mandate" in new Setup {
         val email = "client_email@email.com"
        when(mockEmailConnector.sendTemplatedEmail(ArgumentMatchers.eq(email), any(), any(), any(), any())(any())) thenReturn Future.successful(EmailSent)
        val response: Future[EmailStatus] = service.sendMail(email, Status.Rejected, Some("agent"), Some("client"),"Client name","ATED", Some(Status.Approved))
        await(response) must be(EmailSent)
        verify(mockEmailConnector).sendTemplatedEmail(email, "agent_rejects_mandate", "Annual Tax on Enveloped Dwellings", None, "Client name")
      }

      "agent removes an active mandate" in new Setup {
        val clientEmail = "client_email@email.com"
        val agentEmail = "agent_email@email.com"

        when(mockEmailConnector.sendTemplatedEmail(ArgumentMatchers.eq(clientEmail), any(), any(), any(), any())(any())) thenReturn Future.successful(EmailSent)
        when(mockEmailConnector.sendTemplatedEmail(ArgumentMatchers.eq(agentEmail), any(), any(), any(), any())(any())) thenReturn Future.successful(EmailSent)

        val responseToAgent: Future[EmailStatus] = service.sendMail(
          agentEmail, Status.Cancelled, Some("agent"), Some("agent"),"Agent name","ATED", Some(Status.Active))
        val responseToClient: Future[EmailStatus] = service.sendMail(
          clientEmail,
          Status.Cancelled,
          Some("agent"),
          Some("client"),"Client name","ATED",
          uniqueAuthNo = Some("UNIQUEREF123"),
          prevStatus = Some(Status.Active))

        await(responseToAgent) must be(EmailSent)
        await(responseToClient) must be(EmailSent)

        verify(mockEmailConnector).sendTemplatedEmail(
          agentEmail, "agent_self_auth_deactivates_mandate", "Annual Tax on Enveloped Dwellings", None, "Agent name")
        verify(mockEmailConnector).sendTemplatedEmail(clientEmail, "agent_removes_mandate",
          "Annual Tax on Enveloped Dwellings", uniqueAuthNo = Some("UNIQUEREF123"), "Client name")
      }

      "client cancels approved mandate" in new Setup {
        val email = "client@client_email.com"
        when(mockEmailConnector.sendTemplatedEmail(ArgumentMatchers.eq(email), any(), any(), any(), any())(any())) thenReturn Future.successful(EmailSent)
        val response: Future[EmailStatus] = service.sendMail(
          email, Status.Cancelled, Some("client"), Some("agent"), "Agent name","ATED", Some(Status.Approved), None)
        await(response) must be(EmailSent)
        verify(mockEmailConnector).sendTemplatedEmail(email, "client_removes_mandate", "Annual Tax on Enveloped Dwellings", None, "Agent name")
      }

      "client cancels active mandate" in new Setup {
        val email = "client_email@email.com"
        when(mockEmailConnector.sendTemplatedEmail(ArgumentMatchers.eq(email), any(), any(), any(), any())(any())) thenReturn Future.successful(EmailSent)
        val response: Future[EmailStatus] = service.sendMail(
          email, Status.Cancelled, Some("client"), Some("agent"),"Agent name","ATED", Some(Status.Active), None)
        await(response) must be(EmailSent)
        verify(mockEmailConnector).sendTemplatedEmail(email, "client_cancels_active_mandate", "Annual Tax on Enveloped Dwellings", None, "Agent name")
      }
    }

    "send email to default" when {
      "service name not found" in new Setup {
        val email = "some_email@email.com"
        when(mockEmailConnector.sendTemplatedEmail(ArgumentMatchers.eq(email), any(), any(), any(), any())(any())) thenReturn Future.successful(EmailSent)
        val response: Future[EmailStatus] = service.sendMail(email, Status.Active, None, None, "", service = "aaaa", None)
        await(response) must be(EmailSent)
      }
    }

  }

  implicit val hc: HeaderCarrier = HeaderCarrier()

  val validResponse: JsValue = Json.parse( """{"valid":"true"}""")
  val invalidResponse: JsValue = Json.parse( """{"valid":"false"}""")

  val clientMandate: Mandate =
    Mandate(
      id = "123",
      createdBy = User("credid",nameGen.sample.get , None),
      agentParty = Party(partyIDGen.sample.get, nameGen.sample.get, PartyType.Organisation, ContactDetails(emailGen.sample.get, telephoneNumberGen.sample)),
      clientParty = Some(Party(
        partyIDGen.sample.get, nameGen.sample.get, PartyType.Organisation, ContactDetails(emailGen.sample.get, telephoneNumberGen.sample))),
      currentStatus = MandateStatus(Status.New, new DateTime(), "credid"),
      statusHistory = Nil,
      subscription = Subscription(subscriptionReferenceGen.sample, Service("ated", "ATED")),
      clientDisplayName = "client display name"
    )

  val invalidMandateId = "123456"
  val validMandateId = "123"

  val invalidEmail = "aa bb cc"

  val mockEmailConnector: EmailConnector = mock[EmailConnector]

  override def beforeEach(): Unit = {
    reset(mockEmailConnector)
  }

}

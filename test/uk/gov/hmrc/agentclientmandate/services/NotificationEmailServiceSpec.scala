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

package uk.gov.hmrc.agentclientmandate.services

import org.joda.time.DateTime
import org.mockito.ArgumentMatchers
import org.mockito.ArgumentMatchers._
import org.mockito.Matchers
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterEach
import org.scalatest.mockito.MockitoSugar
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import org.scalatestplus.play.{OneServerPerSuite, PlaySpec}
import play.api.libs.json.Json
import play.api.test.Helpers._
import uk.gov.hmrc.agentclientmandate.connectors.{EmailConnector, EmailNotSent, EmailSent}
import uk.gov.hmrc.agentclientmandate.models._
import uk.gov.hmrc.agentclientmandate.repositories.{MandateFetched, MandateNotFound}

import scala.concurrent.Future
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.agentclientmandate.utils.Generators._

class NotificationEmailServiceSpec extends PlaySpec with GuiceOneServerPerSuite with MockitoSugar with BeforeAndAfterEach {

  trait Setup {
    class TestNotificationEmailService extends NotificationEmailService {
      override val emailConnector = mockEmailConnector
    }

    val service = new TestNotificationEmailService
  }

  "NotificationEmailService" should {

     "send email" when {

      "client approves mandate" in new Setup {
        val email = emailGen.sample.get
        when(mockEmailConnector.sendTemplatedEmail(ArgumentMatchers.eq(email), any(), any())(any())) thenReturn Future.successful(EmailSent)
        val response = service.sendMail(email, Status.Approved, None, "ATED")
        await(response) must be(EmailSent)
        verify(mockEmailConnector).sendTemplatedEmail(email, "client_approves_mandate", "Annual Tax on Enveloped Dwellings")
      }

      "agent activates mandate" in new Setup {
        val email = emailGen.sample.get
        when(mockEmailConnector.sendTemplatedEmail(ArgumentMatchers.eq(email), any(), any())(any())) thenReturn Future.successful(EmailSent)
        val response = service.sendMail(email, Status.Active, None, "ATED")
        await(response) must be(EmailSent)
        verify(mockEmailConnector).sendTemplatedEmail(email, "agent_activates_mandate", "Annual Tax on Enveloped Dwellings")
      }

       "agent self-auth non-uk mandate" in new Setup {
         val email = emailGen.sample.get
         when(mockEmailConnector.sendTemplatedEmail(ArgumentMatchers.eq(email), any(), any())(any())) thenReturn Future.successful(EmailSent)
         val response = service.sendMail(email, Status.Active, Some("agent"), "ATED")
         await(response) must be(EmailSent)
         verify(mockEmailConnector).sendTemplatedEmail(email, "agent_self_auth_activates_mandate", "Annual Tax on Enveloped Dwellings")
       }

       "agent rejects mandate" in new Setup {
         val email = emailGen.sample.get
        when(mockEmailConnector.sendTemplatedEmail(ArgumentMatchers.eq(email), any(), any())(any())) thenReturn Future.successful(EmailSent)
        val response = service.sendMail(email, Status.Rejected, None, "ATED")
        await(response) must be(EmailSent)
        verify(mockEmailConnector).sendTemplatedEmail(email, "agent_rejects_mandate", "Annual Tax on Enveloped Dwellings")
      }

      "agent cancels active mandate" in new Setup {
        val email = emailGen.sample.get
        when(mockEmailConnector.sendTemplatedEmail(ArgumentMatchers.eq(email), any(), any())(any())) thenReturn Future.successful(EmailSent)
        val response = service.sendMail(email, Status.Cancelled, Some("client"), "ATED")
        await(response) must be(EmailSent)
        verify(mockEmailConnector).sendTemplatedEmail(email, "agent_removes_mandate", "Annual Tax on Enveloped Dwellings")
      }

       "agent cancels self-auth non-uk mandate" in new Setup {
         val email = emailGen.sample.get
         when(mockEmailConnector.sendTemplatedEmail(ArgumentMatchers.eq(email), any(), any())(any())) thenReturn Future.successful(EmailSent)
         val response = service.sendMail(email, Status.Cancelled, Some("agent"), "ATED")
         await(response) must be(EmailSent)
         verify(mockEmailConnector).sendTemplatedEmail(email, "agent_self_auth_deactivates_mandate", "Annual Tax on Enveloped Dwellings")
       }

      "client cancels approved mandate" in new Setup {
        val email = emailGen.sample.get
        when(mockEmailConnector.sendTemplatedEmail(ArgumentMatchers.eq(email), any(), any())(any())) thenReturn Future.successful(EmailSent)
        val response = service.sendMail(email, Status.Cancelled, Some("client"), "ATED", Some(Status.Approved))
        await(response) must be(EmailSent)
        verify(mockEmailConnector).sendTemplatedEmail(email, "client_removes_mandate", "Annual Tax on Enveloped Dwellings")
      }

      "client cancels active mandate" in new Setup {
        val email = emailGen.sample.get
        when(mockEmailConnector.sendTemplatedEmail(ArgumentMatchers.eq(email), any(), any())(any())) thenReturn Future.successful(EmailSent)
        val response = service.sendMail(email, Status.Cancelled, Some("client"), "ATED", Some(Status.PendingCancellation))
        await(response) must be(EmailSent)
        verify(mockEmailConnector).sendTemplatedEmail(email, "client_cancels_active_mandate", "Annual Tax on Enveloped Dwellings")
      }
    }

    "send email to default" when {
      "service name cant be found" in new Setup {
        val email = emailGen.sample.get
        when(mockEmailConnector.sendTemplatedEmail(ArgumentMatchers.eq(email), any(), any())(any())) thenReturn Future.successful(EmailSent)
        val response = service.sendMail(email, Status.Active, service = "aaaa")
        await(response) must be(EmailSent)
      }
    }

  }

  implicit val hc: HeaderCarrier = HeaderCarrier()

  val validResponse = Json.parse( """{"valid":"true"}""")
  val invalidResponse = Json.parse( """{"valid":"false"}""")

  val clientMandate =
    Mandate(
      id = "123",
      createdBy = User("credid",nameGen.sample.get , None),
      agentParty = Party(partyIDGen.sample.get, nameGen.sample.get, PartyType.Organisation, ContactDetails(emailGen.sample.get, telephoneNumberGen.sample)),
      clientParty = Some(Party(partyIDGen.sample.get, nameGen.sample.get, PartyType.Organisation, ContactDetails(emailGen.sample.get, telephoneNumberGen.sample))),
      currentStatus = MandateStatus(Status.New, new DateTime(), "credid"),
      statusHistory = Nil,
      subscription = Subscription(subscriptionReferenceGen.sample, Service("ated", "ATED")),
      clientDisplayName = "client display name"
    )

  val invalidMandateId = "123456"
  val validMandateId = "123"

  val invalidEmail = "aa bb cc"

  val mockEmailConnector = mock[EmailConnector]

  override def beforeEach(): Unit = {
    reset(mockEmailConnector)
  }

}

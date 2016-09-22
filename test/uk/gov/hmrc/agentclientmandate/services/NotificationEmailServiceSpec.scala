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

import org.joda.time.DateTime
import org.mockito.Matchers
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterEach
import org.scalatest.mock.MockitoSugar
import org.scalatestplus.play.{OneServerPerSuite, PlaySpec}
import play.api.libs.json.Json
import play.api.test.Helpers._
import uk.gov.hmrc.agentclientmandate.connectors.{EmailSent, EmailNotSent, EmailConnector}
import uk.gov.hmrc.agentclientmandate.models._
import uk.gov.hmrc.agentclientmandate.repositories.{MandateFetched, MandateNotFound}
import uk.gov.hmrc.play.http.{HeaderCarrier, HttpResponse}

import scala.concurrent.Future

class NotificationEmailServiceSpec extends PlaySpec with OneServerPerSuite with MockitoSugar with BeforeAndAfterEach {

  "NotificationEmailService" should {

    "use the correct connector" in {
      NotificationEmailService.emailConnector must be(EmailConnector)
    }

    "return a not found" when {

      "no matching mandate is found" in {

        when(mockMandateFetchService.fetchClientMandate(Matchers.any())) thenReturn Future.successful(MandateNotFound)

        val response = TestNotificationEmailService.sendMail(invalidMandateId, "client")
        await(response) must be(EmailNotSent)

      }

    }

    "return 202" when {

      "matching mandateId is found and email is sent successfully to agent" in {

        when(mockMandateFetchService.fetchClientMandate(Matchers.eq(validMandateId))) thenReturn Future.successful(MandateFetched(clientMandate))
        when(mockEmailConnector.sendTemplatedEmail(Matchers.eq("test@test.com"))(Matchers.any())) thenReturn Future.successful(EmailSent)

        val response = TestNotificationEmailService.sendMail(validMandateId, "agent")
        await(response) must be(EmailSent)

      }

      "matching mandateId is found and email is sent successfully to client" in {

        when(mockMandateFetchService.fetchClientMandate(Matchers.any())) thenReturn Future.successful(MandateFetched(clientMandate))
        when(mockEmailConnector.sendTemplatedEmail(Matchers.any())(Matchers.any())) thenReturn Future.successful(EmailSent)

        val response = TestNotificationEmailService.sendMail(validMandateId, "client")
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
      createdBy = User("credid", "name", None),
      agentParty = Party("JARN123456", "Joe Bloggs", PartyType.Organisation, ContactDetails("test@test.com", Some("0123456789"))),
      clientParty = Some(Party("XVAT00000123456", "Jon Snow", PartyType.Organisation, ContactDetails("client@test.com", Some("0123456789")))),
      currentStatus = MandateStatus(Status.New, new DateTime(), "credid"),
      statusHistory = None,
      subscription = Subscription(Some("XVAT00000123456"), Service("ated", "ATED"))
    )

  val invalidMandateId = "123456"
  val validMandateId = "123"

  val invalidEmail = "aa bb cc"

  val mockMandateFetchService = mock[MandateFetchService]
  val mockEmailConnector = mock[EmailConnector]

  object TestNotificationEmailService extends NotificationEmailService {
    override val mandateFetchService = mockMandateFetchService
    override val emailConnector = mockEmailConnector
  }

  override def beforeEach(): Unit = {
    reset(mockMandateFetchService)
    reset(mockEmailConnector)
  }

}

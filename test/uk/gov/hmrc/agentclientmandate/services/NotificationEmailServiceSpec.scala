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
import uk.gov.hmrc.agentclientmandate.connectors.EmailConnector
import uk.gov.hmrc.agentclientmandate.models._
import uk.gov.hmrc.agentclientmandate.repositories.{ClientMandateFetched, ClientMandateNotFound, ClientMandateRepository}
import uk.gov.hmrc.play.http.{HeaderCarrier, HttpResponse}

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

class NotificationEmailServiceSpec extends PlaySpec with OneServerPerSuite with MockitoSugar with BeforeAndAfterEach {

  def await[A](future: Future[A]) = Await.result(future, 5 seconds)

  "NotificationEmailService" should {

    "use the correct connector" in {
      NotificationEmailService.emailConnector must be(EmailConnector)
    }

    "return a not found" when {

      "no matching mandate is found" in {

        when(mockClientMandateFetchService.fetchClientMandate(Matchers.any())) thenReturn Future.successful(ClientMandateNotFound)

        val response = TestNotificationEmailService.sendMail(invalidMandateId, "client")
        await(response).status must be(NOT_FOUND)

      }

    }

    "return 202" when {

      "matching mandateId is found and email is sent succesfully" in {

        when(mockClientMandateFetchService.fetchClientMandate(Matchers.eq(validMandateId))) thenReturn Future.successful(ClientMandateFetched(clientMandate))
        when(mockEmailConnector.sendTemplatedEmail(Matchers.eq("test@test.com"))(Matchers.any())) thenReturn Future.successful(HttpResponse(ACCEPTED, None))

        val response = TestNotificationEmailService.sendMail(validMandateId, "agent")
        await(response).status must be(ACCEPTED)

      }
    }

  }

  implicit val hc: HeaderCarrier = HeaderCarrier()

  val validResponse = Json.parse( """{"valid":"true"}""")
  val invalidResponse = Json.parse( """{"valid":"false"}""")


  val clientMandate =
    ClientMandate(
      id = "123",
      createdBy = "credid",
      party = Party("JARN123456", "Joe Bloggs", "Organisation", ContactDetails("test@test.com", "0123456789")),
      currentStatus = MandateStatus(Status.Pending, new DateTime(), "credid"),
      statusHistory = None,
      service = Service(None, "ATED")
    )

  val invalidMandateId = "123456"
  val validMandateId = "123"

  val invalidEmail = "aa bb cc"

  val mockClientMandateFetchService = mock[ClientMandateFetchService]
  val mockEmailConnector = mock[EmailConnector]

  object TestNotificationEmailService extends NotificationEmailService {
    override val clientMandateFetchService = mockClientMandateFetchService
    override val emailConnector = mockEmailConnector
  }

  override def beforeEach(): Unit = {
    reset(mockClientMandateFetchService)
  }

}

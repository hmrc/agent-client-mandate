/*
 * Copyright 2019 HM Revenue & Customs
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
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterEach
import org.scalatest.mockito.MockitoSugar
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.libs.json.Json
import play.api.test.Helpers._
import uk.gov.hmrc.agentclientmandate.connectors.AuthorityConnector
import uk.gov.hmrc.agentclientmandate.models._
import uk.gov.hmrc.agentclientmandate.repositories.{MandateFetched, MandateRepository}
import uk.gov.hmrc.agentclientmandate.utils.Generators._
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.Future

class MandateFetchServiceSpec extends PlaySpec with GuiceOneServerPerSuite with MockitoSugar with BeforeAndAfterEach {

  val mandateId = "123"

  implicit val hc = HeaderCarrier()

  val mockMandateRepository = mock[MandateRepository]
  val mockAuthConnector = mock[AuthorityConnector]

  trait Setup {

    class TestFetchMandateService extends MandateFetchService {
      override val mandateRepository = mockMandateRepository
      override val authConnector = mockAuthConnector
      override val clientCancelledMandateNotification: Int = 5
    }

    val service = new TestFetchMandateService
  }

  override def beforeEach(): Unit = {
    reset(mockMandateRepository)
    reset(mockAuthConnector)
  }

  "FetchClientMandateService" should {

    "return a success response" when {

      "a client mandate is found for a valid mandate id in MongoDB" in new Setup {

        when(mockMandateRepository.fetchMandate(any())) thenReturn Future.successful(MandateFetched(clientMandate))

        val response = service.fetchClientMandate(mandateId)
        await(response) must be(MandateFetched(clientMandate))

      }

    }

    "list of client mandate is found for a valid arn and service name in MongoDB" in new Setup {

      when(mockMandateRepository.getAllMandatesByServiceName(any(), any(), any(), any(), any())) thenReturn Future.successful(List(clientMandate))

      val response = service.getAllMandates(agentReferenceNumberGen.sample.get, "ATED", None, None)
      await(response) must be(List(clientMandate))

    }

    "list of client mandate is found for a valid arn and service name in MongoDB and filtering is applied" in new Setup {

      val successResponseJsonAuth = Json.parse(
        s"""{
               "credentials": {
                 "gatewayId": "cred-id-113244018119",
                 "idaPids": []
               },
               "accounts": {
                 "agent": {
                   "agentCode":"${agentCodeGen.sample.get}", "agentBusinessUtr":"${agentBusinessUtrGen.sample.get}"
                 }
               }
             }""")

      when(mockAuthConnector.getAuthority()(any())) thenReturn {
        Future.successful(successResponseJsonAuth)
      }
      val agentRefNumber = agentReferenceNumberGen.sample.get
      when(mockMandateRepository.getAllMandatesByServiceName(any(), any(), any(), any(), any())) thenReturn Future.successful(List(clientMandate))

      val response = service.getAllMandates(agentRefNumber, "ATED", Some("credId"), None)
      await(response) must be(List(clientMandate))
    }

    "a mandate is found for a valid client id and service" in new Setup {

      when(mockMandateRepository.fetchMandateByClient(any(), any())) thenReturn Future.successful(MandateFetched(clientMandate))

      val response = service.fetchClientMandate("clientId", "service")
      await(response) must be(MandateFetched(clientMandate))

    }

    "a list of mandates is found for an agent id" in new Setup {
      when(mockMandateRepository.findMandatesMissingAgentEmail(any(), any())) thenReturn Future.successful(List(clientMandate.id))

      val response = service.getMandatesMissingAgentsEmails("agentId", "ated")
      await(response) must be(List(clientMandate.id))
    }

    "a list of client display names" in new Setup {
      when(mockMandateRepository.getClientCancelledMandates(any(), any(), any())) thenReturn Future.successful(List("AAA", "BBB"))
      val response = service.fetchClientCancelledMandates("arn", "service")
      await(response) must be(List("AAA", "BBB"))
    }

  }

  val clientMandate =
    Mandate(
      id = "123",
      createdBy = User("credid", nameGen.sample.get, None),
      agentParty = Party(partyIDGen.sample.get, nameGen.sample.get, PartyType.Organisation, ContactDetails(emailGen.sample.get, telephoneNumberGen.sample)),
      clientParty = None,
      currentStatus = MandateStatus(Status.New, new DateTime(), "credid"),
      statusHistory = Nil,
      subscription = Subscription(None, Service("ated", "ATED")),
      clientDisplayName = "client display name"
    )
}

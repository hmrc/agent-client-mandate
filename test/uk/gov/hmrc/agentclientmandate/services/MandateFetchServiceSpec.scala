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

import org.joda.time.DateTime
import org.mockito.Matchers
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterEach
import org.scalatest.mock.MockitoSugar
import org.scalatestplus.play.{OneServerPerSuite, PlaySpec}
import play.api.test.Helpers._
import uk.gov.hmrc.agentclientmandate.models._
import uk.gov.hmrc.agentclientmandate.repositories.{MandateFetched, MandateRepository}

import scala.concurrent.Future

class MandateFetchServiceSpec extends PlaySpec with OneServerPerSuite with MockitoSugar with BeforeAndAfterEach {

  val mandateId = "123"

  "FetchClientMandateService" should {

    "return a success response" when {

      "a client mandate is found for a valid mandate id in MongoDB" in {

        when(mockMandateRepository.fetchMandate(Matchers.any())) thenReturn Future.successful(MandateFetched(clientMandate))

        val response = TestFetchMandateService.fetchClientMandate(mandateId)
        await(response) must be(MandateFetched(clientMandate))

      }

    }

    "list of client mandate is found for a valid arn and service name in MongoDB" in {

      when(mockMandateRepository.getAllMandatesByServiceName(Matchers.any(), Matchers.any())) thenReturn Future.successful(List(clientMandate))

      val response = TestFetchMandateService.getAllMandates("JARN123456", "ATED")
      await(response) must be(List(clientMandate))

    }

    "a mandate is found for a valid client id and service" in {

      when(mockMandateRepository.fetchMandateByClient(Matchers.any(), Matchers.any())) thenReturn Future.successful(MandateFetched(clientMandate))

      val response = TestFetchMandateService.fetchClientMandate("clientId", "service")
      await(response) must be(MandateFetched(clientMandate))

    }

    "a list of mandates is found for an agent id" in {
      when(mockMandateRepository.findMandatesMissingAgentEmail(Matchers.any(), Matchers.any())) thenReturn Future.successful(List(clientMandate.id))

      val response = TestFetchMandateService.getMandatesMissingAgentsEmails("agentId", "ated")
      await(response) must be(List(clientMandate.id))
    }

  }

  val clientMandate =
    Mandate(
      id = "123",
      createdBy = User("credid", "name", None),
      agentParty = Party("JARN123456", "Joe Bloggs", PartyType.Organisation, ContactDetails("test@test.com", Some("0123456789"))),
      clientParty = None,
      currentStatus = MandateStatus(Status.New, new DateTime(), "credid"),
      statusHistory = Nil,
      subscription = Subscription(None, Service("ated", "ATED")),
      clientDisplayName = "client display name"
    )

  val mockMandateRepository = mock[MandateRepository]

  object TestFetchMandateService extends MandateFetchService {
    override val mandateRepository = mockMandateRepository
  }

  override def beforeEach(): Unit = {
    reset(mockMandateRepository)
  }

}

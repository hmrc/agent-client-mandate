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

package uk.gov.hmrc.agentclientmandate.controllers

import org.joda.time.DateTime
import org.mockito.Matchers
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterEach
import org.scalatest.mock.MockitoSugar
import org.scalatestplus.play.{OneAppPerSuite, PlaySpec}
import play.api.test.Helpers._
import play.api.test.{FakeApplication, FakeRequest}
import uk.gov.hmrc.agentclientmandate.models._
import uk.gov.hmrc.agentclientmandate.repositories.{MandateFetched, MandateNotFound}
import uk.gov.hmrc.agentclientmandate.services.{AllocateAgentService, MandateFetchService}
import uk.gov.hmrc.play.http.HttpResponse

import scala.concurrent.Future


class MandateControllerSpec extends PlaySpec with OneAppPerSuite with MockitoSugar with BeforeAndAfterEach {

  val fetchServiceMock = mock[MandateFetchService]
  val allocateAgentServiceMock = mock[AllocateAgentService]

  object TestMandateController extends MandateController {
    override val fetchService = fetchServiceMock
    override val allocateAgentService = allocateAgentServiceMock
  }

  override def beforeEach(): Unit = {
    reset(fetchServiceMock)
    reset(allocateAgentServiceMock)
  }

  implicit override lazy val app: FakeApplication = FakeApplication(
    additionalConfiguration = Map("auditing.enabled" -> "false")
  )

  val mandateId = "123"
  val agentCode = "ABC"

  val clientMandate =
    Mandate(
      id = "123",
      createdBy = User("credid",None),
      agentParty = Party("JARN123456", "Joe Bloggs", "Organisation", ContactDetails("test@test.com", "0123456789")),
      clientParty = None,
      currentStatus = MandateStatus(Status.Pending, new DateTime(), "credid"),
      statusHistory = None,
      subscription = Subscription(None, Service("ated", "ATED"))
    )

  "MandateController" should {

    "when client mandate found try to allocate the agent" when {
      "request is valid" in {
        when(fetchServiceMock.fetchClientMandate(Matchers.eq(mandateId))) thenReturn Future.successful(MandateFetched(clientMandate))
        when(allocateAgentServiceMock.allocateAgent(Matchers.any(), Matchers.any())(Matchers.any())) thenReturn Future.successful(HttpResponse(200, None))

        val response = TestMandateController.allocate(agentCode, mandateId).apply(FakeRequest())

        status(response) must be(OK)
      }

      "bad request" in {
        when(fetchServiceMock.fetchClientMandate(Matchers.eq(mandateId))) thenReturn Future.successful(MandateFetched(clientMandate))
        when(allocateAgentServiceMock.allocateAgent(Matchers.any(), Matchers.any())(Matchers.any())) thenReturn Future.successful(HttpResponse(400, None))

        val response = TestMandateController.allocate(agentCode, mandateId).apply(FakeRequest())

        status(response) must be(BAD_REQUEST)
      }

      "server error" in {
        when(fetchServiceMock.fetchClientMandate(Matchers.eq(mandateId))) thenReturn Future.successful(MandateFetched(clientMandate))
        when(allocateAgentServiceMock.allocateAgent(Matchers.any(), Matchers.any())(Matchers.any())) thenReturn Future.successful(HttpResponse(500, None))

        val response = TestMandateController.allocate(agentCode, mandateId).apply(FakeRequest())

        status(response) must be(INTERNAL_SERVER_ERROR)
      }
    }

    "when client mandate not found return not found" when {
      "client mandate not found" in {

        when(fetchServiceMock.fetchClientMandate(Matchers.eq(mandateId))) thenReturn Future.successful(MandateNotFound)

        val response = TestMandateController.allocate(agentCode, mandateId).apply(FakeRequest())
        status(response) must be(NOT_FOUND)
      }
    }

  }
}

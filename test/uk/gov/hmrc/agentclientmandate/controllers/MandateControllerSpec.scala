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
import org.scalatestplus.play.{OneServerPerSuite, PlaySpec}
import play.api.libs.json.Json
import play.api.test.Helpers._
import play.api.test.{FakeApplication, FakeHeaders, FakeRequest}
import uk.gov.hmrc.agentclientmandate.models._
import uk.gov.hmrc.agentclientmandate.repositories.{MandateFetched, MandateNotFound, MandateUpdateError, MandateUpdated}
import uk.gov.hmrc.agentclientmandate.services.{AllocateAgentService, MandateCreateService, MandateFetchService, MandateUpdateService}
import uk.gov.hmrc.play.http.HttpResponse

import scala.concurrent.Future


class MandateControllerSpec extends PlaySpec with OneServerPerSuite with MockitoSugar with BeforeAndAfterEach {

  "MandateController" should {

    "when client mandate found try to allocate the agent" when {
      "request is valid" in {
        when(fetchServiceMock.fetchClientMandate(Matchers.eq(mandateId))) thenReturn Future.successful(MandateFetched(mandate))
        when(allocateAgentServiceMock.allocateAgent(Matchers.any(), Matchers.any())(Matchers.any())) thenReturn Future.successful(HttpResponse(200, None))

        val result = TestMandateController.activate(agentCode, mandateId).apply(FakeRequest())

        status(result) must be(OK)
      }

      "bad request" in {
        when(fetchServiceMock.fetchClientMandate(Matchers.eq(mandateId))) thenReturn Future.successful(MandateFetched(mandate))
        when(allocateAgentServiceMock.allocateAgent(Matchers.any(), Matchers.any())(Matchers.any())) thenReturn Future.successful(HttpResponse(400, None))

        val result = TestMandateController.activate(agentCode, mandateId).apply(FakeRequest())

        status(result) must be(BAD_REQUEST)
      }

      "server error" in {
        when(fetchServiceMock.fetchClientMandate(Matchers.eq(mandateId))) thenReturn Future.successful(MandateFetched(mandate))
        when(allocateAgentServiceMock.allocateAgent(Matchers.any(), Matchers.any())(Matchers.any())) thenReturn Future.successful(HttpResponse(500, None))

        val result = TestMandateController.activate(agentCode, mandateId).apply(FakeRequest())

        status(result) must be(INTERNAL_SERVER_ERROR)
      }
    }

    "when client mandate not found return not found" when {
      "client mandate not found" in {

        when(fetchServiceMock.fetchClientMandate(Matchers.eq(mandateId))) thenReturn Future.successful(MandateNotFound)

        val result = TestMandateController.activate(agentCode, mandateId).apply(FakeRequest())
        status(result) must be(NOT_FOUND)
      }
    }

    "create a mandate and return mandate Id" when {
      "an agent request it and passes valid DTO" in {
        when(createServiceMock.createMandate(Matchers.eq(agentCode), Matchers.eq(createMandateDto))(Matchers.any())).thenReturn(Future.successful(mandateId))
        val fakeRequest = FakeRequest(method = "POST", uri = "", headers = FakeHeaders(Seq("Content-type" -> Seq("application/json"))), body = Json.toJson(createMandateDto))
        val result = TestMandateController.create(agentCode).apply(fakeRequest)
        status(result) must be(CREATED)
        contentAsJson(result) must be(Json.parse("""{"mandateId":"123"}"""))
      }

    }

    "return bad-request" when {
      "invalid dto is passed by agent" in {
        val fakeRequest = FakeRequest(method = "POST", uri = "", headers = FakeHeaders(Seq("Content-type" -> Seq("application/json"))), body = Json.parse("""{}"""))
        val result = TestMandateController.create(agentCode).apply(fakeRequest)
        status(result) must be(BAD_REQUEST)
      }
    }

    "fetch a mandate" when {
      "a valid mandate id is passed" in {
        when(fetchServiceMock.fetchClientMandate(Matchers.eq(mandateId))) thenReturn Future.successful(MandateFetched(mandate))
        val result = TestMandateController.fetch(agentCode, mandateId).apply(FakeRequest())
        status(result) must be(OK)
        contentAsJson(result) must be(Json.toJson(mandate))
      }
    }

    "return Not Found" when {
      "an invalid or non-existing mandateId is passed" in {
        when(fetchServiceMock.fetchClientMandate(Matchers.eq(mandateId))) thenReturn Future.successful(MandateNotFound)
        val result = TestMandateController.fetch(agentCode, mandateId).apply(FakeRequest())
        status(result) must be(NOT_FOUND)
      }
    }

    "fetch all mandates with respect to a service and ARN" when {
      "agent supplies valid service and ARN" in {
        when(fetchServiceMock.getAllMandates(Matchers.eq(arn), Matchers.eq(service))).thenReturn(Future.successful(Seq(mandate)))
        val result = TestMandateController.fetchAll(agentCode, arn, service).apply(FakeRequest())
        status(result) must be(OK)
        contentAsJson(result) must be(Json.toJson(Seq(mandate)))
      }
    }

    "return not-found when trying to fetch all mandates with respect to a service and ARN" when {
      "agent supplies invalid/non-existing service and ARN" in {
        when(fetchServiceMock.getAllMandates(Matchers.eq(arn), Matchers.eq(service))).thenReturn(Future.successful(Nil))
        val result = TestMandateController.fetchAll(agentCode, arn, service).apply(FakeRequest())
        status(result) must be(NOT_FOUND)
      }
    }

    "update mandate for a client" when {
      "client provided valid payload and mandate has been successfully updated in mongo" in {
        when(updateServiceMock.updateMandate(Matchers.eq(mandate))(Matchers.any())).thenReturn(Future.successful(MandateUpdated(mandate)))
        val fakeRequest = FakeRequest(method = "POST", uri = "", headers = FakeHeaders(Seq("Content-type" -> Seq("application/json"))), body = Json.toJson(mandate))
        val result = TestMandateController.update(orgId).apply(fakeRequest)
        status(result) must be(OK)
      }
    }

    "throw error while trying to update mandate for a client" when {
      "client provided valid payload but mandate wasn't successfully updated in mongo" in {
        when(updateServiceMock.updateMandate(Matchers.eq(mandate))(Matchers.any())).thenReturn(Future.successful(MandateUpdateError))
        val fakeRequest = FakeRequest(method = "POST", uri = "", headers = FakeHeaders(Seq("Content-type" -> Seq("application/json"))), body = Json.toJson(mandate))
        val result = TestMandateController.update(orgId).apply(fakeRequest)
        status(result) must be(INTERNAL_SERVER_ERROR)
      }
    }

    "return bad-request while trying to update mandate for a client" when {
      "client provided invalid payload and" in {
        val fakeRequest = FakeRequest(method = "POST", uri = "", headers = FakeHeaders(Seq("Content-type" -> Seq("application/json"))), body = Json.parse("""{}"""))
        val result = TestMandateController.update(orgId).apply(fakeRequest)
        status(result) must be(BAD_REQUEST)
      }
    }

  }

  val fetchServiceMock = mock[MandateFetchService]
  val createServiceMock = mock[MandateCreateService]
  val updateServiceMock = mock[MandateUpdateService]
  val allocateAgentServiceMock = mock[AllocateAgentService]

  object TestMandateController extends MandateController {
    override val fetchService = fetchServiceMock
    override val createService = createServiceMock
    override val allocateAgentService = allocateAgentServiceMock
    override val updateService = updateServiceMock
  }

  override def beforeEach(): Unit = {
    reset(fetchServiceMock)
    reset(createServiceMock)
    reset(updateServiceMock)
    reset(allocateAgentServiceMock)
  }

  implicit override lazy val app: FakeApplication = FakeApplication(
    additionalConfiguration = Map("auditing.enabled" -> "false")
  )

  val mandateId = "123"
  val agentCode = "ABC"
  val orgId = "ORG"
  val arn = "JARN123456"
  val service = "ated"

  val mandate =
    Mandate(
      id = "123",
      createdBy = User("credid", "name", None),
      agentParty = Party("JARN123456", "Joe Bloggs", PartyType.Organisation, ContactDetails("test@test.com", Some("0123456789"))),
      clientParty = None,
      currentStatus = MandateStatus(Status.New, new DateTime(), "credid"),
      statusHistory = None,
      subscription = Subscription(None, Service("ated", "ATED"))
    )

  val createMandateDto = CreateMandateDto("test@test.com", "ated")

}

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
import uk.gov.hmrc.agentclientmandate.connectors.EmailSent
import uk.gov.hmrc.agentclientmandate.models._
import uk.gov.hmrc.agentclientmandate.repositories._
import uk.gov.hmrc.agentclientmandate.services._
import uk.gov.hmrc.agentclientmandate.utils.TestAudit
import uk.gov.hmrc.play.audit.model.Audit
import uk.gov.hmrc.play.http.HttpResponse

import scala.concurrent.Future


class MandateControllerSpec extends PlaySpec with OneServerPerSuite with MockitoSugar with BeforeAndAfterEach {

  "MandateController" should {

    "try to activate the client" when {

      "request is valid and client mandate found " in {
        when(fetchServiceMock.fetchClientMandate(Matchers.eq(mandateId))) thenReturn Future.successful(MandateFetched(approvedMandate))
        when(updateServiceMock.updateMandate(Matchers.any(), Matchers.any())(Matchers.any())) thenReturn Future.successful(MandateUpdated(mandate))
        when(relationshipServiceMock.maintainRelationship(Matchers.any(), Matchers.any(), Matchers.any())(Matchers.any())) thenReturn Future.successful(HttpResponse(200, None))
        when(notificationServiceMock.sendMail(Matchers.any(), Matchers.any(), Matchers.any(), Matchers.any(), Matchers.any())(Matchers.any())) thenReturn Future.successful(EmailSent)

        val result = TestMandateController.activate(agentCode, mandateId).apply(FakeRequest())

        status(result) must be(OK)
      }

      "bad request" in {
        when(fetchServiceMock.fetchClientMandate(Matchers.eq(mandateId))) thenReturn Future.successful(MandateFetched(approvedMandate))
        when(updateServiceMock.updateMandate(Matchers.any(), Matchers.any())(Matchers.any())) thenReturn Future.successful(MandateUpdated(mandate))
        when(relationshipServiceMock.maintainRelationship(Matchers.any(), Matchers.any(), Matchers.any())(Matchers.any())) thenReturn Future.successful(HttpResponse(400, None))

        val result = TestMandateController.activate(agentCode, mandateId).apply(FakeRequest())

        status(result) must be(BAD_REQUEST)
      }

      "server error" in {
        when(fetchServiceMock.fetchClientMandate(Matchers.eq(mandateId))) thenReturn Future.successful(MandateFetched(approvedMandate))
        when(updateServiceMock.updateMandate(Matchers.any(), Matchers.any())(Matchers.any())) thenReturn Future.successful(MandateUpdated(mandate))
        when(relationshipServiceMock.maintainRelationship(Matchers.any(), Matchers.any(), Matchers.any())(Matchers.any())) thenReturn Future.successful(HttpResponse(500, None))

        val result = TestMandateController.activate(agentCode, mandateId).apply(FakeRequest())

        status(result) must be(INTERNAL_SERVER_ERROR)
      }
    }

    "fail to activate client" when {

      "mongo update error occurs while changing the status to ACTIVE" in {
        when(fetchServiceMock.fetchClientMandate(Matchers.eq(mandateId))) thenReturn Future.successful(MandateFetched(approvedMandate))
        when(updateServiceMock.updateMandate(Matchers.any(), Matchers.eq(Some(Status.PendingActivation)))(Matchers.any())) thenReturn Future.successful(MandateUpdated(mandate))
        when(relationshipServiceMock.maintainRelationship(Matchers.any(), Matchers.any(), Matchers.any())(Matchers.any())) thenReturn Future.successful(HttpResponse(200, None))
        when(updateServiceMock.updateMandate(Matchers.any(), Matchers.eq(Some(Status.Active)))(Matchers.any())) thenReturn Future.successful(MandateUpdateError)
        when(notificationServiceMock.sendMail(Matchers.any(), Matchers.any(), Matchers.any(), Matchers.any(), Matchers.any())(Matchers.any())) thenReturn Future.successful(EmailSent)

        val result = TestMandateController.activate(agentCode, mandateId).apply(FakeRequest())

        status(result) must be(INTERNAL_SERVER_ERROR)
      }

      "mongo update error occurs while changing the status to PENDINGACTIVATION" in {
        when(fetchServiceMock.fetchClientMandate(Matchers.eq(mandateId))) thenReturn Future.successful(MandateFetched(approvedMandate))
        when(updateServiceMock.updateMandate(Matchers.any(), Matchers.any())(Matchers.any())) thenReturn Future.successful(MandateUpdateError)
        when(notificationServiceMock.sendMail(Matchers.any(), Matchers.any(), Matchers.any(), Matchers.any(), Matchers.any())(Matchers.any())) thenReturn Future.successful(EmailSent)

        val result = TestMandateController.activate(agentCode, mandateId).apply(FakeRequest())

        status(result) must be(NOT_FOUND)
      }

      "status of mandate returned is not ACTIVE" in {
        when(fetchServiceMock.fetchClientMandate(Matchers.eq(mandateId))) thenReturn Future.successful(MandateFetched(mandate))

        val thrown = the[RuntimeException] thrownBy await(TestMandateController.activate(agentCode, mandateId).apply(FakeRequest()))

        thrown.getMessage must include("Mandate with status New cannot be activated")
      }

    }


    "try to remove the mandate" when {

      "request is valid and client mandate found " in {
        when(fetchServiceMock.fetchClientMandate(Matchers.eq(mandateId))) thenReturn Future.successful(MandateFetched(activeMandate))
        when(updateServiceMock.updateMandate(Matchers.any(), Matchers.any())(Matchers.any())) thenReturn Future.successful(MandateUpdated(mandate))
        when(relationshipServiceMock.maintainRelationship(Matchers.any(), Matchers.any(), Matchers.any())(Matchers.any())) thenReturn Future.successful(HttpResponse(200, None))
        when(notificationServiceMock.sendMail(Matchers.any(), Matchers.eq(Status.Rejected), Matchers.any(), Matchers.any(), Matchers.any())(Matchers.any())) thenReturn Future.successful(EmailSent)

        val result = TestMandateController.remove(agentCode, mandateId).apply(FakeRequest())

        status(result) must be(OK)
      }

      "request is valid and it's an agent performing this " in {
        when(fetchServiceMock.fetchClientMandate(Matchers.eq(mandateId))) thenReturn Future.successful(MandateFetched(activeMandate))
        when(updateServiceMock.updateMandate(Matchers.any(), Matchers.any())(Matchers.any())) thenReturn Future.successful(MandateUpdated(mandate))
        when(relationshipServiceMock.maintainRelationship(Matchers.any(), Matchers.any(), Matchers.any())(Matchers.any())) thenReturn Future.successful(HttpResponse(200, None))
        when(notificationServiceMock.sendMail(Matchers.any(), Matchers.eq(Status.Rejected), Matchers.any(), Matchers.any(), Matchers.any())(Matchers.any())) thenReturn Future.successful(EmailSent)

        val result = TestAgentMandateController.remove(agentCode, mandateId).apply(FakeRequest())

        status(result) must be(OK)
      }

      "bad request" in {
        when(fetchServiceMock.fetchClientMandate(Matchers.eq(mandateId))) thenReturn Future.successful(MandateFetched(activeMandate))
        when(updateServiceMock.updateMandate(Matchers.any(), Matchers.any())(Matchers.any())) thenReturn Future.successful(MandateUpdated(mandate))
        when(relationshipServiceMock.maintainRelationship(Matchers.any(), Matchers.any(), Matchers.any())(Matchers.any())) thenReturn Future.successful(HttpResponse(400, None))

        val result = TestMandateController.remove(agentCode, mandateId).apply(FakeRequest())

        status(result) must be(BAD_REQUEST)
      }

      "server error" in {
        when(fetchServiceMock.fetchClientMandate(Matchers.eq(mandateId))) thenReturn Future.successful(MandateFetched(activeMandate))
        when(updateServiceMock.updateMandate(Matchers.any(), Matchers.any())(Matchers.any())) thenReturn Future.successful(MandateUpdated(mandate))
        when(relationshipServiceMock.maintainRelationship(Matchers.any(), Matchers.any(), Matchers.any())(Matchers.any())) thenReturn Future.successful(HttpResponse(500, None))

        val result = TestMandateController.remove(agentCode, mandateId).apply(FakeRequest())

        status(result) must be(INTERNAL_SERVER_ERROR)
      }
    }

    "try to remove the mandate as an agent" when {



    }

    "fail to remove client" when {

      "mongo update error occurs while changing the status to ACTIVE" in {
        when(fetchServiceMock.fetchClientMandate(Matchers.eq(mandateId))) thenReturn Future.successful(MandateFetched(activeMandate))
        when(updateServiceMock.updateMandate(Matchers.any(), Matchers.eq(Some(Status.PendingCancellation)))(Matchers.any())) thenReturn Future.successful(MandateUpdated(mandate))
        when(relationshipServiceMock.maintainRelationship(Matchers.any(), Matchers.any(), Matchers.any())(Matchers.any())) thenReturn Future.successful(HttpResponse(200, None))
        when(updateServiceMock.updateMandate(Matchers.any(), Matchers.eq(Some(Status.Cancelled)))(Matchers.any())) thenReturn Future.successful(MandateUpdateError)
        when(notificationServiceMock.sendMail(Matchers.any(), Matchers.eq(Status.Rejected), Matchers.any(), Matchers.any(), Matchers.any())(Matchers.any())) thenReturn Future.successful(EmailSent)

        val result = TestMandateController.remove(agentCode, mandateId).apply(FakeRequest())

        status(result) must be(INTERNAL_SERVER_ERROR)
      }

      "mongo update error occurs while changing the status to PENDINGACTIVATION" in {
        when(fetchServiceMock.fetchClientMandate(Matchers.eq(mandateId))) thenReturn Future.successful(MandateFetched(activeMandate))
        when(updateServiceMock.updateMandate(Matchers.any(), Matchers.any())(Matchers.any())) thenReturn Future.successful(MandateUpdateError)

        val result = TestMandateController.remove(agentCode, mandateId).apply(FakeRequest())

        status(result) must be(NOT_FOUND)

      }

      "status of mandate returned is not ACTIVE" in {
        when(fetchServiceMock.fetchClientMandate(Matchers.eq(mandateId))) thenReturn Future.successful(MandateFetched(mandate))

        val thrown = the[RuntimeException] thrownBy await(TestMandateController.remove(agentCode, mandateId).apply(FakeRequest()))

        thrown.getMessage must include("Mandate with status New cannot be removed")
      }

      "no mandate is fetched" in {
        when(fetchServiceMock.fetchClientMandate(Matchers.eq(mandateId))) thenReturn Future.successful(MandateNotFound)

        val result = TestMandateController.remove(agentCode, mandateId).apply(FakeRequest())

        status(result) must be(NOT_FOUND)

      }

      "mandate with no agent code is fetched" in {
        when(fetchServiceMock.fetchClientMandate(Matchers.eq(mandateId))) thenReturn Future.successful(MandateFetched(activeMandate1))
        when(updateServiceMock.updateMandate(Matchers.any(), Matchers.any())(Matchers.any())) thenReturn Future.successful(MandateUpdated(mandate))

        val thrown = the[RuntimeException] thrownBy await(TestMandateController.remove(agentCode, mandateId).apply(FakeRequest()))

        thrown.getMessage must include("agent code not found!")

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
        val fakeRequest = FakeRequest(method = "POST", uri = "", headers = FakeHeaders(Seq("Content-type" -> "application/json")), body = Json.toJson(createMandateDto))
        val result = TestMandateController.create(agentCode).apply(fakeRequest)
        status(result) must be(CREATED)
        contentAsJson(result) must be(Json.parse("""{"mandateId":"123"}"""))
      }

    }

    "return bad-request" when {
      "invalid dto is passed by agent" in {
        val fakeRequest = FakeRequest(method = "POST", uri = "", headers = FakeHeaders(Seq("Content-type" -> "application/json")), body = Json.parse("""{}"""))
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

      "return not found with invalid or non-existing mandateId is passed" in {
        when(fetchServiceMock.fetchClientMandate(Matchers.eq(mandateId))) thenReturn Future.successful(MandateNotFound)
        val result = TestMandateController.fetch(agentCode, mandateId).apply(FakeRequest())
        status(result) must be(NOT_FOUND)
      }

      "mandate found when fetching by client and valid clientId" in {
        when(fetchServiceMock.fetchClientMandate(Matchers.any(), Matchers.any())) thenReturn Future.successful(MandateFetched(mandate))
        val result = TestMandateController.fetchByClient(orgId, clientId, service).apply(FakeRequest())
        status(result) must be(OK)
        contentAsJson(result) must be(Json.toJson(mandate))
      }

      "mandate not found when fetching by client using invalid clientId" in {
        when(fetchServiceMock.fetchClientMandate(Matchers.any(), Matchers.any())) thenReturn Future.successful(MandateNotFound)

        val result = TestMandateController.fetchByClient(orgId, clientId, service).apply(FakeRequest())
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
        when(updateServiceMock.approveMandate(Matchers.eq(mandate))(Matchers.any())).thenReturn(Future.successful(MandateUpdated(mandate)))
        val fakeRequest = FakeRequest(method = "POST", uri = "", headers = FakeHeaders(Seq("Content-type" -> "application/json")), body = Json.toJson(mandate))
        val result = TestMandateController.approve(orgId).apply(fakeRequest)
        status(result) must be(OK)
      }
    }

    "throw error while trying to update mandate for a client" when {
      "client provided valid payload but mandate wasn't successfully updated in mongo" in {
        when(updateServiceMock.approveMandate(Matchers.eq(mandate))(Matchers.any())).thenReturn(Future.successful(MandateUpdateError))
        val fakeRequest = FakeRequest(method = "POST", uri = "", headers = FakeHeaders(Seq("Content-type" -> "application/json")), body = Json.toJson(mandate))
        val result = TestMandateController.approve(orgId).apply(fakeRequest)
        status(result) must be(INTERNAL_SERVER_ERROR)
      }
    }

    "return bad-request while trying to update mandate for a client" when {
      "client provided invalid payload and" in {
        val fakeRequest = FakeRequest(method = "POST", uri = "", headers = FakeHeaders(Seq("Content-type" -> "application/json")), body = Json.parse("""{}"""))
        val result = TestMandateController.approve(orgId).apply(fakeRequest)
        status(result) must be(BAD_REQUEST)
      }
    }

    "update mandate with pending cancellation status" when {
      "agent has rejected client and status returned ok" in {
        when(updateServiceMock.updateMandate(Matchers.any(), Matchers.any())(Matchers.any())).thenReturn(Future.successful(MandateUpdated(mandate)))
        when(fetchServiceMock.fetchClientMandate(Matchers.eq(mandateId))) thenReturn Future.successful(MandateFetched(mandate))
        val result = TestMandateController.agentRejectsClient("", mandateId).apply(FakeRequest())
        status(result) must be(OK)
      }

      "agent has rejected client and status returned not ok" in {
        when(updateServiceMock.updateMandate(Matchers.any(), Matchers.any())(Matchers.any())).thenReturn(Future.successful(MandateUpdateError))
        when(fetchServiceMock.fetchClientMandate(Matchers.eq(mandateId))) thenReturn Future.successful(MandateFetched(mandate))
        val result = TestMandateController.agentRejectsClient("", mandateId).apply(FakeRequest())
        status(result) must be(INTERNAL_SERVER_ERROR)
      }

      "agent has rejected client and status returned not found" in {
        when(fetchServiceMock.fetchClientMandate(Matchers.eq(mandateId))) thenReturn Future.successful(MandateNotFound)
        val result = TestMandateController.agentRejectsClient("", mandateId).apply(FakeRequest())
        status(result) must be(NOT_FOUND)
      }
    }

    "get agent details" when {
      "agent requests details" in {
        when(agentDetailsServiceMock.getAgentDetails(Matchers.any())(Matchers.any())).thenReturn(Future.successful(agentDetails))
        val result = TestMandateController.getAgentDetails(agentCode).apply(FakeRequest())
        status(result) must be(OK)
      }
    }

    "import existing relationships" must {
      "sending incorrect data" must {
        "return Bad Request" in {
          val fakeRequest = FakeRequest(method = "POST", uri = "", headers = FakeHeaders(Seq("Content-type" -> "application/json")), body = Json.parse(incorrectJson))
          val result = TestMandateController.importExistingRelationships("agentCode").apply(fakeRequest)
          status(result) must be(BAD_REQUEST)
        }
      }

      "sending correct data" must {
        "return Ok when insert relationships ok" in {
          when(createServiceMock.insertExistingRelationships(Matchers.any())).thenReturn(Future.successful(ExistingRelationshipsInserted))
          val fakeRequest = FakeRequest(method = "POST", uri = "", headers = FakeHeaders(Seq("Content-type" -> "application/json")), body = Json.parse(correctJson))
          val result = TestMandateController.importExistingRelationships("agentCode").apply(fakeRequest)
          status(result) must be(OK)
        }

        "return Ok when relationships already exist" in {
          when(createServiceMock.insertExistingRelationships(Matchers.any())).thenReturn(Future.successful(ExistingRelationshipsAlreadyExist))
          val fakeRequest = FakeRequest(method = "POST", uri = "", headers = FakeHeaders(Seq("Content-type" -> "application/json")), body = Json.parse(correctJson))
          val result = TestMandateController.importExistingRelationships("agentCode").apply(fakeRequest)
          status(result) must be(OK)
        }

        "exception thrown when there is an error inserting relationships" in {
          when(createServiceMock.insertExistingRelationships(Matchers.any())).thenReturn(Future.successful(ExistingRelationshipsInsertError))
          val fakeRequest = FakeRequest(method = "POST", uri = "", headers = FakeHeaders(Seq("Content-type" -> "application/json")), body = Json.parse(correctJson))

          val thrown = the[RuntimeException] thrownBy await(TestMandateController.importExistingRelationships("agentCode").apply(fakeRequest))

          thrown.getMessage must include("Could not insert existing relationships")
        }
      }
    }

      "trying to create mandate for non-uk client by an agent" when {

      "return CREATED as status code, for successful creation" in {
        val dto = NonUKClientDto("safeId", "atedRefNum", "ated", "aa@mail.com", "arn", "bb@mail.com", "client display name")
        val fakeRequest = FakeRequest(method = "POST", uri = "", headers = FakeHeaders(Seq("Content-type" -> "application/json")), body = Json.toJson(dto))
        when(createServiceMock.createMandateForNonUKClient(Matchers.any(), Matchers.eq(dto))(Matchers.any())).thenReturn(Future.successful("mandateId"))
        val result = TestMandateController.createRelationship("agentCode").apply(fakeRequest)
        status(result) must be(CREATED)
      }

    }

    "edit mandate details" must {
      "return OK, when mandate is updated in MongoDB" in {
        when(updateServiceMock.updateMandate(Matchers.any(), Matchers.any())(Matchers.any())).thenReturn(Future.successful(MandateUpdated(mandate)))
        val fakeRequest = FakeRequest(method = "POST", uri = "", headers = FakeHeaders(Seq("Content-type" -> "application/json")), body = Json.toJson(mandate))
        val result = TestMandateController.editMandate("agentCode").apply(fakeRequest)
        status(result) must be(OK)
      }
      "return INTERNAL_SERVER_ERROR, when update fail in MongoDB" in {
        when(updateServiceMock.updateMandate(Matchers.any(), Matchers.any())(Matchers.any())).thenReturn(Future.successful(MandateUpdateError))
        val fakeRequest = FakeRequest(method = "POST", uri = "", headers = FakeHeaders(Seq("Content-type" -> "application/json")), body = Json.toJson(mandate))
        val result = TestMandateController.editMandate(agentCode).apply(fakeRequest)
        status(result) must be(INTERNAL_SERVER_ERROR)
      }
    }

  }

  val incorrectJson =
    s"""
        {
          "serviceName": "",
          "agentPartyId": "",
          "credId": "",
          "clientSubscriptionId": "",
          "agentCode": ""
        }
      """

  val correctJson =
    s"""
        [
            {
              "serviceName": "",
              "agentPartyId": "",
              "credId": "",
              "clientSubscriptionId": "",
              "agentCode": ""
            },
            {
              "serviceName": "",
              "agentPartyId": "",
              "credId": "",
              "clientSubscriptionId": "",
              "agentCode": ""
            }
        ]
      """

  val fetchServiceMock = mock[MandateFetchService]
  val createServiceMock = mock[MandateCreateService]
  val updateServiceMock = mock[MandateUpdateService]
  val relationshipServiceMock = mock[RelationshipService]
  val agentDetailsServiceMock = mock[AgentDetailsService]
  val notificationServiceMock = mock[NotificationEmailService]

  object TestAgentMandateController extends MandateController {
    override val fetchService = fetchServiceMock
    override val createService = createServiceMock
    override val relationshipService = relationshipServiceMock
    override val updateService = updateServiceMock
    override val agentDetailsService = agentDetailsServiceMock
    override val emailNotificationService = notificationServiceMock
    override val audit: Audit = new TestAudit
    override val userType = "agent"
  }

  object TestMandateController extends MandateController {
    override val fetchService = fetchServiceMock
    override val createService = createServiceMock
    override val relationshipService = relationshipServiceMock
    override val updateService = updateServiceMock
    override val agentDetailsService = agentDetailsServiceMock
    override val emailNotificationService = notificationServiceMock
    override val audit: Audit = new TestAudit
    override val userType = "client"
  }

  override def beforeEach(): Unit = {
    reset(fetchServiceMock)
    reset(createServiceMock)
    reset(updateServiceMock)
    reset(relationshipServiceMock)
  }

  implicit override lazy val app: FakeApplication = FakeApplication(
    additionalConfiguration = Map("auditing.enabled" -> "false")
  )

  val mandateId = "123"
  val agentCode = "ABC"
  val clientId = "XYZ"
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
      statusHistory = Nil,
      subscription = Subscription(None, Service("ated", "ATED")),
      clientDisplayName = "client display name"
    )

  val approvedMandate =
    Mandate(
      id = "123",
      createdBy = User("credid", "name", Some("agent-code")),
      agentParty = Party("JARN123456", "Joe Bloggs", PartyType.Organisation, ContactDetails("test@test.com", Some("0123456789"))),
      clientParty = None,
      currentStatus = MandateStatus(Status.Approved, new DateTime(), "credid"),
      statusHistory = Nil,
      subscription = Subscription(None, Service("ated", "ATED")),
      clientDisplayName = "client display name"
    )

  val activeMandate =
    Mandate(
      id = "123",
      createdBy = User("credid", "name", Some("agent-code")),
      agentParty = Party("JARN123456", "Joe Bloggs", PartyType.Organisation, ContactDetails("test@test.com", Some("0123456789"))),
      clientParty = None,
      currentStatus = MandateStatus(Status.Active, new DateTime(), "credid"),
      statusHistory = Nil,
      subscription = Subscription(None, Service("ated", "ATED")),
      clientDisplayName = "client display name"
    )

  val activeMandate1 =
    Mandate(
      id = "123",
      createdBy = User("credid", "name", None),
      agentParty = Party("JARN123456", "Joe Bloggs", PartyType.Organisation, ContactDetails("test@test.com", Some("0123456789"))),
      clientParty = None,
      currentStatus = MandateStatus(Status.Active, new DateTime(), "credid"),
      statusHistory = Nil,
      subscription = Subscription(None, Service("ated", "ATED")),
      clientDisplayName = "client display name"
    )


  val createMandateDto = CreateMandateDto("test@test.com", "ated", "client display name")

  val registeredAddressDetails = RegisteredAddressDetails("123 Fake Street", "Somewhere", None, None, None, "GB")
  val agentDetails = AgentDetails("Agent Ltd.", registeredAddressDetails)

}

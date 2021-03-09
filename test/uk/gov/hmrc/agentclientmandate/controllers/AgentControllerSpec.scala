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

package uk.gov.hmrc.agentclientmandate.controllers

import org.joda.time.DateTime
import org.mockito.ArgumentMatchers
import org.mockito.ArgumentMatchers._
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterEach
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.PlaySpec
import play.api.libs.json.Json
import play.api.mvc.{ControllerComponents, Result}
import play.api.test.Helpers._
import play.api.test.{FakeHeaders, FakeRequest, Helpers}
import uk.gov.hmrc.agentclientmandate.auth.AuthRetrieval
import uk.gov.hmrc.agentclientmandate.builders.AgentBuilder
import uk.gov.hmrc.agentclientmandate.connectors.EmailSent
import uk.gov.hmrc.agentclientmandate.models._
import uk.gov.hmrc.agentclientmandate.repositories._
import uk.gov.hmrc.agentclientmandate.services._
import uk.gov.hmrc.agentclientmandate.utils.Generators._
import uk.gov.hmrc.auth.core.retrieve.{AgentInformation, Credentials}
import uk.gov.hmrc.auth.core.{Enrolment, EnrolmentIdentifier}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.bootstrap.auth.DefaultAuthConnector

import scala.concurrent.{ExecutionContext, Future}

class AgentControllerSpec extends PlaySpec with MockitoSugar with BeforeAndAfterEach {

  "AgentController" should {
    "return a NOT_FOUND " when {
      "an exception is thrown by updateMandate in 'activate'" in  new Setup {
        when(fetchServiceMock.fetchClientMandate(ArgumentMatchers.eq(mandateId))(any())) thenReturn Future.successful(MandateFetched(approvedMandate))
        when(updateServiceMock.updateMandate(any(), any())(any())) thenReturn Future.failed(new RuntimeException("[AuthRetrieval] No GGCredId found."))

        val result = TestMandateController.activate(agentCode, mandateId).apply(FakeRequest())
        status(result) mustBe NOT_FOUND
      }

      "an exception is thrown by createMandate in 'create'" in new Setup {
        when(createServiceMock.createMandate(any(), any())(any(), any())).thenReturn(Future.failed(new RuntimeException("[AuthRetrieval] No GGCredId found.")))

        val fakeRequest = FakeRequest(method = "POST", uri = "", headers = FakeHeaders(Seq("Content-type" -> "application/json")), body = Json.toJson(createMandateDto))
        val result = TestMandateController.create(agentCode).apply(fakeRequest)
        status(result) mustBe NOT_FOUND
      }

      "an exception is thrown by getAllMandates in 'fetchAll'" in new Setup {
        when(fetchServiceMock.getAllMandates(any(), any(), any(), any())(any(),any())).thenReturn(Future.failed(new RuntimeException("[AuthRetrieval] No GGCredId found.")))

        val result = TestMandateController.fetchAll(arn, service, None, None).apply(FakeRequest())
        status(result) mustBe NOT_FOUND
      }


      "an exception is thrown by getAgentDetails in 'getAgentDetails'" in new Setup {
        when(agentDetailsServiceMock.getAgentDetails(any())).thenReturn(Future.failed(new RuntimeException("[AuthRetrieval] No enrolment id found for AgentRefNumber.")))
        val result = TestMandateController.getAgentDetails().apply(FakeRequest())
        status(result) mustBe NOT_FOUND
      }

      "an exception is thrown by createMandateForNonUKClient in 'createRelationship'" in new Setup {
        val dto = NonUKClientDto(safeIDGen.sample.get, "atedRefNum", "ated", "clientEmail@email.com", agentReferenceNumberGen.sample.get, "agentEmail@email.com", "client display name")
        val fakeRequest = FakeRequest(method = "POST", uri = "", headers = FakeHeaders(Seq("Content-type" -> "application/json")), body = Json.toJson(dto))
        when(createServiceMock.createMandateForNonUKClient(any(), ArgumentMatchers.eq(dto))(any(), any())).thenReturn(Future.failed(new RuntimeException("[AuthRetrieval] No GGCredId found.")))
        val result = TestMandateController.createRelationship("agentCode").apply(fakeRequest)
        status(result) mustBe NOT_FOUND
      }

      "an exception is thrown by updateMandate in 'agentRejectsClient'" in new Setup {
        when(fetchServiceMock.fetchClientMandate(ArgumentMatchers.eq(mandateId))(any())) thenReturn Future.successful(MandateFetched(newMandate))
        when(updateServiceMock.updateMandate(any(), any())(any())).thenReturn(Future.failed(new RuntimeException("[AuthRetrieval] No GGCredId found.")))

        val result = TestMandateController.agentRejectsClient("", mandateId).apply(FakeRequest())
        status(result) mustBe NOT_FOUND
      }

      "an exception is thrown by updateMandate in 'editMandate'" in new Setup {
        when(updateServiceMock.updateMandate(any(), any())(any())).thenReturn(Future.failed(new RuntimeException("[AuthRetrieval] No GGCredId found.")))

        val fakeRequest = FakeRequest(method = "POST", uri = "", headers = FakeHeaders(Seq("Content-type" -> "application/json")), body = Json.toJson(newMandate))
        val result = TestMandateController.editMandate("agentCode").apply(fakeRequest)
        status(result) mustBe NOT_FOUND
      }

      "an exception is thrown by updateAgentCredId in 'updateAgentCredId'" in new Setup {
        when(updateServiceMock.updateAgentCredId(any())(any())).thenReturn(Future.failed(new RuntimeException("[AuthRetrieval] No GGCredId found.")))

        val fakeRequest = FakeRequest(method = "POST", uri = "", headers = FakeHeaders(Seq("Content-type" -> "application/json")), body = Json.toJson("oldCredId"))
        val result = TestMandateController.updateAgentCredId().apply(fakeRequest)
        status(result) mustBe NOT_FOUND
      }

      "an exception is thrown by updateMandateForNonUKClient in 'updateRelationship'" in new Setup {
        val dto = NonUKClientDto(safeIDGen.sample.get, "atedRefNum", "ated", emailGen.sample.get, agentReferenceNumberGen.sample.get,  emailGen.sample.get, "client display name", mandateReferenceGen.sample)
        when(createServiceMock.updateMandateForNonUKClient(any(), ArgumentMatchers.eq(dto))(any(), any())).thenReturn(Future.failed(new RuntimeException("[AuthRetrieval] No GGCredId found.")))
        val fakeRequest = FakeRequest(method = "POST", uri = "", headers = FakeHeaders(Seq("Content-type" -> "application/json")), body = Json.toJson(dto))
        val result = TestMandateController.updateRelationship("agentCode").apply(fakeRequest)
        status(result) mustBe NOT_FOUND
      }
    }

    "activate the client" when {

      "request is valid and client mandate found " in new Setup {

        when(fetchServiceMock.fetchClientMandate(ArgumentMatchers.eq(mandateId))(any())) thenReturn Future.successful(MandateFetched(approvedMandate))
        when(updateServiceMock.updateMandate(ArgumentMatchers.eq(approvedMandate), any())(any())) thenReturn Future.successful(MandateUpdated(pendingActiveMandate))
        val result = TestMandateController.activate(agentCode, mandateId).apply(FakeRequest())
        status(result) must be (OK)
      }
    }

    "not activate the client" when {

      "status of mandate returned is not ACTIVE" in new Setup {
        when(fetchServiceMock.fetchClientMandate(ArgumentMatchers.eq(mandateId))(any())) thenReturn Future.successful(MandateFetched(newMandate))
        val thrown = the[RuntimeException] thrownBy await(TestMandateController.activate(agentCode, mandateId).apply(FakeRequest()))
        thrown.getMessage must include("Mandate with status New cannot be activated")
      }

      "cant find mandate while changing the status to PENDINGACTIVATION" in new Setup {
        when(fetchServiceMock.fetchClientMandate(ArgumentMatchers.eq(mandateId))(any())) thenReturn Future.successful(MandateFetched(approvedMandate))
        when(updateServiceMock.updateMandate(any(), any())(any())) thenReturn Future.successful(MandateUpdateError)
        when(notificationServiceMock.sendMail(any(), any(), any(), any(), any(), any(), any(), any())(any())) thenReturn Future.successful(EmailSent)

        val result = TestMandateController.activate(agentCode, mandateId).apply(FakeRequest())

        status(result) must be(NOT_FOUND)
      }

      "cant find mandate" in new Setup {
        when(fetchServiceMock.fetchClientMandate(ArgumentMatchers.eq(mandateId))(any())) thenReturn Future.successful(MandateNotFound)

        val result = TestMandateController.activate(agentCode, mandateId).apply(FakeRequest())

        status(result) must be(NOT_FOUND)
      }
    }

    "create a mandate and return mandate Id" when {

      "an agent request it and passes valid DTO" in new Setup {
        when(createServiceMock.createMandate(ArgumentMatchers.eq(agentCode), ArgumentMatchers.eq(createMandateDto))(any(), any())).thenReturn(Future.successful(mandateId))
        val fakeRequest = FakeRequest(method = "POST", uri = "", headers = FakeHeaders(Seq("Content-type" -> "application/json")), body = Json.toJson(createMandateDto))
        val result = TestMandateController.create(agentCode).apply(fakeRequest)
        status(result) must be(CREATED)
        contentAsJson(result) must be(Json.parse("""{"mandateId":"123"}"""))
      }
    }

    "return bad-request" when {
      "invalid dto is passed by agent" in new Setup {
        val fakeRequest = FakeRequest(method = "POST", uri = "", headers = FakeHeaders(Seq("Content-type" -> "application/json")), body = Json.parse("""{}"""))
        val result = TestMandateController.create(agentCode).apply(fakeRequest)
        status(result) must be(BAD_REQUEST)
      }
    }
  }

    "fetch all mandates with respect to a service and ARN" when {
      "agent supplies valid service and ARN" in new Setup {
        when(fetchServiceMock.getAllMandates(ArgumentMatchers.eq(arn), ArgumentMatchers.eq(service), any(), any())(any(),any())).thenReturn(Future.successful(Seq(newMandate)))
        val result = TestMandateController.fetchAll(arn, service, None, None).apply(FakeRequest())
        status(result) must be(OK)
        contentAsJson(result) must be(Json.toJson(Seq(newMandate)))
      }
    }

    "return not-found when trying to fetch all mandates with respect to a service and ARN" when {
      "agent supplies invalid/non-existing service and ARN" in new Setup {
        when(fetchServiceMock.getAllMandates(ArgumentMatchers.eq(arn), ArgumentMatchers.eq(service), any(), any())(any(),any())).thenReturn(Future.successful(Nil))
        val result = TestMandateController.fetchAll(arn, service, None, None).apply(FakeRequest())
        status(result) must be(NOT_FOUND)
      }
    }

    "update mandate with pending cancellation status" when {
      "agent has rejected client and status returned ok" in new Setup {
        when(updateServiceMock.updateMandate(any(), any())(any())).thenReturn(Future.successful(MandateUpdated(newMandate)))
        when(fetchServiceMock.fetchClientMandate(ArgumentMatchers.eq(mandateId))(any())) thenReturn Future.successful(MandateFetched(newMandate))
        val result = TestMandateController.agentRejectsClient("", mandateId).apply(FakeRequest())
        status(result) must be(OK)
      }

      "agent has rejected client but mandate cannot be updated" in new Setup {
        when(updateServiceMock.updateMandate(any(), any())(any())).thenReturn(Future.successful(MandateUpdateError))
        when(fetchServiceMock.fetchClientMandate(ArgumentMatchers.eq(mandateId))(any())) thenReturn Future.successful(MandateFetched(newMandate))
        val result = TestMandateController.agentRejectsClient("", mandateId).apply(FakeRequest())
        status(result) must be(INTERNAL_SERVER_ERROR)
      }
    }

    "return NotFound" when {
      "agent has rejected client and mandate cannot be found" in new Setup {
        when(fetchServiceMock.fetchClientMandate(ArgumentMatchers.eq(mandateId))(any())) thenReturn Future.successful(MandateNotFound)
        val result = TestMandateController.agentRejectsClient("", mandateId).apply(FakeRequest())
        status(result) must be(NOT_FOUND)
      }
    }

    "get agent details" when {
      "agent requests details" in new Setup {
        when(agentDetailsServiceMock.getAgentDetails(any())).thenReturn(Future.successful(agentDetails))
        val result = TestMandateController.getAgentDetails().apply(FakeRequest())
        status(result) must be(OK)
      }
    }

    "trying to create mandate for non-uk client by an agent" when {

      "return CREATED as status code, for successful creation" in new Setup {
        val dto = NonUKClientDto(safeIDGen.sample.get, "atedRefNum", "ated", emailGen.sample.get, agentReferenceNumberGen.sample.get, emailGen.sample.get, "client display name")
        val fakeRequest = FakeRequest(method = "POST", uri = "", headers = FakeHeaders(Seq("Content-type" -> "application/json")), body = Json.toJson(dto))
        when(createServiceMock.createMandateForNonUKClient(any(), ArgumentMatchers.eq(dto))(any(), any())).thenReturn(Future.unit)
        val result = TestMandateController.createRelationship("agentCode").apply(fakeRequest)
        status(result) must be(CREATED)
        verify(createServiceMock, times(1)).createMandateForNonUKClient(any(), any())(any(), any())
      }
    }

    "trying to update mandate for non-uk client by an agent" when {

      "return CREATED as status code, for successful creation" in new Setup {
        val dto = NonUKClientDto(safeIDGen.sample.get, "atedRefNum", "ated", emailGen.sample.get, agentReferenceNumberGen.sample.get,  emailGen.sample.get, "client display name", mandateReferenceGen.sample)
        val fakeRequest = FakeRequest(method = "POST", uri = "", headers = FakeHeaders(Seq("Content-type" -> "application/json")), body = Json.toJson(dto))
        when(createServiceMock.updateMandateForNonUKClient(any(), ArgumentMatchers.eq(dto))(any(), any())).thenReturn(Future.unit)
        val result = TestMandateController.updateRelationship("agentCode").apply(fakeRequest)
        status(result) must be(CREATED)
        verify(createServiceMock, times(1)).updateMandateForNonUKClient(any(), any())(any(), any())
      }
    }


    "edit mandate details" must {
      "return OK, when mandate is updated in MongoDB" in new Setup {
        when(updateServiceMock.updateMandate(any(), any())(any())).thenReturn(Future.successful(MandateUpdated(newMandate)))
        val fakeRequest = FakeRequest(method = "POST", uri = "", headers = FakeHeaders(Seq("Content-type" -> "application/json")), body = Json.toJson(newMandate))
        val result = TestMandateController.editMandate("agentCode").apply(fakeRequest)
        status(result) must be(OK)
      }
      "return INTERNAL_SERVER_ERROR, when update fail in MongoDB" in new Setup {
        when(updateServiceMock.updateMandate(any(), any())(any())).thenReturn(Future.successful(MandateUpdateError))
        val fakeRequest = FakeRequest(method = "POST", uri = "", headers = FakeHeaders(Seq("Content-type" -> "application/json")), body = Json.toJson(newMandate))
        val result = TestMandateController.editMandate(agentCode).apply(fakeRequest)
        status(result) must be(INTERNAL_SERVER_ERROR)
      }
    }

    "isAgentMissingEmail" must {
      "return Found when mandates found" in new Setup {
        when(fetchServiceMock.getMandatesMissingAgentsEmails(any(), any())(any())) thenReturn Future.successful(mandates)
        val result = TestMandateController.isAgentMissingEmail(arn, service).apply(FakeRequest())
        status(result) must be(OK)
      }

      "return Ok when no mandates found without email addresses" in new Setup {
        when(fetchServiceMock.getMandatesMissingAgentsEmails(any(), any())(any())) thenReturn Future.successful(Nil)
        val result = TestMandateController.isAgentMissingEmail(arn, service).apply(FakeRequest())
        status(result) must be(NO_CONTENT)
      }
    }

    "updateAgentEmail" must {
      "return ok if agents email updated" in new Setup {
        when(updateServiceMock.updateAgentEmail(any(), any(), any())) thenReturn Future.successful(MandateUpdatedEmail)
        val fakeRequest = FakeRequest(method = "POST", uri = "", headers = FakeHeaders(Seq("Content-type" -> "application/json")), body = Json.toJson("test@mail.com"))
        val result = TestMandateController.updateAgentEmail(arn, service).apply(fakeRequest)
        status(result) must be(OK)
      }

      "return error if agents email not updated" in new Setup {
        when(updateServiceMock.updateAgentEmail(any(), any(), any())) thenReturn Future.successful(MandateUpdateError)
        val fakeRequest = FakeRequest(method = "POST", uri = "", headers = FakeHeaders(Seq("Content-type" -> "application/json")), body = Json.toJson("test@mail.com"))
        val result = TestMandateController.updateAgentEmail(arn, service).apply(fakeRequest)
        status(result) must be(INTERNAL_SERVER_ERROR)
      }

      "return bad request if no email address sent" in new Setup {
        val fakeRequest = FakeRequest(method = "POST", uri = "", headers = FakeHeaders(Seq("Content-type" -> "application/json")), body = Json.toJson(""))
        val result = TestMandateController.updateAgentEmail(arn, service).apply(fakeRequest)
        status(result) must be(BAD_REQUEST)
      }
    }

    "updateCredId" must {
      "return ok if credId updated" in new Setup {
        when(updateServiceMock.updateAgentCredId(any())(any())) thenReturn Future.successful(MandateUpdatedCredId)
        val fakeRequest = FakeRequest(method = "POST", uri = "", headers = FakeHeaders(Seq("Content-type" -> "application/json")), body = Json.toJson("oldCredId"))
        val result = TestMandateController.updateAgentCredId().apply(fakeRequest)
        status(result) must be(OK)
      }

      "return error if credId not updated" in new Setup {
        when(updateServiceMock.updateAgentCredId(any())(any())) thenReturn Future.successful(MandateUpdateError)
        val fakeRequest = FakeRequest(method = "POST", uri = "", headers = FakeHeaders(Seq("Content-type" -> "application/json")), body = Json.toJson("oldCredId"))
        val result = TestMandateController.updateAgentCredId().apply(fakeRequest)
        status(result) must be(INTERNAL_SERVER_ERROR)
      }

      "return bad request if no credId sent" in new Setup {
        val fakeRequest = FakeRequest(method = "POST", uri = "", headers = FakeHeaders(Seq("Content-type" -> "application/json")), body = Json.toJson(""))
        val result = TestMandateController.updateAgentCredId().apply(fakeRequest)
        status(result) must be(BAD_REQUEST)
      }
    }

    "getClientsThatCancelled" must {
      "return ok if mandates found and return client display names" in new Setup {
        when(fetchServiceMock.fetchClientCancelledMandates(any(), any())(any())) thenReturn Future.successful(List("AAA", "BBB"))
        val result = TestMandateController.getClientsThatCancelled(arn, service).apply(FakeRequest())
        status(result) must be(OK)
        contentAsJson(result) must be(Json.toJson(Seq("AAA", "BBB")))
      }

      "return NotFound if no mandates returned" in new Setup {
        when(fetchServiceMock.fetchClientCancelledMandates(any(), any())(any())) thenReturn Future.successful(Nil)
        val result = TestMandateController.getClientsThatCancelled(arn, service).apply(FakeRequest())
        status(result) must be(NOT_FOUND)
      }
    }

  implicit val hc: HeaderCarrier = HeaderCarrier()

  val mandates: Seq[String] = Seq("AAAAAAA", "BBBBBB", "CCCCCC")

  val ar: AuthRetrieval = AuthRetrieval(
    enrolments = Set(
      Enrolment(
        key = "HMRC-AGENT-AGENT",
        identifiers = Seq(EnrolmentIdentifier(key = "AgentRefNumber", value = agentBusinessUtrGen.sample.get)),
        state = "active"
      ),
      Enrolment(
        key = "HMRC-ATED-ORG",
        identifiers = Seq(EnrolmentIdentifier(key = "ATEDRefNumber", value = "ated-ref-num")),
        state = "active"
      )
    ),
    agentInformation = AgentInformation(None, None, None),
    Option(Credentials(providerId = "cred-id-113244018119", providerType = "GovernmentGateway"))
  )

  val fetchServiceMock: MandateFetchService = mock[MandateFetchService]
  val createServiceMock: MandateCreateService = mock[MandateCreateService]
  val updateServiceMock: MandateUpdateService = mock[MandateUpdateService]
  val relationshipServiceMock: RelationshipService = mock[RelationshipService]
  val agentDetailsServiceMock: AgentDetailsService = mock[AgentDetailsService]
  val notificationServiceMock: NotificationEmailService = mock[NotificationEmailService]
  val authConnectorMock: DefaultAuthConnector = mock[DefaultAuthConnector]
  val auditConnectorMock: AuditConnector = mock[AuditConnector]
  lazy val cc: ControllerComponents = Helpers.stubControllerComponents()

  class Setup {
    val TestMandateController = new AgentController(
      createServiceMock,
      updateServiceMock,
      relationshipServiceMock,
      agentDetailsServiceMock,
      auditConnectorMock,
      notificationServiceMock,
      fetchServiceMock,
      authConnectorMock,
      cc
    )
    {
       override def authRetrieval(body: AuthRetrieval => Future[Result])(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Result] = body(ar)
    }
  }

  override def beforeEach(): Unit = {
    reset(fetchServiceMock)
    reset(createServiceMock)
    reset(updateServiceMock)
    reset(relationshipServiceMock)
    reset(authConnectorMock)
    reset(agentDetailsServiceMock)
    reset(notificationServiceMock)
  }

  val mandateId = "123"
  val agentCode = "ABC"
  val clientId = "XYZ"
  val orgId = "ORG"
  val arn = "JARN123456"
  val service = "ated"

  val newMandate =
    Mandate(
      id = "123",
      createdBy = User("credid", "name", None),
      agentParty = Party(partyIDGen.sample.get, nameGen.sample.get, PartyType.Organisation, ContactDetails(emailGen.sample.get, telephoneNumberGen.sample)),
      clientParty = None,
      currentStatus = MandateStatus(Status.New, new DateTime(), "credid"),
      statusHistory = Nil,
      subscription = Subscription(None, Service("ated", "ATED")),
      clientDisplayName = "client display name"
    )

  val pendingActiveMandate =
    Mandate(
      id = "123",
      createdBy = User("credid", "name", None),
      agentParty = Party(partyIDGen.sample.get, nameGen.sample.get, PartyType.Organisation, ContactDetails(emailGen.sample.get, telephoneNumberGen.sample)),
      clientParty = None,
      currentStatus = MandateStatus(Status.PendingActivation, new DateTime(), "credid"),
      statusHistory = Nil,
      subscription = Subscription(None, Service("ated", "ATED")),
      clientDisplayName = "client display name"
    )

  val approvedMandate =
    Mandate(
      id = "123",
      createdBy = User("credid", "name", Some("agent-code")),
      agentParty = Party(partyIDGen.sample.get, nameGen.sample.get, PartyType.Organisation, ContactDetails(emailGen.sample.get, telephoneNumberGen.sample)),
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
      agentParty = Party(partyIDGen.sample.get, nameGen.sample.get, PartyType.Organisation, ContactDetails(emailGen.sample.get, telephoneNumberGen.sample)),
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
      agentParty = Party(partyIDGen.sample.get, nameGen.sample.get, PartyType.Organisation, ContactDetails(emailGen.sample.get, telephoneNumberGen.sample)),
      clientParty = None,
      currentStatus = MandateStatus(Status.Active, new DateTime(), "credid"),
      statusHistory = Nil,
      subscription = Subscription(None, Service("ated", "ATED")),
      clientDisplayName = "client display name"
    )

  val cancelledMandate =
    Mandate(
      id = "123",
      createdBy = User("credid", "name", None),
      agentParty = Party(partyIDGen.sample.get, nameGen.sample.get, PartyType.Organisation, ContactDetails(emailGen.sample.get, telephoneNumberGen.sample)),
      clientParty = None,
      currentStatus = MandateStatus(Status.Cancelled, new DateTime(), "credid"),
      statusHistory = Nil,
      subscription = Subscription(None, Service("ated", "ATED")),
      clientDisplayName = "client display name"
    )

  val createMandateDto = CreateMandateDto(emailGen.sample.get, "ated", "client display name")
  val registeredAddressDetails = RegisteredAddressDetails("123 Fake Street", "Somewhere", None, None, None, "GB")
  val agentDetails: AgentDetails = AgentBuilder.buildAgentDetails

}

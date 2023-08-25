/*
 * Copyright 2023 HM Revenue & Customs
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

import java.time.Instant
import org.mockito.ArgumentMatchers._
import org.mockito.{ArgumentMatchers, MockitoSugar}
import org.scalatest.BeforeAndAfterEach
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks
import play.api.libs.json.{JsValue, Json}
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

class AgentControllerSpec extends PlaySpec with MockitoSugar with BeforeAndAfterEach with ScalaCheckPropertyChecks {

  implicit val hc: HeaderCarrier = HeaderCarrier()

  val mandates = Seq("AAAAAAA", "BBBBBB", "CCCCCC")

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
    val TestMandateController: AgentController = new AgentController(
      createServiceMock,
      updateServiceMock,
      relationshipServiceMock,
      agentDetailsServiceMock,
      auditConnectorMock,
      notificationServiceMock,
      fetchServiceMock,
      authConnectorMock,
      cc
    ) {
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
  val arn = "JARN123456"
  val service = "ated"

  private def mandateWithStatus(status: Status.Status): Mandate =
    Mandate(
      id = "123",
      createdBy = User("credid", "name", None),
      agentParty = Party(partyIDGen.sample.get, nameGen.sample.get, PartyType.Organisation, ContactDetails(emailGen.sample.get, telephoneNumberGen.sample)),
      clientParty = None,
      currentStatus = MandateStatus(status, Instant.now(), "credid"),
      statusHistory = Nil,
      subscription = Subscription(None, Service("ated", "ATED")),
      clientDisplayName = "client display name"
    )

  val newMandate: Mandate = mandateWithStatus(Status.New)
  val pendingActiveMandate: Mandate = mandateWithStatus(Status.PendingActivation)
  val approvedMandate: Mandate = mandateWithStatus(Status.Approved)
  val createMandateDto: CreateMandateDto = CreateMandateDto(emailGen.sample.get, "ated", "client display name")
  val agentDetails: AgentDetails = AgentBuilder.buildAgentDetails

  "AgentController" should {
    "return a NOT_FOUND " when {
      "an exception is thrown by updateMandate in 'activate'" in new Setup {
        when(fetchServiceMock.fetchClientMandate(ArgumentMatchers.eq(mandateId))(any())) thenReturn Future.successful(MandateFetched(approvedMandate))
        when(updateServiceMock.updateMandate(any(), any())(any())) thenReturn Future.failed(new RuntimeException("[AuthRetrieval] No GGCredId found."))

        val result: Future[Result] = TestMandateController.activate(agentCode, mandateId).apply(FakeRequest())
        status(result) mustBe NOT_FOUND
      }

      "an exception is thrown by createMandate in 'create'" in new Setup {
        when(createServiceMock.createMandate(any(), any())(any(), any())).thenReturn(Future.failed(new RuntimeException("[AuthRetrieval] No GGCredId found.")))

        val fakeRequest: FakeRequest[JsValue] = FakeRequest(
          method = "POST", uri = "", headers = FakeHeaders(Seq("Content-type" -> "application/json")), body = Json.toJson(createMandateDto))
        val result: Future[Result] = TestMandateController.create(agentCode).apply(fakeRequest)
        status(result) mustBe NOT_FOUND
      }

      "an exception is thrown by getAllMandates in 'fetchAll'" in new Setup {
        when(fetchServiceMock.getAllMandates(any(), any(), any(), any())(any(), any()))
          .thenReturn(Future.failed(new RuntimeException("[AuthRetrieval] No GGCredId found.")))

        val result: Future[Result] = TestMandateController.fetchAll(arn, service, None, None).apply(FakeRequest())
        status(result) mustBe NOT_FOUND
      }


      "an exception is thrown by getAgentDetails in 'getAgentDetails'" in new Setup {
        when(agentDetailsServiceMock.getAgentDetails(any(), any()))
          .thenReturn(Future.failed(new RuntimeException("[AuthRetrieval] No enrolment id found for AgentRefNumber.")))
        val result: Future[Result] = TestMandateController.getAgentDetails().apply(FakeRequest())
        status(result) mustBe NOT_FOUND
      }

      "an exception is thrown by createMandateForNonUKClient in 'createRelationship'" in new Setup {
        val dto: NonUKClientDto = NonUKClientDto(
          safeIDGen.sample.get,
          "atedRefNum", "ated",
          "clientEmail@email.com",
          agentReferenceNumberGen.sample.get,
          "agentEmail@email.com",
          "client display name")
        val fakeRequest: FakeRequest[JsValue] = FakeRequest(
          method = "POST", uri = "", headers = FakeHeaders(Seq("Content-type" -> "application/json")), body = Json.toJson(dto))
        when(createServiceMock.createMandateForNonUKClient(any(), ArgumentMatchers.eq(dto))(any(), any()))
          .thenReturn(Future.failed(new RuntimeException("[AuthRetrieval] No GGCredId found.")))
        val result: Future[Result] = TestMandateController.createRelationship("agentCode").apply(fakeRequest)
        status(result) mustBe NOT_FOUND
      }

      "an exception is thrown by updateMandate in 'agentRejectsClient'" in new Setup {
        when(fetchServiceMock.fetchClientMandate(ArgumentMatchers.eq(mandateId))(any())) thenReturn Future.successful(MandateFetched(newMandate))
        when(updateServiceMock.updateMandate(any(), any())(any())).thenReturn(Future.failed(new RuntimeException("[AuthRetrieval] No GGCredId found.")))

        val result: Future[Result] = TestMandateController.agentRejectsClient("", mandateId).apply(FakeRequest())
        status(result) mustBe NOT_FOUND
      }

      "an exception is thrown by updateMandate in 'editMandate'" in new Setup {
        when(updateServiceMock.updateMandate(any(), any())(any())).thenReturn(Future.failed(new RuntimeException("[AuthRetrieval] No GGCredId found.")))

        val fakeRequest: FakeRequest[JsValue] = FakeRequest(
          method = "POST", uri = "", headers = FakeHeaders(Seq("Content-type" -> "application/json")), body = Json.toJson(newMandate))
        val result: Future[Result] = TestMandateController.editMandate("agentCode").apply(fakeRequest)
        status(result) mustBe NOT_FOUND
      }

      "an exception is thrown by updateAgentCredId in 'updateAgentCredId'" in new Setup {
        when(updateServiceMock.updateAgentCredId(any())(any())).thenReturn(Future.failed(new RuntimeException("[AuthRetrieval] No GGCredId found.")))

        val fakeRequest: FakeRequest[JsValue] = FakeRequest(
          method = "POST", uri = "", headers = FakeHeaders(Seq("Content-type" -> "application/json")), body = Json.toJson("oldCredId"))
        val result: Future[Result] = TestMandateController.updateAgentCredId().apply(fakeRequest)
        status(result) mustBe NOT_FOUND
      }

      "an exception is thrown by updateMandateForNonUKClient in 'updateRelationship'" in new Setup {
        val dto: NonUKClientDto = NonUKClientDto(
          safeIDGen.sample.get,
          "atedRefNum", "ated",
          emailGen.sample.get,
          agentReferenceNumberGen.sample.get,
          emailGen.sample.get,
          "client display name",
          mandateReferenceGen.sample)
        when(createServiceMock.updateMandateForNonUKClient(any(), ArgumentMatchers.eq(dto))(any(), any()))
          .thenReturn(Future.failed(new RuntimeException("[AuthRetrieval] No GGCredId found.")))
        val fakeRequest: FakeRequest[JsValue] = FakeRequest(
          method = "POST", uri = "", headers = FakeHeaders(Seq("Content-type" -> "application/json")), body = Json.toJson(dto))
        val result: Future[Result] = TestMandateController.updateRelationship("agentCode").apply(fakeRequest)
        status(result) mustBe NOT_FOUND
      }
    }

    "activate the client" when {

      "request is valid and client mandate found " in new Setup {

        when(fetchServiceMock.fetchClientMandate(
          ArgumentMatchers.eq(mandateId))(any())) thenReturn Future.successful(MandateFetched(approvedMandate))
        when(updateServiceMock.updateMandate(
          ArgumentMatchers.eq(approvedMandate), any())(any())) thenReturn Future.successful(MandateUpdated(pendingActiveMandate))
        val result: Future[Result] = TestMandateController.activate(agentCode, mandateId).apply(FakeRequest())
        status(result) must be(OK)
      }
    }

    "not activate the client" when {

      "status of mandate returned is not Approved" in new Setup {
        forAll { mandateStatus: Status.Status =>
          whenever(mandateStatus != Status.Approved) {
            when(fetchServiceMock.fetchClientMandate(ArgumentMatchers.eq(mandateId))(any())) thenReturn
              Future.successful(MandateFetched(mandateWithStatus(mandateStatus)))

            val result: Future[Result] = TestMandateController.activate(agentCode, mandateId).apply(FakeRequest())

            status(result) must be(UNPROCESSABLE_ENTITY)
          }
        }
      }

      "cant find mandate while changing the status to PENDINGACTIVATION" in new Setup {
        when(fetchServiceMock.fetchClientMandate(ArgumentMatchers.eq(mandateId))(any())) thenReturn Future.successful(MandateFetched(approvedMandate))
        when(updateServiceMock.updateMandate(any(), any())(any())) thenReturn Future.successful(MandateUpdateError)
        when(notificationServiceMock.sendMail(any(), any(), any(), any(), any(), any(), any(), any())(any())) thenReturn Future.successful(EmailSent)

        val result: Future[Result] = TestMandateController.activate(agentCode, mandateId).apply(FakeRequest())

        status(result) must be(NOT_FOUND)
      }

      "cant find mandate" in new Setup {
        when(fetchServiceMock.fetchClientMandate(ArgumentMatchers.eq(mandateId))(any())) thenReturn Future.successful(MandateNotFound)

        val result: Future[Result] = TestMandateController.activate(agentCode, mandateId).apply(FakeRequest())

        status(result) must be(NOT_FOUND)
      }
    }

    "create a mandate and return mandate Id" when {

      "an agent request it and passes valid DTO" in new Setup {
        when(createServiceMock.createMandate(ArgumentMatchers.eq(agentCode), ArgumentMatchers.eq(createMandateDto))(any(), any()))
          .thenReturn(Future.successful(mandateId))
        val fakeRequest: FakeRequest[JsValue] = FakeRequest(
          method = "POST", uri = "", headers = FakeHeaders(Seq("Content-type" -> "application/json")), body = Json.toJson(createMandateDto))
        val result: Future[Result] = TestMandateController.create(agentCode).apply(fakeRequest)
        status(result) must be(CREATED)
        contentAsJson(result) must be(Json.parse("""{"mandateId":"123"}"""))
      }
    }

    "return bad-request" when {
      "invalid dto is passed by agent" in new Setup {
        val fakeRequest: FakeRequest[JsValue] = FakeRequest(
          method = "POST", uri = "", headers = FakeHeaders(Seq("Content-type" -> "application/json")), body = Json.parse("""{}"""))
        val result: Future[Result] = TestMandateController.create(agentCode).apply(fakeRequest)
        status(result) must be(BAD_REQUEST)
      }
    }
  }

  "fetch all mandates with respect to a service and ARN" when {
    "agent supplies valid service and ARN" in new Setup {
      when(fetchServiceMock.getAllMandates(ArgumentMatchers.eq(arn), ArgumentMatchers.eq(service), any(), any())(any(), any()))
        .thenReturn(Future.successful(Seq(newMandate)))
      val result: Future[Result] = TestMandateController.fetchAll(arn, service, None, None).apply(FakeRequest())
      status(result) must be(OK)
      contentAsJson(result) must be(Json.toJson(Seq(newMandate)))
    }
  }

  "return not-found when trying to fetch all mandates with respect to a service and ARN" when {
    "agent supplies invalid/non-existing service and ARN" in new Setup {
      when(fetchServiceMock.getAllMandates(ArgumentMatchers.eq(arn), ArgumentMatchers.eq(service), any(), any())(any(), any()))
        .thenReturn(Future.successful(Nil))
      val result: Future[Result] = TestMandateController.fetchAll(arn, service, None, None).apply(FakeRequest())
      status(result) must be(NOT_FOUND)
    }
  }

  "update mandate with pending cancellation status" when {
    "agent has rejected client and status returned ok" in new Setup {
      when(updateServiceMock.updateMandate(any(), any())(any())).thenReturn(Future.successful(MandateUpdated(newMandate)))
      when(fetchServiceMock.fetchClientMandate(ArgumentMatchers.eq(mandateId))(any())) thenReturn Future.successful(MandateFetched(newMandate))
      val result: Future[Result] = TestMandateController.agentRejectsClient("", mandateId).apply(FakeRequest())
      status(result) must be(OK)
    }

    "agent has rejected client but mandate cannot be updated" in new Setup {
      when(updateServiceMock.updateMandate(any(), any())(any())).thenReturn(Future.successful(MandateUpdateError))
      when(fetchServiceMock.fetchClientMandate(ArgumentMatchers.eq(mandateId))(any())) thenReturn Future.successful(MandateFetched(newMandate))
      val result: Future[Result] = TestMandateController.agentRejectsClient("", mandateId).apply(FakeRequest())
      status(result) must be(INTERNAL_SERVER_ERROR)
    }
  }

  "return NotFound" when {
    "agent has rejected client and mandate cannot be found" in new Setup {
      when(fetchServiceMock.fetchClientMandate(ArgumentMatchers.eq(mandateId))(any())) thenReturn Future.successful(MandateNotFound)
      val result: Future[Result] = TestMandateController.agentRejectsClient("", mandateId).apply(FakeRequest())
      status(result) must be(NOT_FOUND)
    }
  }

  "get agent details" when {
    "agent requests details" in new Setup {
      when(agentDetailsServiceMock.getAgentDetails(any(), any())).thenReturn(Future.successful(agentDetails))
      val result: Future[Result] = TestMandateController.getAgentDetails().apply(FakeRequest())
      status(result) must be(OK)
    }
  }

  "trying to create mandate for non-uk client by an agent" when {

    "return CREATED as status code, for successful creation" in new Setup {
      val dto: NonUKClientDto = NonUKClientDto(
        safeIDGen.sample.get, "atedRefNum", "ated", emailGen.sample.get, agentReferenceNumberGen.sample.get, emailGen.sample.get, "client display name")
      val fakeRequest: FakeRequest[JsValue] = FakeRequest(
        method = "POST", uri = "", headers = FakeHeaders(Seq("Content-type" -> "application/json")), body = Json.toJson(dto))
      when(createServiceMock.createMandateForNonUKClient(any(), ArgumentMatchers.eq(dto))(any(), any())).thenReturn(Future.unit)
      val result: Future[Result] = TestMandateController.createRelationship("agentCode").apply(fakeRequest)
      status(result) must be(CREATED)
      verify(createServiceMock, times(1)).createMandateForNonUKClient(any(), any())(any(), any())
    }
  }

  "trying to update mandate for non-uk client by an agent" when {

    "return CREATED as status code, for successful creation" in new Setup {
      val dto: NonUKClientDto = NonUKClientDto(
        safeIDGen.sample.get,
        "atedRefNum", "ated",
        emailGen.sample.get,
        agentReferenceNumberGen.sample.get,
        emailGen.sample.get,
        "client display name",
        mandateReferenceGen.sample)
      val fakeRequest: FakeRequest[JsValue] = FakeRequest(
        method = "POST", uri = "", headers = FakeHeaders(Seq("Content-type" -> "application/json")), body = Json.toJson(dto))
      when(createServiceMock.updateMandateForNonUKClient(any(), ArgumentMatchers.eq(dto))(any(), any())).thenReturn(Future.unit)
      val result: Future[Result] = TestMandateController.updateRelationship("agentCode").apply(fakeRequest)
      status(result) must be(CREATED)
      verify(createServiceMock, times(1)).updateMandateForNonUKClient(any(), any())(any(), any())
    }
  }


  "edit mandate details" must {
    "return OK, when mandate is updated in MongoDB" in new Setup {
      when(updateServiceMock.updateMandate(any(), any())(any())).thenReturn(Future.successful(MandateUpdated(newMandate)))
      val fakeRequest: FakeRequest[JsValue] = FakeRequest(
        method = "POST", uri = "", headers = FakeHeaders(Seq("Content-type" -> "application/json")), body = Json.toJson(newMandate))
      val result: Future[Result] = TestMandateController.editMandate("agentCode").apply(fakeRequest)
      status(result) must be(OK)
    }
    "return INTERNAL_SERVER_ERROR, when update fail in MongoDB" in new Setup {
      when(updateServiceMock.updateMandate(any(), any())(any())).thenReturn(Future.successful(MandateUpdateError))
      val fakeRequest: FakeRequest[JsValue] = FakeRequest(
        method = "POST", uri = "", headers = FakeHeaders(Seq("Content-type" -> "application/json")), body = Json.toJson(newMandate))
      val result: Future[Result] = TestMandateController.editMandate(agentCode).apply(fakeRequest)
      status(result) must be(INTERNAL_SERVER_ERROR)
    }
  }

  "isAgentMissingEmail" must {
    "return Found when mandates found" in new Setup {
      when(fetchServiceMock.getMandatesMissingAgentsEmails(any(), any())(any())) thenReturn Future.successful(mandates)
      val result: Future[Result] = TestMandateController.isAgentMissingEmail(arn, service).apply(FakeRequest())
      status(result) must be(OK)
    }

    "return Ok when no mandates found without email addresses" in new Setup {
      when(fetchServiceMock.getMandatesMissingAgentsEmails(any(), any())(any())) thenReturn Future.successful(Nil)
      val result: Future[Result] = TestMandateController.isAgentMissingEmail(arn, service).apply(FakeRequest())
      status(result) must be(NO_CONTENT)
    }
  }

  "updateAgentEmail" must {
    "return ok if agents email updated" in new Setup {
      when(updateServiceMock.updateAgentEmail(any(), any(), any())) thenReturn Future.successful(MandateUpdatedEmail)
      val fakeRequest: FakeRequest[JsValue] = FakeRequest(
        method = "POST", uri = "", headers = FakeHeaders(Seq("Content-type" -> "application/json")), body = Json.toJson("test@mail.com"))
      val result: Future[Result] = TestMandateController.updateAgentEmail(arn, service).apply(fakeRequest)
      status(result) must be(OK)
    }

    "return error if agents email not updated" in new Setup {
      when(updateServiceMock.updateAgentEmail(any(), any(), any())) thenReturn Future.successful(MandateUpdateError)
      val fakeRequest: FakeRequest[JsValue] = FakeRequest(
        method = "POST", uri = "", headers = FakeHeaders(Seq("Content-type" -> "application/json")), body = Json.toJson("test@mail.com"))
      val result: Future[Result] = TestMandateController.updateAgentEmail(arn, service).apply(fakeRequest)
      status(result) must be(INTERNAL_SERVER_ERROR)
    }

    "return bad request if no email address sent" in new Setup {
      val fakeRequest: FakeRequest[JsValue] = FakeRequest(
        method = "POST", uri = "", headers = FakeHeaders(Seq("Content-type" -> "application/json")), body = Json.toJson(""))
      val result: Future[Result] = TestMandateController.updateAgentEmail(arn, service).apply(fakeRequest)
      status(result) must be(BAD_REQUEST)
    }
  }

  "updateCredId" must {
    "return ok if credId updated" in new Setup {
      when(updateServiceMock.updateAgentCredId(any())(any())) thenReturn Future.successful(MandateUpdatedCredId)
      val fakeRequest: FakeRequest[JsValue] = FakeRequest(
        method = "POST", uri = "", headers = FakeHeaders(Seq("Content-type" -> "application/json")), body = Json.toJson("oldCredId"))
      val result: Future[Result] = TestMandateController.updateAgentCredId().apply(fakeRequest)
      status(result) must be(OK)
    }

    "return error if credId not updated" in new Setup {
      when(updateServiceMock.updateAgentCredId(any())(any())) thenReturn Future.successful(MandateUpdateError)
      val fakeRequest: FakeRequest[JsValue] = FakeRequest(
        method = "POST", uri = "", headers = FakeHeaders(Seq("Content-type" -> "application/json")), body = Json.toJson("oldCredId"))
      val result: Future[Result] = TestMandateController.updateAgentCredId().apply(fakeRequest)
      status(result) must be(INTERNAL_SERVER_ERROR)
    }

    "return bad request if no credId sent" in new Setup {
      val fakeRequest: FakeRequest[JsValue] = FakeRequest(
        method = "POST", uri = "", headers = FakeHeaders(Seq("Content-type" -> "application/json")), body = Json.toJson(""))
      val result: Future[Result] = TestMandateController.updateAgentCredId().apply(fakeRequest)
      status(result) must be(BAD_REQUEST)
    }
  }

  "getClientsThatCancelled" must {
    "return ok if mandates found and return client display names" in new Setup {
      when(fetchServiceMock.fetchClientCancelledMandates(any(), any())(any())) thenReturn Future.successful(List("AAA", "BBB"))
      val result: Future[Result] = TestMandateController.getClientsThatCancelled(arn, service).apply(FakeRequest())
      status(result) must be(OK)
      contentAsJson(result) must be(Json.toJson(Seq("AAA", "BBB")))
    }

    "return NotFound if no mandates returned" in new Setup {
      when(fetchServiceMock.fetchClientCancelledMandates(any(), any())(any())) thenReturn Future.successful(Nil)
      val result: Future[Result] = TestMandateController.getClientsThatCancelled(arn, service).apply(FakeRequest())
      status(result) must be(NOT_FOUND)
    }
  }


}

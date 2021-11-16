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
import org.mockito.ArgumentMatchers._
import org.mockito.{ArgumentMatchers, MockitoSugar}
import org.scalatest.BeforeAndAfterEach
import org.scalatestplus.play.PlaySpec
import play.api.libs.json.Json
import play.api.mvc.{ControllerComponents, Result}
import play.api.test.Helpers._
import play.api.test.{FakeHeaders, FakeRequest, Helpers}
import uk.gov.hmrc.agentclientmandate.auth.AuthRetrieval
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

class ClientControllerSpec extends PlaySpec with MockitoSugar with BeforeAndAfterEach {

  "ClientController" should {
    "return a NOT_FOUND " when {
      "an exception is thrown by approveMandate in 'approve'" in  new Setup {
        when(updateServiceMock.approveMandate(any())(any())).thenReturn(Future.failed(new RuntimeException("[AuthRetrieval] No enrolment id found for ATEDRefNumber.")))

        val fakeRequest = FakeRequest(method = "POST", uri = "", headers = FakeHeaders(Seq("Content-type" -> "application/json")), body = Json.toJson(newMandate))
        val result = TestMandateController.approve().apply(fakeRequest)
        status(result) mustBe NOT_FOUND
      }
    }

    "fetch a mandate" when {
      "mandate found when fetching by client and valid clientId" in new Setup {
        when(fetchServiceMock.fetchClientMandate(any(), any())(any())) thenReturn Future.successful(MandateFetched(newMandate))
        val result = TestMandateController.fetchByClient(clientId, service).apply(FakeRequest())
        status(result) must be(OK)
        contentAsJson(result) must be(Json.toJson(newMandate))
      }

      "mandate not found when fetching by client using invalid clientId" in new Setup {
        when(fetchServiceMock.fetchClientMandate(any(), any())(any())) thenReturn Future.successful(MandateNotFound)

        val result = TestMandateController.fetchByClient(clientId, service).apply(FakeRequest())
        status(result) must be(NOT_FOUND)
      }
    }

    "update mandate for a client" when {
      "client provided valid payload and mandate has been successfully updated in mongo" in new Setup {
        when(updateServiceMock.approveMandate(ArgumentMatchers.eq(newMandate))(any())).thenReturn(Future.successful(MandateUpdated(newMandate)))
        val fakeRequest = FakeRequest(method = "POST", uri = "", headers = FakeHeaders(Seq("Content-type" -> "application/json")), body = Json.toJson(newMandate))
        val result = TestMandateController.approve().apply(fakeRequest)
        status(result) must be(OK)
      }
    }

    "throw error while trying to update mandate for a client" when {
      "client provided valid payload but mandate wasn't successfully updated in mongo" in new Setup {
        when(updateServiceMock.approveMandate(ArgumentMatchers.eq(newMandate))(any())).thenReturn(Future.successful(MandateUpdateError))
        val fakeRequest = FakeRequest(method = "POST", uri = "", headers = FakeHeaders(Seq("Content-type" -> "application/json")), body = Json.toJson(newMandate))
        val result = TestMandateController.approve().apply(fakeRequest)
        status(result) must be(INTERNAL_SERVER_ERROR)
      }
    }

    "return bad-request while trying to update mandate for a client" when {
      "client provided invalid payload and" in new Setup {
        val fakeRequest = FakeRequest(method = "POST", uri = "", headers = FakeHeaders(Seq("Content-type" -> "application/json")), body = Json.parse("""{}"""))
        val result = TestMandateController.approve().apply(fakeRequest)
        status(result) must be(BAD_REQUEST)
      }
    }

    "updateClientEmail" must {
      "return ok if clients email updated" in new Setup {
        when(updateServiceMock.updateClientEmail(any(), any())) thenReturn Future.successful(MandateUpdatedEmail)
        val fakeRequest = FakeRequest(method = "POST", uri = "", headers = FakeHeaders(Seq("Content-type" -> "application/json")), body = Json.toJson("test@mail.com"))
        val result = TestMandateController.updateClientEmail(mandateId).apply(fakeRequest)
        status(result) must be(OK)
      }

      "return error if clients email not updated" in new Setup {
        when(updateServiceMock.updateClientEmail(any(), any())) thenReturn Future.successful(MandateUpdateError)
        val fakeRequest = FakeRequest(method = "POST", uri = "", headers = FakeHeaders(Seq("Content-type" -> "application/json")), body = Json.toJson("test@mail.com"))
        val result = TestMandateController.updateClientEmail(mandateId).apply(fakeRequest)
        status(result) must be(INTERNAL_SERVER_ERROR)
      }

      "return bad request if no email address sent" in new Setup {
        val fakeRequest = FakeRequest(method = "POST", uri = "", headers = FakeHeaders(Seq("Content-type" -> "application/json")), body = Json.toJson(""))
        val result = TestMandateController.updateClientEmail(mandateId).apply(fakeRequest)
        status(result) must be(BAD_REQUEST)
      }
    }
  }

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
    val TestMandateController: ClientController = new ClientController(
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
  val clientId = "XYZ"
  val service = "ated"

  val newMandate: Mandate =
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
}


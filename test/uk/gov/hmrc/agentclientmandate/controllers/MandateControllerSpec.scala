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

import org.joda.time.DateTime
import org.mockito.ArgumentMatchers._
import org.mockito.{ArgumentMatchers, MockitoSugar}
import org.scalatest.BeforeAndAfterEach
import org.scalatestplus.play.PlaySpec
import play.api.libs.json.Json
import play.api.mvc.{ControllerComponents, Result}
import play.api.test.Helpers._
import play.api.test.{FakeRequest, Helpers}
import uk.gov.hmrc.agentclientmandate.auth.AuthRetrieval
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

class MandateControllerSpec extends PlaySpec with MockitoSugar with BeforeAndAfterEach {

  "MandateController" should {
    "remove the mandate" when {

      "request is valid and client mandate found and status is active" in new Setup {
        when(fetchServiceMock.fetchClientMandate(ArgumentMatchers.eq(mandateId))(any())) thenReturn Future.successful(MandateFetched(activeMandate))
        when(updateServiceMock.updateMandate(any(), any())(any())) thenReturn Future.successful(MandateUpdated(newMandate))
        val result: Future[Result] = TestMandateController.remove(mandateId).apply(FakeRequest())
        status(result) must be(OK)
      }

      "request is valid and client mandate found and status is approved" in new Setup {
        when(notificationServiceMock.sendMail(any(), any(), any(), any(), any(), any(), any(), any())(any())) thenReturn Future.successful(EmailSent)
        when(fetchServiceMock.fetchClientMandate(ArgumentMatchers.eq(mandateId))(any())) thenReturn Future.successful(MandateFetched(approvedMandate))
        when(updateServiceMock.updateMandate(any(), any())(any())) thenReturn Future.successful(MandateUpdated(newMandate))
        val result: Future[Result] = TestMandateController.remove(mandateId).apply(FakeRequest())
        status(result) must be(OK)
      }

      "request is valid and client mandate found and status is New" in new Setup {
        when(fetchServiceMock.fetchClientMandate(ArgumentMatchers.eq(mandateId))(any())) thenReturn Future.successful(MandateFetched(newMandate))
        when(updateServiceMock.updateMandate(any(), any())(any())) thenReturn Future.successful(MandateUpdated(newMandate))
        val result: Future[Result] = TestMandateController.remove(mandateId).apply(FakeRequest())
        status(result) must be(OK)
      }
    }

    "cant remove the mandate" when {

      "mandate with no agent code is fetched" in new Setup {
        when(fetchServiceMock.fetchClientMandate(ArgumentMatchers.eq(mandateId))(any())) thenReturn Future.successful(MandateFetched(activeMandate1))

        val result: Future[Result] = TestMandateController.remove(mandateId).apply(FakeRequest())
        status(result) mustBe NOT_FOUND
      }

      "mongo update error occurs while changing the status to PENDING_CANCELLATION" in new Setup {
        when(fetchServiceMock.fetchClientMandate(ArgumentMatchers.eq(mandateId))(any())) thenReturn Future.successful(MandateFetched(activeMandate))
        when(updateServiceMock.updateMandate(any(), any())(any())) thenReturn Future.successful(MandateUpdateError)

        val result: Future[Result] = TestMandateController.remove(mandateId).apply(FakeRequest())

        status(result) must be(NOT_FOUND)
      }

      "mongo update error occurs while changing the status to CANCELLED" in new Setup {
        when(fetchServiceMock.fetchClientMandate(ArgumentMatchers.eq(mandateId))(any())) thenReturn Future.successful(MandateFetched(approvedMandate))
        when(updateServiceMock.updateMandate(any(), any())(any())) thenReturn Future.successful(MandateUpdateError)

        val result: Future[Result] = TestMandateController.remove(mandateId).apply(FakeRequest())

        status(result) must be(NOT_FOUND)
      }

      "mongo update error occurs while changing the New status to CANCELLED" in new Setup {
        when(fetchServiceMock.fetchClientMandate(ArgumentMatchers.eq(mandateId))(any())) thenReturn Future.successful(MandateFetched(newMandate))
        when(updateServiceMock.updateMandate(any(), any())(any())) thenReturn Future.successful(MandateUpdateError)

        val result: Future[Result] = TestMandateController.remove(mandateId).apply(FakeRequest())

        status(result) must be(NOT_FOUND)
      }

      "status of mandate returned is not ACTIVE" in new Setup {
        when(fetchServiceMock.fetchClientMandate(ArgumentMatchers.eq(mandateId))(any())) thenReturn Future.successful(MandateFetched(cancelledMandate))

        status(TestMandateController.remove(mandateId).apply(FakeRequest())) mustBe NOT_FOUND
      }

      "no mandate is fetched" in new Setup {
        when(fetchServiceMock.fetchClientMandate(ArgumentMatchers.eq(mandateId))(any())) thenReturn Future.successful(MandateNotFound)

        val result: Future[Result] = TestMandateController.remove(mandateId).apply(FakeRequest())

        status(result) must be(NOT_FOUND)
      }
    }

    "fetch a mandate" when {
      "a valid mandate id is passed" in new Setup {
        when(fetchServiceMock.fetchClientMandate(ArgumentMatchers.eq(mandateId))(any())) thenReturn Future.successful(MandateFetched(newMandate))
        val result: Future[Result] = TestMandateController.fetch(mandateId).apply(FakeRequest())
        status(result) must be(OK)
        contentAsJson(result) must be(Json.toJson(newMandate))
      }

      "return not found with invalid or non-existing mandateId is passed" in new Setup {
        when(fetchServiceMock.fetchClientMandate(ArgumentMatchers.eq(mandateId))(any())) thenReturn Future.successful(MandateNotFound)
        val result: Future[Result] = TestMandateController.fetch(mandateId).apply(FakeRequest())
        status(result) must be(NOT_FOUND)
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
    val TestMandateController: MandateController = new MandateController(
      createServiceMock,
      updateServiceMock,
      relationshipServiceMock,
      agentDetailsServiceMock,
      auditConnectorMock,
      notificationServiceMock,
      fetchServiceMock,
      authConnectorMock,
      cc
    ){
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

  val approvedMandate: Mandate =
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

  val activeMandate: Mandate =
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

  val activeMandate1: Mandate =
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

  val cancelledMandate: Mandate =
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
}

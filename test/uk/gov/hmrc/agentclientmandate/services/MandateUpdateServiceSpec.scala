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

package uk.gov.hmrc.agentclientmandate.services

import org.joda.time.{DateTime, DateTimeUtils}
import org.mockito.ArgumentMatchers
import org.mockito.ArgumentMatchers._
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterEach
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.PlaySpec
import play.api.libs.json.{JsValue, Json}
import play.api.test.Helpers._
import uk.gov.hmrc.agentclientmandate.auth.AuthRetrieval
import uk.gov.hmrc.agentclientmandate.connectors.EtmpConnector
import uk.gov.hmrc.agentclientmandate.models._
import uk.gov.hmrc.agentclientmandate.repositories._
import uk.gov.hmrc.agentclientmandate.utils.Generators._
import uk.gov.hmrc.agentclientmandate.utils.MockMetricsCache
import uk.gov.hmrc.auth.core.retrieve.{AgentInformation, Credentials}
import uk.gov.hmrc.auth.core.{Enrolment, EnrolmentIdentifier}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.audit.http.connector.AuditConnector

import scala.concurrent.Future

class MandateUpdateServiceSpec extends PlaySpec with BeforeAndAfterEach with MockitoSugar {

  implicit val hc: HeaderCarrier = HeaderCarrier()

  val mockMandateRepository: MandateRepository = mock[MandateRepository]
  val mockEtmpConnector: EtmpConnector = mock[EtmpConnector]
  val mockAuditConnector: AuditConnector = mock[AuditConnector]

  override def beforeEach: Unit = {
    reset(mockMandateRepository)
    reset(mockEtmpConnector)

    when(MockMetricsCache.mockMetrics.startTimer(any()))
      .thenReturn(null)
  }

  trait Setup {

    class TestMandateUpdateService extends MandateUpdateService {
      override val mandateRepository: MandateRepository = mockMandateRepository
      override val etmpConnector: EtmpConnector = mockEtmpConnector
      override val auditConnector: AuditConnector = mockAuditConnector
      override val expiryAfterDays: Int = 5
    }

    val service = new TestMandateUpdateService
  }

  implicit val testAuthRetrieval: AuthRetrieval = AuthRetrieval(
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

  "MandateUpdateService" should {

    "update data in mongo with given data provided" when {

      "requested to do so - updateMandate" in new Setup {
        when(mockMandateRepository.updateMandate(any())).thenReturn(Future.successful(MandateUpdated(clientApprovedMandate)))

        await(service.updateMandate(mandate, Some(Status.Approved))(testAuthRetrieval)) must be(MandateUpdated(clientApprovedMandate))
      }
    }

    "approveMandate" must {
      "change status of mandate to approve, if all calls are successful and service name is ated" in new Setup {
        DateTimeUtils.setCurrentMillisFixed(currentMillis)
        when(mockMandateRepository.fetchMandate(any())).thenReturn(Future.successful(MandateFetched(mandate)))
        when(mockEtmpConnector.getAtedSubscriptionDetails(ArgumentMatchers.eq("ated-ref-num"))).thenReturn(Future.successful(etmpSubscriptionJson))
        when(mockMandateRepository.updateMandate(any())).thenReturn(Future.successful(MandateUpdated(updatedMandate)))
        val result: MandateUpdate = await(service.approveMandate(clientApprovedMandate))
        result must be(MandateUpdated(updatedMandate))
      }

      "throw exception, if post was made without client party in it" in new Setup {
        DateTimeUtils.setCurrentMillisFixed(currentMillis)
        when(mockMandateRepository.fetchMandate(any())).thenReturn(Future.successful(MandateFetched(mandate)))
        when(mockEtmpConnector.getAtedSubscriptionDetails(ArgumentMatchers.eq("ated-ref-num"))).thenReturn(Future.successful(etmpSubscriptionJson))
        when(mockMandateRepository.updateMandate(any())).thenReturn(Future.successful(MandateUpdated(updatedMandate)))
        val thrown: RuntimeException = the[RuntimeException] thrownBy await(service.approveMandate(mandate))
        thrown.getMessage must be("Client party not found")
      }

      "throw exception, if used for any other service" in new Setup {
        val mandateToUse: Mandate = clientApprovedMandate.copy(subscription = clientApprovedMandate.subscription.copy(service = Service("other", "other")))
        val thrown: RuntimeException = the[RuntimeException] thrownBy await(service.approveMandate(mandateToUse))
        thrown.getMessage must be("currently supported only for ATED")
      }

      "throw exception if no mandate is fetched" in new Setup {
        when(mockMandateRepository.fetchMandate(any())).thenReturn(Future.successful(MandateNotFound))
        val thrown: RuntimeException = the[RuntimeException] thrownBy await(service.approveMandate(mandate))
        thrown.getMessage must startWith("mandate not found for mandate id")

      }
    }

    "updateStatus" must {
      "change mandate status and send email for client" in new Setup {
        when(mockMandateRepository.updateMandate(any())).thenReturn(Future.successful(MandateUpdated(updatedMandate)))

        val result: MandateUpdate = await(service.updateMandate(updatedMandate, Some(Status.PendingCancellation)))
        result must be(MandateUpdated(updatedMandate))
      }

      "change mandate status and send email for agent" in new Setup {
        when(mockMandateRepository.updateMandate(any())).thenReturn(Future.successful(MandateUpdated(updatedMandate)))

        val result: MandateUpdate = await(service.updateMandate(updatedMandate, Some(Status.PendingCancellation)))
        result must be(MandateUpdated(updatedMandate))
      }
    }

    "updateAgentEmail" must {
      "update all mandates with email for agent" in new Setup {
        when(mockMandateRepository.findMandatesMissingAgentEmail(any(), any())) thenReturn Future.successful(mandateIds)
        when(mockMandateRepository.updateAgentEmail(any(), any())) thenReturn Future.successful(MandateUpdatedEmail)
        val result: MandateUpdate = await(service.updateAgentEmail("agentId", emailGen.sample.get, "ated"))
        result must be(MandateUpdatedEmail)
      }
    }

    "updateClientEmail" must {
      "update the mandate with email for client" in new Setup {
        when(mockMandateRepository.updateClientEmail(any(), any())) thenReturn Future.successful(MandateUpdatedEmail)
        val result: MandateUpdate = await(service.updateClientEmail("mandateId", emailGen.sample.get))
        result must be(MandateUpdatedEmail)
      }
    }

    "updateAgentCredId" must {
      "update the mandate with the proper cred id" in new Setup {
        when(mockMandateRepository.updateAgentCredId(any(), any())) thenReturn Future.successful(MandateUpdatedCredId)
        val result: MandateUpdate = await(service.updateAgentCredId("credId"))
        result must be(MandateUpdatedCredId)
      }
    }

    "checkExpiry" must {
      "get expired mandate list and update all to be expired" in new Setup {
        when(mockMandateRepository.findOldMandates(any())).thenReturn(Future.successful(List(mandate)))
        when(mockMandateRepository.updateMandate(any())).thenReturn(Future.successful(MandateUpdated(mandate)))
        await(service.checkStaleDocuments())
        verify(mockMandateRepository, times(1)).updateMandate(any())
      }

      "get expired mandate list but fail when updating mandate" in new Setup {
        when(mockMandateRepository.findOldMandates(any())).thenReturn(Future.successful(List(mandate)))
        when(mockMandateRepository.updateMandate(any())).thenReturn(Future.successful(MandateUpdateError))
        await(service.checkStaleDocuments())
        verify(mockMandateRepository, times(1)).updateMandate(any())
      }
    }
  }

  val timeToUse: DateTime = DateTime.now()
  val currentMillis: Long = timeToUse.getMillis

  val mandate = Mandate(mandateReferenceGen.sample.get,
    User("credid", nameGen.sample.get, None),
    agentParty = Party(partyIDGen.sample.get, nameGen.sample.get, PartyType.Organisation, ContactDetails("", Some(""))),
    currentStatus = MandateStatus(Status.New, timeToUse, "credid"),
    subscription = Subscription(None, Service("ated", "ATED")),
    clientDisplayName = "client display name"
  )

  val clientApprovedMandate = Mandate(mandateReferenceGen.sample.get,
    User("credid", nameGen.sample.get, None),
    agentParty = Party(partyIDGen.sample.get, nameGen.sample.get, PartyType.Organisation, ContactDetails("", Some(""))),
    clientParty = Some(Party("", "", PartyType.Organisation, ContactDetails(emailGen.sample.get))),
    currentStatus = MandateStatus(Status.Approved, timeToUse, ""),
    subscription = Subscription(None, Service("ated", "ATED")),
    clientDisplayName = "client display name"
  )

  val updatedMandate = Mandate(mandateReferenceGen.sample.get,
    User("credid", nameGen.sample.get, None),
    agentParty = Party(partyIDGen.sample.get, nameGen.sample.get, PartyType.Organisation, ContactDetails("", Some(""))),
    clientParty = Some(Party("safe-id", "client-name", PartyType.Organisation, ContactDetails(emailGen.sample.get))),
    currentStatus = MandateStatus(Status.Approved, timeToUse, "credid"),
    subscription = Subscription(Some("ated-ref-no"), Service("ated", "ATED")),
    clientDisplayName = "client display name"
  )

  val etmpSubscriptionJson: JsValue = Json.parse(
    """
      |{
      |  "safeId": "cred-id-1234567890",
      |  "organisationName": "client-name"
      |}
    """.stripMargin
  )

  val mandateIds: Seq[String] = Seq(mandate.id, clientApprovedMandate.id, updatedMandate.id)

}

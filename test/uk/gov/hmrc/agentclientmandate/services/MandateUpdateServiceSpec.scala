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

import org.joda.time.{DateTime, DateTimeUtils}
import org.mockito.ArgumentMatchers
import org.mockito.ArgumentMatchers._
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterEach
import org.scalatest.mockito.MockitoSugar
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.libs.json.Json
import play.api.test.Helpers._
import uk.gov.hmrc.agentclientmandate.connectors.{AuthorityConnector, EtmpConnector}
import uk.gov.hmrc.agentclientmandate.models._
import uk.gov.hmrc.agentclientmandate.repositories._
import uk.gov.hmrc.agentclientmandate.utils.Generators._
import uk.gov.hmrc.agentclientmandate.utils.MockMetricsCache
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.audit.http.connector.AuditConnector

import scala.concurrent.Future

class MandateUpdateServiceSpec extends PlaySpec with GuiceOneServerPerSuite with BeforeAndAfterEach with MockitoSugar {

  implicit val hc = HeaderCarrier()

  val mockMandateRepository = mock[MandateRepository]
  val mockEtmpConnector = mock[EtmpConnector]
  val mockAuthConnector = mock[AuthorityConnector]
  val mockAuditConnector = mock[AuditConnector]

  override def beforeEach: Unit = {
    reset(mockMandateRepository)
    reset(mockEtmpConnector)
    reset(mockAuthConnector)

    when(MockMetricsCache.mockMetrics.startTimer(any()))
      .thenReturn(null)
  }

  trait Setup {

    class TestMandateUpdateService extends MandateUpdateService {
      override val mandateRepository = mockMandateRepository
      override val etmpConnector = mockEtmpConnector
      override val authConnector = mockAuthConnector
      override val auditConnector: AuditConnector = mockAuditConnector
      override val expiryAfterDays: Int = 5
    }

    val service = new TestMandateUpdateService
  }

  "MandateUpdateService" should {

    "update data in mongo with given data provided" when {

      "requested to do so - updateMandate" in new Setup {
        when(mockAuthConnector.getAuthority()(any())).thenReturn(Future.successful(authJson))
        when(mockMandateRepository.updateMandate(any())).thenReturn(Future.successful(MandateUpdated(clientApprovedMandate)))

        await(service.updateMandate(mandate, Some(Status.Approved))(new HeaderCarrier())) must be(MandateUpdated(clientApprovedMandate))
      }
    }

    "approveMandate" must {
      "change status of mandate to approve, if all calls are successful and service name is ated" in new Setup {
        DateTimeUtils.setCurrentMillisFixed(currentMillis)
        when(mockMandateRepository.fetchMandate(any())).thenReturn(Future.successful(MandateFetched(mandate)))
        when(mockAuthConnector.getAuthority()(any())).thenReturn(Future.successful(authJson))
        when(mockEtmpConnector.getAtedSubscriptionDetails(ArgumentMatchers.eq("ated-ref-num"))).thenReturn(Future.successful(etmpSubscriptionJson))
        when(mockMandateRepository.updateMandate(any())).thenReturn(Future.successful(MandateUpdated(updatedMandate)))
        val result = await(service.approveMandate(clientApprovedMandate))
        result must be(MandateUpdated(updatedMandate))
      }

      "throw exception, if post was made without client party in it" in new Setup {
        DateTimeUtils.setCurrentMillisFixed(currentMillis)
        when(mockMandateRepository.fetchMandate(any())).thenReturn(Future.successful(MandateFetched(mandate)))
        when(mockAuthConnector.getAuthority()(any())).thenReturn(Future.successful(authJson))
        when(mockEtmpConnector.getAtedSubscriptionDetails(ArgumentMatchers.eq("ated-ref-num"))).thenReturn(Future.successful(etmpSubscriptionJson))
        when(mockMandateRepository.updateMandate(any())).thenReturn(Future.successful(MandateUpdated(updatedMandate)))
        val thrown = the[RuntimeException] thrownBy await(service.approveMandate(mandate))
        thrown.getMessage must be("Client party not found")
      }

      "throw exception, if used for any other service" in new Setup {
        val mandateToUse = clientApprovedMandate.copy(subscription = clientApprovedMandate.subscription.copy(service = Service("other", "other")))
        val thrown = the[RuntimeException] thrownBy await(service.approveMandate(mandateToUse))
        thrown.getMessage must be("currently supported only for ATED")
      }

      "throw exception if no mandate is fetched" in new Setup {
        when(mockMandateRepository.fetchMandate(any())).thenReturn(Future.successful(MandateNotFound))
        val thrown = the[RuntimeException] thrownBy await(service.approveMandate(mandate))
        thrown.getMessage must startWith("mandate not found for mandate id")

      }
    }

    "updateStatus" must {
      "change mandate status and send email for client" in new Setup {
        when(mockAuthConnector.getAuthority()(any())).thenReturn(Future.successful(authJson))
        when(mockMandateRepository.updateMandate(any())).thenReturn(Future.successful(MandateUpdated(updatedMandate)))

        val result = await(service.updateMandate(updatedMandate, Some(Status.PendingCancellation)))
        result must be(MandateUpdated(updatedMandate))
      }

      "change mandate status and send email for agent" in new Setup {
        when(mockAuthConnector.getAuthority()(any())).thenReturn(Future.successful(authJson1))
        when(mockMandateRepository.updateMandate(any())).thenReturn(Future.successful(MandateUpdated(updatedMandate)))

        val result = await(service.updateMandate(updatedMandate, Some(Status.PendingCancellation)))
        result must be(MandateUpdated(updatedMandate))
      }
    }

    "updateAgentEmail" must {
      "update all mandates with email for agent" in new Setup {
        when(mockMandateRepository.findMandatesMissingAgentEmail(any(), any())) thenReturn Future.successful(mandateIds)
        when(mockMandateRepository.updateAgentEmail(any(), any())) thenReturn Future.successful(MandateUpdatedEmail)
        val result = await(service.updateAgentEmail("agentId", emailGen.sample.get, "ated"))
        result must be(MandateUpdatedEmail)
      }
    }

    "updateClientEmail" must {
      "update the mandate with email for client" in new Setup {
        when(mockMandateRepository.updateClientEmail(any(), any())) thenReturn Future.successful(MandateUpdatedEmail)
        val result = await(service.updateClientEmail("mandateId", emailGen.sample.get))
        result must be(MandateUpdatedEmail)
      }
    }

    "updateAgentCredId" must {
      "update the mandate with the proper cred id" in new Setup {
        when(mockAuthConnector.getAuthority()(any())).thenReturn(Future.successful(authJson1))
        when(mockMandateRepository.updateAgentCredId(any(), any())) thenReturn Future.successful(MandateUpdatedCredId)
        val result = await(service.updateAgentCredId("credId"))
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

  val timeToUse = DateTime.now()
  val currentMillis = timeToUse.getMillis

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

  val authJson = Json.parse(
    """
      |{
      |  "credentials": {
      |    "gatewayId": "cred-id-1234567890"
      |  },
      |  "accounts": {
      |    "ated": {
      |      "utr": "ated-ref-num",
      |      "link": "/link"
      |    }
      |  }
      |}
    """.stripMargin
  )

  val authJson1 = Json.parse(
    s"""
       |{
       |  "credentials": {
       |    "gatewayId": "cred-id-1234567890"
       |  },
       |  "accounts": {
       |    "agent": {
       |      "agentBusinessUtr": "${agentBusinessUtrGen.sample.get}",
       |      "link": "/link"
       |    }
       |  }
       |}
    """.stripMargin
  )

  val authJson2 = Json.parse(
    """
      |{
      |  "credentials": {
      |    "gatewayId": "cred-id-1234567890"
      |  },
      |  "accounts": {
      |    "awrs": {
      |      "utr": "ated-ref-num",
      |      "link": "/link"
      |    }
      |  }
      |}
    """.stripMargin
  )

  val etmpSubscriptionJson = Json.parse(
    """
      |{
      |  "safeId": "cred-id-1234567890",
      |  "organisationName": "client-name"
      |}
    """.stripMargin
  )

  val mandateIds = Seq(mandate.id, clientApprovedMandate.id, updatedMandate.id)


}

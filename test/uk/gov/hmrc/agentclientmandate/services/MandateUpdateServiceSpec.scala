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

import org.joda.time.{DateTime, DateTimeUtils}
import org.mockito.Matchers
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterEach
import org.scalatest.mock.MockitoSugar
import org.scalatestplus.play.{OneServerPerSuite, PlaySpec}
import play.api.libs.json.Json
import play.api.test.Helpers._
import uk.gov.hmrc.agentclientmandate.connectors.{AuthConnector, EtmpConnector}
import uk.gov.hmrc.agentclientmandate.models._
import uk.gov.hmrc.agentclientmandate.repositories._
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.Future

class MandateUpdateServiceSpec extends PlaySpec with OneServerPerSuite with BeforeAndAfterEach with MockitoSugar {

  implicit val hc = HeaderCarrier()

  "MandateUpdateService" should {

    "update data in mongo with given data provided" when {

      "requested to do so - updateMandate" in {
        when(mockAuthConnector.getAuthority()(Matchers.any())).thenReturn(Future.successful(authJson))
        when(mockMandateRepository.updateMandate(Matchers.any())).thenReturn(Future.successful(MandateUpdated(clientApprovedMandate)))

        await(TestMandateUpdateService.updateMandate(mandate, Some(Status.Approved))(new HeaderCarrier())) must be(MandateUpdated(clientApprovedMandate))
      }
    }


    "approveMandate" must {
      "change status of mandate to approve, if all calls are successful and service name is ated" in {
        DateTimeUtils.setCurrentMillisFixed(currentMillis)
        when(mockMandateRepository.fetchMandate(Matchers.any())).thenReturn(Future.successful(MandateFetched(mandate)))
        when(mockAuthConnector.getAuthority()(Matchers.any())).thenReturn(Future.successful(authJson))
        when(mockEtmpConnector.getAtedSubscriptionDetails(Matchers.eq("ated-ref-num"))).thenReturn(Future.successful(etmpSubscriptionJson))
        when(mockMandateRepository.updateMandate(Matchers.any())).thenReturn(Future.successful(MandateUpdated(updatedMandate)))
        val result = await(TestMandateUpdateService.approveMandate(clientApprovedMandate))
        result must be(MandateUpdated(updatedMandate))
      }

      "throw exception, if post was made without client party in it" in {
        DateTimeUtils.setCurrentMillisFixed(currentMillis)
        when(mockMandateRepository.fetchMandate(Matchers.any())).thenReturn(Future.successful(MandateFetched(mandate)))
        when(mockAuthConnector.getAuthority()(Matchers.any())).thenReturn(Future.successful(authJson))
        when(mockEtmpConnector.getAtedSubscriptionDetails(Matchers.eq("ated-ref-num"))).thenReturn(Future.successful(etmpSubscriptionJson))
        when(mockMandateRepository.updateMandate(Matchers.any())).thenReturn(Future.successful(MandateUpdated(updatedMandate)))
        val thrown = the[RuntimeException] thrownBy await(TestMandateUpdateService.approveMandate(mandate))
        thrown.getMessage must be("Client party not found")
      }

      "throw exception, if used for any other service" in {
        val mandateToUse = clientApprovedMandate.copy(subscription = clientApprovedMandate.subscription.copy(service = Service("other", "other")))
        val thrown = the[RuntimeException] thrownBy await(TestMandateUpdateService.approveMandate(mandateToUse))
        thrown.getMessage must be("currently supported only for ATED")
      }

      "throw exception if no mandate is fetched" in {
        when(mockMandateRepository.fetchMandate(Matchers.any())).thenReturn(Future.successful(MandateNotFound))
        val thrown = the[RuntimeException] thrownBy await(TestMandateUpdateService.approveMandate(mandate))
        thrown.getMessage must be("mandate not found for mandate id::AS12345678")

      }
    }

    "updateStatus" must {
      "change mandate status and send email for client" in {
        when(mockAuthConnector.getAuthority()(Matchers.any())).thenReturn(Future.successful(authJson))
        when(mockMandateRepository.updateMandate(Matchers.any())).thenReturn(Future.successful(MandateUpdated(updatedMandate)))

        val result = await(TestMandateUpdateService.updateMandate(updatedMandate, Some(Status.PendingCancellation)))
        result must be(MandateUpdated(updatedMandate))
      }

      "change mandate status and send email for agent" in {
        when(mockAuthConnector.getAuthority()(Matchers.any())).thenReturn(Future.successful(authJson1))
        when(mockMandateRepository.updateMandate(Matchers.any())).thenReturn(Future.successful(MandateUpdated(updatedMandate)))

        val result = await(TestMandateUpdateService.updateMandate(updatedMandate, Some(Status.PendingCancellation)))
        result must be(MandateUpdated(updatedMandate))
      }
    }

    "updateAgentEmail" must {
      "update all mandates with email for agent" in {
        when(mockMandateRepository.findMandatesMissingAgentEmail(Matchers.any(), Matchers.any())) thenReturn Future.successful(mandateIds)
        when(mockMandateRepository.updateAgentEmail(Matchers.any(), Matchers.any())) thenReturn Future.successful(MandateUpdatedEmail)
        val result = await(TestMandateUpdateService.updateAgentEmail("agentId", "test@mail.com", "ated"))
        result must be(MandateUpdatedEmail)
      }
    }

    "updateClientEmail" must {
      "update the mandate with email for client" in {
        when(mockMandateRepository.updateClientEmail(Matchers.any(), Matchers.any())) thenReturn Future.successful(MandateUpdatedEmail)
        val result = await(TestMandateUpdateService.updateClientEmail("mandateId", "test@mail.com"))
        result must be(MandateUpdatedEmail)
      }
    }

    "updateAgentCredId" must {
      "update the mandate with the proper cred id" in {
        when(mockAuthConnector.getAuthority()(Matchers.any())).thenReturn(Future.successful(authJson1))
        when(mockMandateRepository.updateAgentCredId(Matchers.any(), Matchers.any())) thenReturn Future.successful(MandateUpdatedCredId)
        val result = await(TestMandateUpdateService.updateAgentCredId("credId"))
        result must be(MandateUpdatedCredId)
      }
    }

    "checkExpiry" must {
      "get expired mandate list and update all to be expired" in {
        when(mockMandateRepository.findOldMandates(Matchers.any())).thenReturn(Future.successful(List(mandate)))
        TestMandateUpdateService.checkExpiry()
        verify(mockMandateRepository, times(1)).updateMandate(Matchers.any())
      }
    }

  }

  val timeToUse = DateTime.now()
  val currentMillis = timeToUse.getMillis

  val mandate = Mandate("AS12345678",
    User("credid", "Joe Bloggs", None),
    agentParty = Party("JARN123456", "Joe Bloggs", PartyType.Organisation, ContactDetails("", Some(""))),
    currentStatus = MandateStatus(Status.New, timeToUse, "credid"),
    subscription = Subscription(None, Service("ated", "ATED")),
    clientDisplayName = "client display name"
  )

  val clientApprovedMandate = Mandate("AS12345678",
    User("credid", "Joe Bloggs", None),
    agentParty = Party("JARN123456", "Joe Bloggs", PartyType.Organisation, ContactDetails("", Some(""))),
    clientParty = Some(Party("", "", PartyType.Organisation, ContactDetails("client@mail.com"))),
    currentStatus = MandateStatus(Status.Approved, timeToUse, ""),
    subscription = Subscription(None, Service("ated", "ATED")),
    clientDisplayName = "client display name"
  )

  val updatedMandate = Mandate("AS12345678",
    User("credid", "Joe Bloggs", None),
    agentParty = Party("JARN123456", "Joe Bloggs", PartyType.Organisation, ContactDetails("", Some(""))),
    clientParty = Some(Party("safe-id", "client-name", PartyType.Organisation, ContactDetails("client@mail.com"))),
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
    """
      |{
      |  "credentials": {
      |    "gatewayId": "cred-id-1234567890"
      |  },
      |  "accounts": {
      |    "agent": {
      |      "agentBusinessUtr": "JARN1234567",
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

  val mockMandateRepository = mock[MandateRepository]
  val mockEtmpConnector = mock[EtmpConnector]
  val mockAuthConnector = mock[AuthConnector]

  object TestMandateUpdateService extends MandateUpdateService {
    override val mandateRepository = mockMandateRepository
    override val etmpConnector = mockEtmpConnector
    override val authConnector = mockAuthConnector
  }

  override def beforeEach: Unit = {
    reset(mockMandateRepository)
    reset(mockEtmpConnector)
    reset(mockAuthConnector)
  }



}

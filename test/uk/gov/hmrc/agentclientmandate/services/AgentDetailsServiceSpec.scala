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

import org.joda.time.DateTime
import org.mockito.Matchers
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterEach
import org.scalatest.mock.MockitoSugar
import org.scalatestplus.play.{OneServerPerSuite, PlaySpec}
import play.api.libs.json.Json
import uk.gov.hmrc.agentclientmandate.connectors.{AuthConnector, EtmpConnector}
import play.api.test.Helpers._
import uk.gov.hmrc.agentclientmandate.models._
import uk.gov.hmrc.domain.{AtedUtr, Generator}
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.Future




class AgentDetailsServiceSpec extends PlaySpec with OneServerPerSuite with MockitoSugar with BeforeAndAfterEach {

  val successResponseJsonAuth = Json.parse(
    """{
               "credentials": {
                 "gatewayId": "cred-id-113244018119",
                 "idaPids": []
               },
               "accounts": {
                 "agent": {
                   "agentCode":"AGENT-123", "agentBusinessUtr":"JARN1234567"
                 }
               }
             }""")

  "AgentDetailsService" must {

    "get agent details for individual" in {

      val successResponseJsonETMP = Json.parse(
        """
      {
          |  "sapNumber":"1234567890", "safeId": "EX0012345678909",
          |  "agentReferenceNumber": "AARN1234567",
          |  "isAnIndividual": true,
          |  "isAnAgent": true,
          |  "isEditable": true,
          |  "individual": {
          |    "firstName": "Jon",
          |    "lastName": "Snow",
          |    "dateOfBirth": "1962-10-12"
          |  },
          |  "addressDetails": {
          |    "addressLine1": "Melbourne House",
          |    "addressLine2": "Eastgate",
          |    "addressLine3": "Accrington",
          |    "addressLine4": "Lancashire",
          |    "postalCode": "BB5 6PU",
          |    "countryCode": "GB"
          |  },
          |  "contactDetails" : {}
          |}
        """.stripMargin
      )

      when(authConnectorMock.getAuthority()(Matchers.any())) thenReturn {
        Future.successful(successResponseJsonAuth)
      }

      when(etmpConnectorMock.getRegistrationDetails(Matchers.any(), Matchers.eq("arn"))) thenReturn {
        Future.successful(successResponseJsonETMP)
      }

      implicit val hc = new HeaderCarrier()
      val result = await(TestAgentDetailsService.getAgentDetails("ac"))
      result.agentName must be("Jon Snow")
    }

    "get agent details for organisation" in {
      val successResponseJsonETMP = Json.parse(
        """
      {
          |  "sapNumber":"1234567890", "safeId": "EX0012345678909",
          |  "agentReferenceNumber": "AARN1234567",
          |  "isAnIndividual": false,
          |  "isAnAgent": true,
          |  "isEditable": true,
             "organisation": {
          |    "organisationName": "ABC Limited"
          |  },
          |  "addressDetails": {
          |    "addressLine1": "Melbourne House",
          |    "addressLine2": "Eastgate",
          |    "addressLine3": "Accrington",
          |    "addressLine4": "Lancashire",
          |    "postalCode": "BB5 6PU",
          |    "countryCode": "GB"
          |  },
          |  "contactDetails" : {}
          |}
        """.stripMargin
      )

      when(authConnectorMock.getAuthority()(Matchers.any())) thenReturn {
        Future.successful(successResponseJsonAuth)
      }

      when(etmpConnectorMock.getRegistrationDetails(Matchers.any(), Matchers.eq("arn"))) thenReturn {
        Future.successful(successResponseJsonETMP)
      }

      implicit val hc = new HeaderCarrier()
      val result = await(TestAgentDetailsService.getAgentDetails("ac"))
      result.agentName must be("ABC Limited")
    }

    "returns true - for delegation authorization check for Ated" when {
      "fetched mandates have a mandate with the ATED ref number passed as subscription service reference number" in {
        when(authConnectorMock.getAuthority()(Matchers.any())).thenReturn(Future.successful(successResponseJsonAuth))
        when(mockMandateFetchService.getAllMandates(Matchers.any(), Matchers.eq("ated"))).thenReturn(Future.successful(Seq(mandate)))
        await(TestAgentDetailsService.isAuthorisedForAted(atedUtr)) must be(true)
      }
    }

    "returns false - for delegation authorization check for Ated" when {
      "authority doesn't return registered Agents" in {
        when(authConnectorMock.getAuthority()(Matchers.any())).thenReturn(Future.successful(notRegisteredAgentJsonAuth))
        await(TestAgentDetailsService.isAuthorisedForAted(atedUtr)) must be(false)
      }
      "mandate subscription doesn't have subscription reference" in {
        val mandateToUse = mandate.copy(subscription = mandate.subscription.copy(referenceNumber = None))
        when(authConnectorMock.getAuthority()(Matchers.any())).thenReturn(Future.successful(successResponseJsonAuth))
        when(mockMandateFetchService.getAllMandates(Matchers.any(), Matchers.eq("ated"))).thenReturn(Future.successful(Seq(mandateToUse)))
        await(TestAgentDetailsService.isAuthorisedForAted(atedUtr)) must be(false)
      }
      "mandate doesn't have the same AtedRefNumber" in {
        val mandateToUse = mandate.copy(subscription = mandate.subscription.copy(referenceNumber = Some(atedUtr2.utr)))
        when(authConnectorMock.getAuthority()(Matchers.any())).thenReturn(Future.successful(successResponseJsonAuth))
        when(mockMandateFetchService.getAllMandates(Matchers.any(), Matchers.eq("ated"))).thenReturn(Future.successful(Seq(mandateToUse)))
        await(TestAgentDetailsService.isAuthorisedForAted(atedUtr)) must be(false)
      }
    }
  }

  val atedUtr: AtedUtr = new Generator().nextAtedUtr
  val atedUtr2: AtedUtr = new Generator().nextAtedUtr

  val mandate =
    Mandate(
      id = "123",
      createdBy = User("credid", "name", None),
      agentParty = Party("JARN123456", "Joe Bloggs", PartyType.Organisation, ContactDetails("test@test.com", Some("0123456789"))),
      clientParty = Some(Party("ABCD1234", "Client Name", PartyType.Organisation, ContactDetails("somewhere@someplace.com", Some("98765433210")))),
      currentStatus = MandateStatus(Status.New, new DateTime(), "credid"),
      statusHistory = Nil,
      subscription = Subscription(Some(atedUtr.utr), Service("ated", "ATED")),
      clientDisplayName = "client display name"
    )

  val notRegisteredAgentJsonAuth = Json.parse(
    """
      {
        "accounts": {
          "agent": {
            "agentCode":"AGENT-123"
          }
        }
      }
    """
  )

  implicit val hc = new HeaderCarrier()

  val authConnectorMock = mock[AuthConnector]
  val etmpConnectorMock = mock[EtmpConnector]
  val mockMandateFetchService = mock[MandateFetchService]

  override def beforeEach(): Unit = {
    reset(authConnectorMock)
    reset(etmpConnectorMock)
    reset(mockMandateFetchService)
  }

  object TestAgentDetailsService extends AgentDetailsService {
    override val authConnector = authConnectorMock
    override val etmpConnector = etmpConnectorMock
    override val mandateFetchService: MandateFetchService = mockMandateFetchService
  }
}

/*
 * Copyright 2018 HM Revenue & Customs
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
import play.api.test.Helpers._
import uk.gov.hmrc.agentclientmandate.connectors.{AuthConnector, EtmpConnector}
import uk.gov.hmrc.agentclientmandate.models._
import uk.gov.hmrc.agentclientmandate.repositories._
import uk.gov.hmrc.agentclientmandate.utils.TestAudit
import uk.gov.hmrc.play.audit.model.Audit

import scala.concurrent.Future
import uk.gov.hmrc.http.HeaderCarrier

class MandateCreateServiceSpec extends PlaySpec with OneServerPerSuite with MockitoSugar with BeforeAndAfterEach {

  "MandateCreateService" should {

    "create a client mandate status object with a status of pending for new client mandates" in {

      val mandateStatus = TestClientMandateCreateService.createNewStatus("credid")
      mandateStatus must be(MandateStatus(Status.New, mandateStatus.timestamp, "credid"))

    }

    "createMandate" when {

      "a Mandate is created" in {

        val mandateId = TestClientMandateCreateService.createMandateId
        val successResponseJsonETMP = Json.parse(
          """
            |{
            |  "sapNumber":"1234567890",
            |  "safeId": "EX0012345678909",
            |  "agentReferenceNumber": "AARN1234567",
            |  "isAnIndividual": false,
            |  "organisation": {
            |    "organisationName": "ABC Limited"
            |  }
            |}
          """.stripMargin
        )
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

        when(mandateRepositoryMock.insertMandate(Matchers.any())) thenReturn {
          Future.successful(MandateCreated(mandate(mandateId, DateTime.now())))
        }

        when(authConnectorMock.getAuthority()(Matchers.any())) thenReturn {
          Future.successful(successResponseJsonAuth)
        }

        when(etmpConnectorMock.getRegistrationDetails(Matchers.any(), Matchers.eq("arn"))) thenReturn {
          Future.successful(successResponseJsonETMP)
        }

        val createdMandateId = TestClientMandateCreateService.createMandate(agentCode, mandateDto)
        await(createdMandateId) must be(mandateId)

      }

      "results in error" in {
        val mandateId = TestClientMandateCreateService.createMandateId
        val successResponseJsonETMP = Json.parse(
          """
            |{
            |  "sapNumber":"1234567890",
            |  "safeId": "EX0012345678909",
            |  "agentReferenceNumber": "AARN1234567",
            |  "isAnIndividual": false,
            |  "organisation": {
            |    "organisationName": "ABC Limited"
            |  }
            |}
          """.stripMargin
        )
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

        when(mandateRepositoryMock.insertMandate(Matchers.any())) thenReturn {
          Future.successful(MandateCreateError)
        }

        when(authConnectorMock.getAuthority()(Matchers.any())) thenReturn {
          Future.successful(successResponseJsonAuth)
        }

        when(etmpConnectorMock.getRegistrationDetails(Matchers.any(), Matchers.eq("arn"))) thenReturn {
          Future.successful(successResponseJsonETMP)
        }

        val thrown = the[RuntimeException] thrownBy await(TestClientMandateCreateService.createMandate(agentCode, mandateDto))
        thrown.getMessage must include("Mandate not created")
      }
    }

    "generate a 10 character mandate id" when {

      "a client mandate is created" in {
        val mandateId = TestClientMandateCreateService.createMandateId
        mandateId.length must be(8)
      }
    }

    "getAgentPartyName" must {
      "get agent name for individual" in {
        val etmpAgentDetails = Json.parse(
          """
            |{
            |  "sapNumber":"1234567890",
            |  "safeId": "EX0012345678909",
            |  "agentReferenceNumber": "AARN1234567",
            |  "isAnIndividual": true,
            |  "individual" : {
            |    "firstName": "firstName",
            |    "lastName": "lastName"
            |  }
            |}
          """.stripMargin
        )

        val agentPartyName = TestClientMandateCreateService.getPartyName(etmpAgentDetails, isAnIndividual = true)
        agentPartyName mustBe "firstName lastName"
      }

      "get agent name for organisation" in {

        val etmpAgentDetails = Json.parse(
          """
            |{
            |  "sapNumber":"1234567890",
            |  "safeId": "EX0012345678909",
            |  "agentReferenceNumber": "AARN1234567",
            |  "isAnIndividual": false,
            |  "organisation": {
            |    "organisationName": "ABC Limited"
            |  }
            |}
          """.stripMargin
        )

        val agentPartyName = TestClientMandateCreateService.getPartyName(etmpAgentDetails, isAnIndividual = false)
        agentPartyName mustBe "ABC Limited"
      }
    }

    "getAgentPartyType" must {
      "party type for organisation" in {
        val partyType = TestClientMandateCreateService.getPartyType(false)
        partyType must be(PartyType.Organisation)
      }

      "party type for individual" in {
        val partyType = TestClientMandateCreateService.getPartyType(true)
        partyType must be(PartyType.Individual)
      }
    }

    "createMandateForNonUKClient" when {
      "agent registers a Non-UK Client" in {
        val mandateId = TestClientMandateCreateService.createMandateId
        val successResponseJsonETMP = Json.parse(
          """
            |{
            |  "sapNumber":"1234567890",
            |  "safeId": "EX0012345678909",
            |  "agentReferenceNumber": "AARN1234567",
            |  "isAnIndividual": false,
            |  "organisation": {
            |    "organisationName": "ABC Limited"
            |  }
            |}
          """.stripMargin
        )
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


        when(authConnectorMock.getAuthority()(Matchers.any())) thenReturn {
          Future.successful(successResponseJsonAuth)
        }

        when(etmpConnectorMock.getRegistrationDetails(Matchers.any(), Matchers.eq("arn"))) thenReturn {
          Future.successful(successResponseJsonETMP)
        }
        when(etmpConnectorMock.getRegistrationDetails(Matchers.any(), Matchers.eq("safeid"))) thenReturn {
          Future.successful(successResponseJsonETMP)
        }

        when(mandateRepositoryMock.insertMandate(Matchers.any())) thenReturn {
          Future.successful(MandateCreated(mandateWithClient(mandateId, DateTime.now())))
        }

        val dto = NonUKClientDto("safeId", "atedRefNum", "ated", "aa@mail.com", "arn", "bb@mail.com", "client display name")
        val result = await(TestClientMandateCreateService.createMandateForNonUKClient("agentCode", dto))
        verify(relationshipServiceMock, times(0)).createAgentClientRelationship(Matchers.any(), Matchers.any())(Matchers.any())
      }

      "agent registers a Non-UK Client but fails to create mandate" in {
        val mandateId = TestClientMandateCreateService.createMandateId
        val successResponseJsonETMP = Json.parse(
          """
            |{
            |  "sapNumber":"1234567890",
            |  "safeId": "EX0012345678909",
            |  "agentReferenceNumber": "AARN1234567",
            |  "isAnIndividual": false,
            |  "organisation": {
            |    "organisationName": "ABC Limited"
            |  }
            |}
          """.stripMargin
        )
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


        when(authConnectorMock.getAuthority()(Matchers.any())) thenReturn {
          Future.successful(successResponseJsonAuth)
        }

        when(etmpConnectorMock.getRegistrationDetails(Matchers.any(), Matchers.eq("arn"))) thenReturn {
          Future.successful(successResponseJsonETMP)
        }
        when(etmpConnectorMock.getRegistrationDetails(Matchers.any(), Matchers.eq("safeid"))) thenReturn {
          Future.successful(successResponseJsonETMP)
        }

        when(mandateRepositoryMock.insertMandate(Matchers.any())) thenReturn {
          Future.successful(MandateCreateError)
        }

        val dto = NonUKClientDto("safeId", "atedRefNum", "ated", "aa@mail.com", "arn", "bb@mail.com", "client display name")
        val result = await(TestClientMandateCreateService.createMandateForNonUKClient("agentCode", dto))
        verify(relationshipServiceMock, times(0)).createAgentClientRelationship(Matchers.any(), Matchers.any())(Matchers.any())
      }

    }

    "updateMandateForNonUKClient" when {

      "agent changes a Non-UK Client" in {

        val mandateId = TestClientMandateCreateService.createMandateId
        val newAgentETMPRegJson = Json.parse(
          """
            |{
            |  "sapNumber":"1234567890",
            |  "safeId": "EX0012345678909",
            |  "agentReferenceNumber": "AARN1234567",
            |  "isAnIndividual": false,
            |  "organisation": {
            |    "organisationName": "ABC Limited"
            |  }
            |}
          """.stripMargin
        )
        val newAgentJsonAuth = Json.parse(
          """{
               "credentials": {
                 "gatewayId": "cred-id-112145698732",
                 "idaPids": []
               },
               "accounts": {
                 "agent": {
                   "agentCode":"AGENT-345", "agentBusinessUtr":"KARN123123"
                 }
               }
             }""")

        when(authConnectorMock.getAuthority()(Matchers.any())) thenReturn {
          Future.successful(newAgentJsonAuth)
        }
        when(etmpConnectorMock.getRegistrationDetails(Matchers.any(), Matchers.eq("arn"))) thenReturn {
          Future.successful(newAgentETMPRegJson)
        }
        when(mockMandateFetchService.fetchClientMandate(Matchers.any())) thenReturn {
          Future.successful(MandateFetched(mandate))
        }
        when(mandateRepositoryMock.updateMandate(Matchers.any())) thenReturn {
          Future.successful(MandateUpdated(mandateWithClient(mandateId, DateTime.now())))
        }

        val dto = NonUKClientDto(safeId = "safeId",
          subscriptionReference = "X12345678",
          service = "ated",
          clientEmail = "aa@mail.com",
          arn = "KARN123123",
          agentEmail = "bb@mail.com",
          clientDisplayName = "client display name",
          mandateRef = Some("B3671590"))
        val result = await(TestClientMandateCreateService.updateMandateForNonUKClient("AGENT-345", dto))

        verify(relationshipServiceMock, times(1)).createAgentClientRelationship(Matchers.any(), Matchers.any())(Matchers.any())
      }

      "throw an exception during agent tries changing a Non-UK Client but no old mandate ref found" in {
        val successResponseJsonETMP = Json.parse(
          """
            |{
            |  "sapNumber":"1234567890",
            |  "safeId": "EX0012345678909",
            |  "agentReferenceNumber": "AARN1234567",
            |  "isAnIndividual": false,
            |  "organisation": {
            |    "organisationName": "ABC Limited"
            |  }
            |}
          """.stripMargin
        )

        when(etmpConnectorMock.getRegistrationDetails(Matchers.eq("KARN123123"), Matchers.eq("arn"))) thenReturn {
          Future.successful(successResponseJsonETMP)
        }

        val dto = NonUKClientDto("safeId", "atedRefNum", "ated", "aa@mail.com", "KARN123123", "bb@mail.com", "client display name", mandateRef = None)
        val thrown = the [RuntimeException] thrownBy await(TestClientMandateCreateService.updateMandateForNonUKClient("AGENT-345", dto))
        thrown.getMessage must include("No Old Non-UK Mandate ID recieved for updating mandate")
      }

      "throw an exception during agent tries changing a Non-UK Client but no mandate fetched" in {
        val successResponseJsonETMP = Json.parse(
          """
            |{
            |  "sapNumber":"1234567890",
            |  "safeId": "EX0012345678909",
            |  "agentReferenceNumber": "AARN1234567",
            |  "isAnIndividual": false,
            |  "organisation": {
            |    "organisationName": "ABC Limited"
            |  }
            |}
          """.stripMargin
        )
        val successResponseJsonAuth = Json.parse(
          """{
               "credentials": {
                 "gatewayId": "cred-id-112145698732",
                 "idaPids": []
               },
               "accounts": {
                 "agent": {
                   "agentCode":"AGENT-345", "agentBusinessUtr":"KARN123123"
                 }
               }
             }""")

        when(authConnectorMock.getAuthority()(Matchers.any())) thenReturn {
          Future.successful(successResponseJsonAuth)
        }

        when(etmpConnectorMock.getRegistrationDetails(Matchers.eq("KARN123123"), Matchers.eq("arn"))) thenReturn {
          Future.successful(successResponseJsonETMP)
        }
        when(mockMandateFetchService.fetchClientMandate(Matchers.any())) thenReturn {
          Future.successful(MandateNotFound)
        }

        val dto = NonUKClientDto("safeId", "atedRefNum", "ated", "aa@mail.com", "KARN123123", "bb@mail.com", "client display name", mandateRef = Some("AAAA"))
        val thrown = the [RuntimeException] thrownBy await(TestClientMandateCreateService.updateMandateForNonUKClient("AGENT-345", dto))
        thrown.getMessage must include("No existing non-uk mandate details found for mandate id")
        verify(relationshipServiceMock, times(0)).createAgentClientRelationship(Matchers.any(), Matchers.any())(Matchers.any())
      }


      "agent registers a Non-UK Client but fails to update mandate" in {

        val successResponseJsonETMP = Json.parse(
          """
            |{
            |  "sapNumber":"1234567890",
            |  "safeId": "EX0012345678909",
            |  "agentReferenceNumber": "AARN1234567",
            |  "isAnIndividual": false,
            |  "organisation": {
            |    "organisationName": "ABC Limited"
            |  }
            |}
          """.stripMargin
        )
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


        when(authConnectorMock.getAuthority()(Matchers.any())) thenReturn {
          Future.successful(successResponseJsonAuth)
        }

        when(etmpConnectorMock.getRegistrationDetails(Matchers.any(), Matchers.any())) thenReturn {
          Future.successful(successResponseJsonETMP)
        }
        when(mockMandateFetchService.fetchClientMandate(Matchers.any())) thenReturn {
          Future.successful(MandateFetched(mandate))
        }

        when(mandateRepositoryMock.updateMandate(Matchers.any())) thenReturn {
          Future.successful(MandateUpdateError)
        }

        val dto = NonUKClientDto("safeId", "atedRefNum", "ated", "aa@mail.com", "arn", "bb@mail.com", "client display name", mandateRef = Some("B3671590"))
        val result = await(TestClientMandateCreateService.updateMandateForNonUKClient("AGENT-123", dto))
        verify(relationshipServiceMock, times(0)).createAgentClientRelationship(Matchers.any(), Matchers.any())(Matchers.any())
      }
    }
  }

  val mandateDto = CreateMandateDto("test@test.com", "ated", "client display name")

  def mandate(id: String, statusTime: DateTime): Mandate =
    Mandate(id = id, createdBy = User(hc.gaUserId.getOrElse("credid"), "Joe Bloggs", Some(agentCode)),
      agentParty = Party("JARN123456", "Joe Bloggs", PartyType.Organisation, ContactDetails("test@test.com", Some("0123456789"))),
      clientParty = None,
      currentStatus = MandateStatus(Status.New, statusTime, "credid"),
      statusHistory = Nil,
      subscription = Subscription(None, Service("ated", "ATED")),
      clientDisplayName = "client display name"
    )

  val mandate =
    Mandate(
      id = "B3671590",
      createdBy = User("cred-id-113244018119", "ABC Limited", Some("agentCode")),
      agentParty = Party("arn", "ABC Limited", PartyType.Organisation, ContactDetails("bb@mail.com", None)),
      clientParty = Some(Party("safeId", "ABC Limited", PartyType.Organisation, ContactDetails("aa@mail.com", None))),
      currentStatus = MandateStatus(Status.PendingActivation, new DateTime(), "cred-id-113244018119"),
      statusHistory = Nil,
      subscription = Subscription(Some("atedRefNum"), Service("ated", "ated")),
      clientDisplayName = "client display name")

  val mandateUpdated =
    Mandate(
      id = "B3671590",
      createdBy = User("cred-id-113244018119", "ABC Limited", Some("agentCode")),
      agentParty = Party("KARN123123", "DEF Limited", PartyType.Organisation, ContactDetails("zz@mail.com", None)),
      clientParty = Some(Party("safeId", "ABC Limited", PartyType.Organisation, ContactDetails("aa@mail.com", None))),
      currentStatus = MandateStatus(Status.PendingActivation, new DateTime(), "cred-id-113244018119"),
      statusHistory = Seq(MandateStatus(Status.Cancelled, new DateTime(), "cred-id-113244018119"), MandateStatus(Status.PendingCancellation, new DateTime(), "cred-id-113244018119")),
      subscription = Subscription(Some("atedRefNum"), Service("ated", "ated")),
      clientDisplayName = "client display name")

  def mandateWithClient(id: String, statusTime: DateTime): Mandate =
    Mandate(id = id, createdBy = User(hc.gaUserId.getOrElse("credid"), "Joe Bloggs", Some(agentCode)),
      agentParty = Party("JARN123456", "Joe Bloggs", PartyType.Organisation, ContactDetails("test@test.com", Some("0123456789"))),
      clientParty = Some(Party("clientId", "client name", PartyType.Organisation, ContactDetails("client@test.com", Some("0123456789")))),
      currentStatus = MandateStatus(Status.New, statusTime, "credid"),
      statusHistory = Nil,
      subscription = Subscription(None, Service("ated", "ATED")),
      clientDisplayName = "client display name"
    )

  implicit val hc = HeaderCarrier()
  val agentCode = "ac"

  val mandateRepositoryMock = mock[MandateRepository]
  val authConnectorMock = mock[AuthConnector]
  val etmpConnectorMock = mock[EtmpConnector]
  val relationshipServiceMock = mock[RelationshipService]
  val mockMandateFetchService = mock[MandateFetchService]

  object TestClientMandateCreateService extends MandateCreateService {
    override val mandateRepository = mandateRepositoryMock
    override val authConnector = authConnectorMock
    override val mandateFetchService = mockMandateFetchService
    override val etmpConnector = etmpConnectorMock
    override val relationshipService = relationshipServiceMock
    override val audit: Audit = new TestAudit
  }

  override def beforeEach(): Unit = {
    reset(mandateRepositoryMock)
    reset(authConnectorMock)
    reset(etmpConnectorMock)
    reset(relationshipServiceMock)
    reset(authConnectorMock)
    reset(mockMandateFetchService)
  }

}

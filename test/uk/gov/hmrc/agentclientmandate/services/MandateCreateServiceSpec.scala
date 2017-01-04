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
import play.api.test.Helpers._
import uk.gov.hmrc.agentclientmandate.connectors.{AuthConnector, EtmpConnector}
import uk.gov.hmrc.agentclientmandate.models._
import uk.gov.hmrc.agentclientmandate.repositories._
import uk.gov.hmrc.agentclientmandate.utils.TestAudit
import uk.gov.hmrc.play.audit.model.Audit
import uk.gov.hmrc.play.http.{HeaderCarrier, HttpResponse}

import scala.concurrent.Future

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

        when(etmpConnectorMock.getDetails(Matchers.any(), Matchers.eq("arn"))) thenReturn {
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

        when(etmpConnectorMock.getDetails(Matchers.any(), Matchers.eq("arn"))) thenReturn {
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


    "createMandateForExistingRelationships" must {

      "create a mandate successfully" in {

        val successResponseJsonETMPForAgent = Json.parse(
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
        val etmpSubscriptionJson = Json.parse(
          """
            |{
            |  "safeId": "cred-id-1234567890",
            |  "organisationName": "client-name"
            |}
          """.stripMargin
        )

        val mandateId = TestClientMandateCreateService.createMandateId

        when(mandateRepositoryMock.insertMandate(Matchers.any())) thenReturn {
          Future.successful(MandateCreated(mandateWithClient(mandateId, DateTime.now())))
        }
        when(mandateRepositoryMock.existingRelationshipProcessed(Matchers.any())) thenReturn {
          Future.successful(ExistingRelationshipProcessed)
        }
        when(etmpConnectorMock.getDetails(Matchers.any(), Matchers.eq("arn"))) thenReturn {
          Future.successful(successResponseJsonETMPForAgent)
        }
        when(etmpConnectorMock.getAtedSubscriptionDetails(Matchers.any())).thenReturn(Future.successful(etmpSubscriptionJson))

        val ggRelationshipDto = GGRelationshipDto("ated", "agentPartyId", "credId", "clientSubscriptionId")

        val result = await(TestClientMandateCreateService.createMandateForExistingRelationships(ggRelationshipDto))
        result mustBe true
      }

      "create a mandate successfully but fail to mark it processed" in {

        val successResponseJsonETMPForAgent = Json.parse(
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
        val etmpSubscriptionJson = Json.parse(
          """
            |{
            |  "safeId": "cred-id-1234567890",
            |  "organisationName": "client-name"
            |}
          """.stripMargin
        )

        val mandateId = TestClientMandateCreateService.createMandateId

        when(mandateRepositoryMock.insertMandate(Matchers.any())) thenReturn {
          Future.successful(MandateCreated(mandateWithClient(mandateId, DateTime.now())))
        }
        when(mandateRepositoryMock.existingRelationshipProcessed(Matchers.any())) thenReturn {
          Future.successful(ExistingRelationshipProcessError)
        }
        when(etmpConnectorMock.getDetails(Matchers.any(), Matchers.eq("arn"))) thenReturn {
          Future.successful(successResponseJsonETMPForAgent)
        }
        when(etmpConnectorMock.getAtedSubscriptionDetails(Matchers.any())).thenReturn(Future.successful(etmpSubscriptionJson))

        val ggRelationshipDto = GGRelationshipDto("ated", "agentPartyId", "credId", "clientSubscriptionId")

        val result = await(TestClientMandateCreateService.createMandateForExistingRelationships(ggRelationshipDto))
        result mustBe false
      }

      "fails to create a mandate" in {

        val successResponseJsonETMPForAgent = Json.parse(
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
        val etmpSubscriptionJson = Json.parse(
          """
            |{
            |  "safeId": "cred-id-1234567890",
            |  "organisationName": "client-name"
            |}
          """.stripMargin
        )

        val mandateId = TestClientMandateCreateService.createMandateId

        when(mandateRepositoryMock.insertMandate(Matchers.any())) thenReturn {
          Future.successful(MandateCreateError)
        }
        when(etmpConnectorMock.getDetails(Matchers.any(), Matchers.eq("arn"))) thenReturn {
          Future.successful(successResponseJsonETMPForAgent)
        }
        when(etmpConnectorMock.getAtedSubscriptionDetails(Matchers.any())).thenReturn(Future.successful(etmpSubscriptionJson))

        val ggRelationshipDto = GGRelationshipDto("ated", "agentPartyId", "credId", "clientSubscriptionId")

        val result = await(TestClientMandateCreateService.createMandateForExistingRelationships(ggRelationshipDto))
        result mustBe false
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

    "insert existing relationships" must {
      "insert successfully" in {
        when (mandateRepositoryMock.insertExistingRelationships(Matchers.any())).thenReturn(Future.successful(ExistingRelationshipsInserted))

        val ggRelationshipDto = GGRelationshipDto("ated", "agentPartyId", "credId", "clientSubscriptionId")

        val result = await(TestClientMandateCreateService.insertExistingRelationships(List(ggRelationshipDto)))
        result mustBe ExistingRelationshipsInserted
      }
    }

    "createMandateForNonUKClient" must {
      "create and return mandate Id, if successful" in {
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
          Future.successful(MandateCreated(mandateWithClient(mandateId, DateTime.now())))
        }

        when(authConnectorMock.getAuthority()(Matchers.any())) thenReturn {
          Future.successful(successResponseJsonAuth)
        }

        when(etmpConnectorMock.getDetails(Matchers.any(), Matchers.eq("arn"))) thenReturn {
          Future.successful(successResponseJsonETMP)
        }
        when(etmpConnectorMock.getDetails(Matchers.any(), Matchers.eq("safeid"))) thenReturn {
          Future.successful(successResponseJsonETMP)
        }
        when(relationshipServiceMock.maintainRelationship(Matchers.any(), Matchers.eq("agentCode"), Matchers.eq("Authorise"))(Matchers.any())) thenReturn {
          Future.successful(HttpResponse(OK))
        }
        val dto = NonUKClientDto("safeId", "atedRefNum", "ated", "aa@mail.com", "arn", "bb@mail.com", "client display name")
        val result = TestClientMandateCreateService.createMandateForNonUKClient("agentCode", dto)
        await(result) must be(mandateId)
      }

      "throw an exception, if creation failed due to some reason" in {
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

        when(etmpConnectorMock.getDetails(Matchers.any(), Matchers.eq("arn"))) thenReturn {
          Future.successful(successResponseJsonETMP)
        }
        when(etmpConnectorMock.getDetails(Matchers.any(), Matchers.eq("safeid"))) thenReturn {
          Future.successful(successResponseJsonETMP)
        }
        when(relationshipServiceMock.maintainRelationship(Matchers.any(), Matchers.eq("agentCode"), Matchers.eq("Authorise"))(Matchers.any())) thenReturn {
          Future.successful(HttpResponse(OK))
        }
        val dto = NonUKClientDto("safeId", "atedRefNum", "ated", "aa@mail.com", "arn", "bb@mail.com", "client display name")
        val thrown = the[RuntimeException] thrownBy await(TestClientMandateCreateService.createMandateForNonUKClient("agentCode", dto))
        thrown.getMessage must be("Mandate not created")
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

  object TestClientMandateCreateService extends MandateCreateService {
    override val mandateRepository = mandateRepositoryMock
    override val authConnector = authConnectorMock
    override val etmpConnector = etmpConnectorMock
    override val relationshipService = relationshipServiceMock
    override val audit: Audit = new TestAudit
  }

  override def beforeEach(): Unit = {
    reset(mandateRepositoryMock)
    reset(authConnectorMock)
    reset(etmpConnectorMock)
    reset(relationshipServiceMock)
  }

}

/*
 * Copyright 2024 HM Revenue & Customs
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

import com.typesafe.config.{Config, ConfigFactory}

import java.time.Instant
import org.mockito.ArgumentMatchers._
import org.mockito.ArgumentMatchers
import org.mockito.Mockito.{reset, times, verify, when}
import org.scalatest.BeforeAndAfterEach
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.PlaySpec
import play.api.libs.json.Json
import play.api.test.Helpers._
import uk.gov.hmrc.agentclientmandate.auth.AuthRetrieval
import uk.gov.hmrc.agentclientmandate.connectors.EtmpConnector
import uk.gov.hmrc.agentclientmandate.models._
import uk.gov.hmrc.agentclientmandate.repositories._
import uk.gov.hmrc.agentclientmandate.utils.Generators._
import uk.gov.hmrc.auth.core.retrieve.{AgentInformation, Credentials}
import uk.gov.hmrc.auth.core.{Enrolment, EnrolmentIdentifier}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.audit.http.connector.AuditConnector

import scala.concurrent.{ExecutionContext, Future}

class MandateCreateServiceSpec extends PlaySpec with MockitoSugar with BeforeAndAfterEach {

  implicit val hc: HeaderCarrier = HeaderCarrier()
  val agentCode = "ac"

  val mandateRepositoryMock: MandateRepository = mock[MandateRepository]
  val etmpConnectorMock: EtmpConnector = mock[EtmpConnector]
  val relationshipServiceMock: RelationshipService = mock[RelationshipService]
  val mockMandateFetchService: MandateFetchService = mock[MandateFetchService]
  val mockAuditConnector: AuditConnector = mock[AuditConnector]

  object TestClientMandateCreateService extends MandateCreateService {
    val ec: ExecutionContext = ExecutionContext.global
    override val mandateRepository: MandateRepository = mandateRepositoryMock
    override val mandateFetchService: MandateFetchService = mockMandateFetchService
    override val etmpConnector: EtmpConnector = etmpConnectorMock
    override val relationshipService: RelationshipService = relationshipServiceMock
    override val auditConnector: AuditConnector = mockAuditConnector
    override val identifiers: Config = ConfigFactory.load("identifiers.properties")
  }

  override def beforeEach(): Unit = {
    reset(mandateRepositoryMock)
    reset(etmpConnectorMock)
    reset(relationshipServiceMock)
    reset(mockMandateFetchService)
  }

  val mandateDto: CreateMandateDto = CreateMandateDto(emailGen.sample.get, "ated", "client display name")

  val mandate: Mandate =
    Mandate(
      id = "B3671590",
      createdBy = User("cred-id-113244018119", companyNameGen.sample.get, Some("agentCode")),
      agentParty = Party(partyIDGen.sample.get, companyNameGen.sample.get, PartyType.Organisation, ContactDetails(emailGen.sample.get, None)),
      clientParty = Some(Party(partyIDGen.sample.get, companyNameGen.sample.get, PartyType.Organisation, ContactDetails(emailGen.sample.get, None))),
      currentStatus = MandateStatus(Status.PendingActivation, Instant.now(), "cred-id-113244018119"),
      statusHistory = Nil,
      subscription = Subscription(subscriptionReferenceGen.sample, Service("ated", "ated")),
      clientDisplayName = "client display name")

  val mandateUpdated: Mandate =
    Mandate(
      id = "B3671590",
      createdBy = User("cred-id-113244018119", companyNameGen.sample.get, Some("agentCode")),
      agentParty = Party(partyIDGen.sample.get, companyNameGen.sample.get, PartyType.Organisation, ContactDetails(emailGen.sample.get, None)),
      clientParty = Some(Party(partyIDGen.sample.get, companyNameGen.sample.get, PartyType.Organisation, ContactDetails(emailGen.sample.get, None))),
      currentStatus = MandateStatus(Status.PendingActivation, Instant.now(), "cred-id-113244018119"),
      statusHistory = Seq(MandateStatus(Status.Cancelled, Instant.now(), "cred-id-113244018119"),
      MandateStatus(Status.PendingCancellation, Instant.now(), "cred-id-113244018119")),
      subscription = Subscription(Some("atedRefNum"), Service("ated", "ated")),
      clientDisplayName = "client display name")

  implicit val testAuthRetrieval: AuthRetrieval = AuthRetrieval(
    enrolments = Set(
      Enrolment(
        key = "HMRC-AGENT-AGENT",
        identifiers = Seq(EnrolmentIdentifier(key = "AgentRefNumber", value = agentBusinessUtrGen.sample.get)),
        state = "active"
      )
    ),
    agentInformation = AgentInformation(None, None, None),
    credentials = Option(Credentials(providerId = "cred-id-113244018119", providerType = "GovernmentGateway"))
  )

  "MandateCreateService" should {

    "create a client mandate status object with a status of pending for new client mandates" in {

      val mandateStatus = TestClientMandateCreateService.createNewStatus("credid")
      mandateStatus must be(MandateStatus(Status.New, mandateStatus.timestamp, "credid"))

    }

    "createMandate" when {

      "a Mandate is created" in {

        val mandateId = TestClientMandateCreateService.createMandateId
        val successResponseJsonETMP = Json.parse(
          s"""
            |{
            |  "sapNumber":"${sapNumberGen.sample.get}",
            |  "safeId": "${safeIDGen.sample.get}",
            |  "agentReferenceNumber": "${agentReferenceNumberGen.sample.get}",
            |  "isAnIndividual": false,
            |  "organisation": {
            |    "organisationName": "${companyNameGen.sample.get}"
            |  }
            |}
          """.stripMargin
        )

        when(mandateRepositoryMock.insertMandate(any())(any())) thenReturn {
          Future.successful(MandateCreated(mandate(mandateId, Instant.now())))
        }

        when(etmpConnectorMock.getRegistrationDetails(any(), ArgumentMatchers.eq("arn"))) thenReturn {
          Future.successful(successResponseJsonETMP)
        }

        val createdMandateId = TestClientMandateCreateService.createMandate(agentCode, mandateDto)
        await(createdMandateId) must be(mandateId)

      }

      "results in error" in {
        val successResponseJsonETMP = Json.parse(
          s"""
             |{
             |  "sapNumber":"${sapNumberGen.sample.get}",
             |  "safeId": "${safeIDGen.sample.get}",
             |  "agentReferenceNumber": "${agentReferenceNumberGen.sample.get}",
             |  "isAnIndividual": false,
             |  "organisation": {
             |    "organisationName": "${companyNameGen.sample.get}"
             |  }
             |}
          """.stripMargin
        )

        when(mandateRepositoryMock.insertMandate(any())(any())) thenReturn {
          Future.successful(MandateCreateError)
        }

        when(etmpConnectorMock.getRegistrationDetails(any(), ArgumentMatchers.eq("arn"))) thenReturn {
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
        val firstName = firstNameGen.sample.get
        val lastName = lastNameGen.sample.get

        val etmpAgentDetails = Json.parse(
          s"""
            |{
            |  "sapNumber":"${sapNumberGen.sample.get}",
            |  "safeId": "${safeIDGen.sample.get}",
            |  "agentReferenceNumber": "${agentReferenceNumberGen.sample.get}",
            |  "isAnIndividual": true,
            |  "individual" : {
            |    "firstName": "$firstName",
            |    "lastName": "$lastName"
            |  }
            |}
          """.stripMargin
        )

        val agentPartyName = TestClientMandateCreateService.getPartyName(etmpAgentDetails, isAnIndividual = true)
        agentPartyName mustBe s"$firstName $lastName"
      }

      "get agent name for organisation" in {
        val companyName = companyNameGen.sample.get
        val etmpAgentDetails = Json.parse(
          s"""
             |{
             |  "sapNumber":"${sapNumberGen.sample.get}",
             |  "safeId": "${safeIDGen.sample.get}",
             |  "agentReferenceNumber": "${agentReferenceNumberGen.sample.get}",
             |  "isAnIndividual": false,
             |  "organisation": {
             |    "organisationName": "$companyName"
             |  }
             |}
          """.stripMargin
        )

        val agentPartyName = TestClientMandateCreateService.getPartyName(etmpAgentDetails, isAnIndividual = false)
        agentPartyName mustBe companyName
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
          s"""
             |{
             |  "sapNumber":"${sapNumberGen.sample.get}",
             |  "safeId": "${safeIDGen.sample.get}",
             |  "agentReferenceNumber": "${agentReferenceNumberGen.sample.get}",
             |  "isAnIndividual": false,
             |  "organisation": {
             |    "organisationName": "${companyNameGen.sample.get}"
             |  }
             |}
          """.stripMargin
        )

        when(etmpConnectorMock.getRegistrationDetails(any(),ArgumentMatchers.eq("arn"))) thenReturn {
          Future.successful(successResponseJsonETMP)
        }
        when(etmpConnectorMock.getRegistrationDetails(any(),ArgumentMatchers.eq("safeid"))) thenReturn {
          Future.successful(successResponseJsonETMP)
        }

        when(mandateRepositoryMock.insertMandate(any())(any())) thenReturn {
          Future.successful(MandateCreated(mandateWithClient(mandateId, Instant.now())))
        }

        val dto = NonUKClientDto(safeIDGen.sample.get, "atedRefNum", "ated", emailGen.sample.get, "arn", emailGen.sample.get, "client display name")
        await(TestClientMandateCreateService.createMandateForNonUKClient(agentCodeGen.sample.get, dto))
        verify(relationshipServiceMock, times(1)).createAgentClientRelationship(any(), any())(any(), any())
      }

      "agent registers a Non-UK Client but fails to create mandate" in {
        val successResponseJsonETMP = Json.parse(
          s"""
             |{
             |  "sapNumber":"${sapNumberGen.sample.get}",
             |  "safeId": "${safeIDGen.sample.get}",
             |  "agentReferenceNumber": "${agentReferenceNumberGen.sample.get}",
             |  "isAnIndividual": false,
             |  "organisation": {
             |    "organisationName": "${companyNameGen.sample.get}"
             |  }
             |}
          """.stripMargin
        )

        when(etmpConnectorMock.getRegistrationDetails(any(),ArgumentMatchers.eq("arn"))) thenReturn {
          Future.successful(successResponseJsonETMP)
        }
        when(etmpConnectorMock.getRegistrationDetails(any(),ArgumentMatchers.eq("safeid"))) thenReturn {
          Future.successful(successResponseJsonETMP)
        }

        when(mandateRepositoryMock.insertMandate(any())(any())) thenReturn {
          Future.successful(MandateCreateError)
        }

        val dto = NonUKClientDto(
          safeIDGen.sample.get,
          "atedRefNum",
          "ated",
          emailGen.sample.get,
          agentReferenceNumberGen.sample.get,
          emailGen.sample.get,
          "client display name")
        an[RuntimeException] should be thrownBy await(TestClientMandateCreateService.createMandateForNonUKClient(agentCodeGen.sample.get, dto))
      }

    }

    "updateMandateForNonUKClient" when {

      "agent changes a Non-UK Client" in {

        val mandateId = TestClientMandateCreateService.createMandateId
        val newAgentETMPRegJson = Json.parse(
          s"""
             |{
             |  "sapNumber":"${sapNumberGen.sample.get}",
             |  "safeId": "${safeIDGen.sample.get}",
             |  "agentReferenceNumber": "${agentReferenceNumberGen.sample.get}",
             |  "isAnIndividual": false,
             |  "organisation": {
             |    "organisationName": "${companyNameGen.sample.get}"
             |  }
             |}
          """.stripMargin
        )

        when(etmpConnectorMock.getRegistrationDetails(any(),ArgumentMatchers.eq("arn"))) thenReturn {
          Future.successful(newAgentETMPRegJson)
        }
        when(mockMandateFetchService.fetchClientMandate(any())(any())) thenReturn {
          Future.successful(MandateFetched(mandate))
        }
        when(mandateRepositoryMock.updateMandate(any())(any())) thenReturn {
          Future.successful(MandateUpdated(mandateWithClient(mandateId, Instant.now())))
        }

        val dto = NonUKClientDto(safeId = safeIDGen.sample.get,
          subscriptionReference = subscriptionReferenceGen.sample.get,
          service = "ated",
          clientEmail = emailGen.sample.get,
          arn = agentReferenceNumberGen.sample.get,
          agentEmail = emailGen.sample.get,
          clientDisplayName = "client display name",
          mandateRef = mandateReferenceGen.sample)

        await(TestClientMandateCreateService.updateMandateForNonUKClient(agentCodeGen.sample.get, dto))

        verify(relationshipServiceMock, times(1)).createAgentClientRelationship(any(), any())(any(), any())
      }

      "throw an exception during agent tries changing a Non-UK Client but no old mandate ref found" in {
        val successResponseJsonETMP = Json.parse(
          s"""
             |{
             |  "sapNumber":"${sapNumberGen.sample.get}",
             |  "safeId": "${safeIDGen.sample.get}",
             |  "agentReferenceNumber": "${agentReferenceNumberGen.sample.get}",
             |  "isAnIndividual": false,
             |  "organisation": {
             |    "organisationName": "${companyNameGen.sample.get}"
             |  }
             |}
          """.stripMargin
        )

        val agentReferenceNumber = agentReferenceNumberGen.sample.get

        when(etmpConnectorMock.getRegistrationDetails(ArgumentMatchers.eq(agentReferenceNumber),ArgumentMatchers.eq("arn"))) thenReturn {
          Future.successful(successResponseJsonETMP)
        }

        val dto = NonUKClientDto(
          safeIDGen.sample.get,
          "atedRefNum",
          "ated",
          emailGen.sample.get,
          agentReferenceNumber,
          emailGen.sample.get,
          "client display name",
          mandateRef = None)
        val thrown = the[RuntimeException] thrownBy await(TestClientMandateCreateService.updateMandateForNonUKClient(agentCodeGen.sample.get, dto))
        thrown.getMessage must include("No Old Non-UK Mandate ID recieved for updating mandate")
      }

      "throw an exception during agent tries changing a Non-UK Client but no mandate fetched" in {
        val successResponseJsonETMP = Json.parse(
          s"""
             |{
             |  "sapNumber":"${sapNumberGen.sample.get}",
             |  "safeId": "${safeIDGen.sample.get}",
             |  "agentReferenceNumber": "${agentReferenceNumberGen.sample.get}",
             |  "isAnIndividual": false,
             |  "organisation": {
             |    "organisationName": "${companyNameGen.sample.get}"
             |  }
             |}
          """.stripMargin
        )
        
        val agentReferenceNumber = agentReferenceNumberGen.sample.get

        when(etmpConnectorMock.getRegistrationDetails(ArgumentMatchers.eq(agentReferenceNumber),ArgumentMatchers.eq("arn"))) thenReturn {
          Future.successful(successResponseJsonETMP)
        }
        when(mockMandateFetchService.fetchClientMandate(any())(any())) thenReturn {
          Future.successful(MandateNotFound)
        }

        val dto = NonUKClientDto(
          safeIDGen.sample.get,
          "atedRefNum",
          "ated",
          emailGen.sample.get,
          agentReferenceNumber,
          emailGen.sample.get,
          "client display name",
          mandateReferenceGen.sample)
        val thrown = the[RuntimeException] thrownBy await(TestClientMandateCreateService.updateMandateForNonUKClient(agentCodeGen.sample.get, dto))
        thrown.getMessage must include("No existing non-uk mandate details found for mandate id")
        verify(relationshipServiceMock, times(0)).createAgentClientRelationship(any(), any())(any(), any())
      }

      "agent registers a Non-UK Client but fails to update mandate" in {

        val successResponseJsonETMP = Json.parse(
          s"""
             |{
             |  "sapNumber":"${sapNumberGen.sample.get}",
             |  "safeId": "${safeIDGen.sample.get}",
             |  "agentReferenceNumber": "${agentReferenceNumberGen.sample.get}",
             |  "isAnIndividual": false,
             |  "organisation": {
             |    "organisationName": "${companyNameGen.sample.get}"
             |  }
             |}
          """.stripMargin
        )

        when(etmpConnectorMock.getRegistrationDetails(any(), any())) thenReturn {
          Future.successful(successResponseJsonETMP)
        }
        when(mockMandateFetchService.fetchClientMandate(any())(any())) thenReturn {
          Future.successful(MandateFetched(mandate))
        }

        when(mandateRepositoryMock.updateMandate(any())(any())) thenReturn {
          Future.successful(MandateUpdateError)
        }

        val dto = NonUKClientDto(
          safeIDGen.sample.get,
          "atedRefNum",
          "ated",
          emailGen.sample.get,
          agentReferenceNumberGen.sample.get,
          emailGen.sample.get,
          "client display name",
          mandateReferenceGen.sample)
        val thrown = the[RuntimeException] thrownBy await(TestClientMandateCreateService.updateMandateForNonUKClient(agentCodeGen.sample.get, dto))
        thrown.getMessage must include("Mandate not updated for non-uk")
      }
    }
  }

  def mandate(id: String, statusTime: Instant): Mandate =
    Mandate(id = id, createdBy = User(hc.gaUserId.getOrElse("credid"), nameGen.sample.get, Some(agentCode)),
      agentParty = Party(partyIDGen.sample.get, nameGen.sample.get, PartyType.Organisation, ContactDetails(emailGen.sample.get, telephoneNumberGen.sample)),
      clientParty = None,
      currentStatus = MandateStatus(Status.New, statusTime, "credid"),
      statusHistory = Nil,
      subscription = Subscription(None, Service("ated", "ATED")),
      clientDisplayName = "client display name"
    )

  def mandateWithClient(id: String, statusTime: Instant): Mandate =
    Mandate(id = id, createdBy = User(hc.gaUserId.getOrElse("credid"), nameGen.sample.get, Some(agentCode)),
      agentParty = Party(partyIDGen.sample.get, nameGen.sample.get, PartyType.Organisation, ContactDetails(emailGen.sample.get, telephoneNumberGen.sample)),
      clientParty = Some(Party("clientId", "client name", PartyType.Organisation, ContactDetails(emailGen.sample.get, telephoneNumberGen.sample))),
      currentStatus = MandateStatus(Status.New, statusTime, "credid"),
      statusHistory = Nil,
      subscription = Subscription(None, Service("ated", "ATED")),
      clientDisplayName = "client display name"
    )

}

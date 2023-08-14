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

package uk.gov.hmrc.agentclientmandate.services

import org.joda.time.DateTime
import org.mockito.ArgumentMatchers._
import org.mockito.{ArgumentMatchers, MockitoSugar}
import org.scalacheck.Gen
import org.scalatest.BeforeAndAfterEach
import org.scalatestplus.play.PlaySpec
import play.api.libs.json.{JsValue, Json}
import play.api.test.Helpers._
import uk.gov.hmrc.agentclientmandate.auth.AuthRetrieval
import uk.gov.hmrc.agentclientmandate.connectors.EtmpConnector
import uk.gov.hmrc.agentclientmandate.models._
import uk.gov.hmrc.agentclientmandate.utils.Generators._
import uk.gov.hmrc.auth.core.retrieve.AgentInformation
import uk.gov.hmrc.auth.core.{Enrolment, EnrolmentIdentifier}
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.Future

class AgentDetailsServiceSpec extends PlaySpec with MockitoSugar with BeforeAndAfterEach {

  implicit val hc: HeaderCarrier = HeaderCarrier()

  implicit val testAuthRetrieval: AuthRetrieval = AuthRetrieval(
    enrolments = Set(Enrolment(
      key = "HMRC-AGENT-AGENT",
      identifiers = Seq(EnrolmentIdentifier(key = "AgentRefNumber", value = agentBusinessUtrGen.sample.get)),
      state = "active"
    )),
    agentInformation = AgentInformation(None, None, None),
    credentials = None
  )

  val etmpConnectorMock: EtmpConnector = mock[EtmpConnector]
  val mockMandateFetchService: MandateFetchService = mock[MandateFetchService]

  override def beforeEach(): Unit = {
    reset(etmpConnectorMock)
    reset(mockMandateFetchService)
  }

  object TestAgentDetailsService extends AgentDetailsService {
    override val etmpConnector: EtmpConnector = etmpConnectorMock
    override val mandateFetchService: MandateFetchService = mockMandateFetchService
  }

  "AgentDetailsService" must {

    "get agent details for individual" in {

      val successResponseJsonETMP = Json.parse(
        s"""
      {
          |  "sapNumber":"${sapNumberGen.sample.get}", "safeId": "${safeIDGen.sample.get}",
          |  "agentReferenceNumber": "${agentReferenceNumberGen.sample.get}",
          |  "isAnIndividual": true,
          |  "isAnAgent": true,
          |  "isEditable": true,
          |  "individual": {
          |    "firstName": "${firstNameGen.sample.get}",
          |    "lastName": "${lastNameGen.sample.get}",
          |    "dateOfBirth": "${dateOfBirthGen.sample.get}"
          |  },
          |  "addressDetails": {
          |    "addressLine1": "${Gen.alphaStr}",
          |    "addressLine2": "${Gen.alphaStr}",
          |    "addressLine3": "${Gen.alphaStr}",
          |    "addressLine4": "${Gen.alphaStr}",
          |    "postalCode": "${postcodeGen.sample.get}",
          |    "countryCode": "GB"
          |  },
          |  "contactDetails" : {}
          |}
        """.stripMargin
      )

      when(etmpConnectorMock.getRegistrationDetails(any(), ArgumentMatchers.eq("arn"))) thenReturn {
        Future.successful(successResponseJsonETMP)
      }

      val result = await(TestAgentDetailsService.getAgentDetails)
      result.organisation.map(_.organisationName) must be(None)
    }

    "get agent details for organisation" in {
      val companyName = companyNameGen.sample.get
      val successResponseJsonETMP = Json.parse(
        s"""
      {
          |  "sapNumber":"${sapNumberGen.sample.get}", "safeId": "${safeIDGen.sample.get}",
          |  "agentReferenceNumber": "${agentReferenceNumberGen.sample.get}",
          |  "isAnIndividual": false,
          |  "isAnAgent": true,
          |  "isEditable": true,
             "organisation": {
          |    "organisationName": "$companyName"
          |  },
          |  "addressDetails": {
          |    "addressLine1": "${Gen.alphaStr}",
          |    "addressLine2": "${Gen.alphaStr}",
          |    "addressLine3": "${Gen.alphaStr}",
          |    "addressLine4": "${Gen.alphaStr}",
          |    "postalCode": "${postcodeGen.sample.get}",
          |    "countryCode": "GB"
          |  },
          |  "contactDetails" : {}
          |}
        """.stripMargin
      )

      when(etmpConnectorMock.getRegistrationDetails(any(), ArgumentMatchers.eq("arn"))) thenReturn {
        Future.successful(successResponseJsonETMP)
      }

      val result: AgentDetails = await(TestAgentDetailsService.getAgentDetails)
      result.organisation.map(_.organisationName) must be(Some(companyName))
    }

    "returns true - for delegation authorization check for Ated" when {
      "fetched mandates have a mandate with the ATED ref number passed as subscription service reference number" in {
        when(mockMandateFetchService.getAllMandates(any(), ArgumentMatchers.eq("ated"), any(), any())(any(),any())).thenReturn(Future.successful(Seq(mandate)))
        await(TestAgentDetailsService.isAuthorisedForAted(atedUtr)) must be(true)
      }
    }

    "returns false - for delegation authorization check for Ated" when {
      "authority doesn't return registered Agents" in {
        val testAuthRetrievalNoAgentRef: AuthRetrieval = AuthRetrieval(
          enrolments = Set(Enrolment(
            key = "HMRC-AGENT-AGENT",
            identifiers = Seq(),
            state = "active"
          )),
          agentInformation = AgentInformation(None, None, None),
          credentials = None
        )

        await(TestAgentDetailsService.isAuthorisedForAted(atedUtr)(testAuthRetrievalNoAgentRef)) must be(false)
      }
      "mandate subscription doesn't have subscription reference" in {
        val mandateToUse = mandate.copy(subscription = mandate.subscription.copy(referenceNumber = None))
        when(mockMandateFetchService.getAllMandates(any(), ArgumentMatchers.eq("ated"), any(), any())(any(),any()))
          .thenReturn(Future.successful(Seq(mandateToUse)))
        await(TestAgentDetailsService.isAuthorisedForAted(atedUtr)) must be(false)
      }
      "mandate doesn't have the same AtedRefNumber" in {
        val mandateToUse = mandate.copy(subscription = mandate.subscription.copy(referenceNumber = Some(atedUtr2.utr)))
        when(mockMandateFetchService.getAllMandates(any(), ArgumentMatchers.eq("ated"), any(), any())(any(),any()))
          .thenReturn(Future.successful(Seq(mandateToUse)))
        await(TestAgentDetailsService.isAuthorisedForAted(atedUtr)) must be(false)
      }
    }
  }

  val mandate: Mandate =
    Mandate(
      id = "123",
      createdBy = User("credid", "name", None),
      agentParty = Party(partyIDGen.sample.get, nameGen.sample.get, PartyType.Organisation, ContactDetails(emailGen.sample.get, telephoneNumberGen.sample)),
      clientParty = Some(Party(partyIDGen.sample.get, "Client Name", PartyType.Organisation, ContactDetails(emailGen.sample.get, telephoneNumberGen.sample))),
      currentStatus = MandateStatus(Status.New, new DateTime(), "credid"),
      statusHistory = Nil,
      subscription = Subscription(Some(atedUtr.utr), Service("ated", "ATED")),
      clientDisplayName = "client display name"
    )

  val notRegisteredAgentJsonAuth: JsValue = Json.parse(
    s"""
      {
        "accounts": {
          "agent": {
            "agentCode":"${agentCodeGen.sample.get}"
          }
        }
      }
    """
  )
}

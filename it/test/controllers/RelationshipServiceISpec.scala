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

package controllers

import com.github.tomakehurst.wiremock.client.WireMock._
import helpers.IntegrationSpec
import java.time.{LocalDateTime, ZoneOffset}
import org.scalatest
import play.api.libs.json.Json
import play.api.libs.ws.WSResponse
import uk.gov.hmrc.agentclientmandate.models._
import uk.gov.hmrc.agentclientmandate.repositories.{MandateFetchStatus, MandateFetched}

import scala.concurrent.ExecutionContext.Implicits.global

class RelationshipServiceISpec extends IntegrationSpec {

  "agent/[agentcode]/mandate/activate/[mandateid]" should {
    "activate a agent client mandate" when {
      "a mandate can be successfully activated" in {
        val email = "not-real-email@notrealemail.fake"
        val serviceName = "ATED"
        val displayName = "display-name"
        val mandateID = "mandateID"
        val fakeUTR = "FAKE-UTR"
        val user = User(
          "credID", "Name"
        )
        val subscription = Subscription(None, Service("ATED", serviceName))
        val agentParty = Party(
          fakeUTR,
          "First Name",
          PartyType.Individual,
          ContactDetails(email, None)
        )
        val mandateStatus = MandateStatus(
          Status.Approved, LocalDateTime.of(2010, 6,30,1,20).toInstant(ZoneOffset.UTC), "credID"
        )
        val mandate = Mandate(
          mandateID, user, None, None, agentParty, None, mandateStatus, Nil, subscription, displayName
        )

        await(mandateRepo.repository.insertMandate(mandate))

        stubFor(get(urlMatching("/auth/authority"))
          .willReturn(
            aResponse()
              .withStatus(200)
              .withBody(
                s"""{
                  | "accounts": {
                  |  "agent": {
                  |   "agentBusinessUtr": "$fakeUTR"
                  |  }
                  | },
                  | "credentials": {
                  |  "gatewayId" : "GID"
                  | }
                }""".stripMargin
              )
          )
        )

        stubFor(post(urlMatching("/auth/authorise"))
          .willReturn(
            aResponse()
              .withStatus(200)
              .withBody(
                s"""{
                   |	"groupId": "42424200-0000-0000-0000-000000000000",
                   |	"affinityGroup": "Agent",
                   |	"users": [
                   |		{
                   |			"credId": "42424211-Agent-Admin",
                   |			"name": "Assistant Agent",
                   |			"email": "default@example.com",
                   |			"credentialRole": "Assistant",
                   |			"description": "User Description"
                   |		}
                   |	],
                   |	"allEnrolments": [
                   |		{
                   |			"key": "HMRC-ATED-ORG",
                   |			"identifiers": [
                   |				{
                   |					"key": "ATEDRefNumber",
                   |					"value": "XN1200000100001"
                   |				}
                   |			],
                   |			"enrolmentFriendlyName": "Ated Enrolment",
                   |			"assignedUserCreds": [
                   |				"42424211-Client-Admin"
                   |			],
                   |			"state": "Activated",
                   |			"enrolmentType": "delegated",
                   |			"assignedToAll": false
                   |		},
                   |		{
                   |			"key": "HMRC-AGENT-AGENT",
                   |			"identifiers": [
                   |				{
                   |					"key": "AgentRefNumber",
                   |					"value": "XY1200000100002"
                   |				}
                   |			],
                   |			"enrolmentFriendlyName": "Agent Enrolment",
                   |			"assignedUserCreds": [
                   |				"42424211-Client-Admin"
                   |			],
                   |			"state": "Activated",
                   |			"enrolmentType": "delegated",
                   |			"assignedToAll": false
                   |		}
                   |	],
                   |  "agentInformation": {
                   |    "agentId": "007",
                   |	  "agentCode": "123456789123",
                   |    "agentFriendlyName": "FakeTim"
                   |   },
                   |  "optionalCredentials": {
                   |    "providerType": "GovernmentGateway",
                   |    "providerId": "cred-id-113244018119"
                   |  }
                   |}
                   |""".stripMargin
              )
          )
        )

        val result: WSResponse = await(
          hitApplicationEndpoint(s"/agent/FAKE-AB123456/mandate/activate/$mandateID")
            .post(Json.toJson("""{}"""))
        )

        result.status mustBe 200

        val fetchedMandate: MandateFetchStatus = await(mandateRepo.repository.fetchMandate(mandateID))
        fetchedMandate match {
          case MandateFetched(fetched) => fetched.currentStatus.status mustBe Status.PendingActivation
          case _ => scalatest.Assertions.fail()
        }
      }
    }
  }

}
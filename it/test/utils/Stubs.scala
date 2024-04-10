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

package utils

import com.github.tomakehurst.wiremock.client.WireMock.{aResponse, get, post, stubFor, urlMatching}
import com.github.tomakehurst.wiremock.stubbing.StubMapping

object Stubs {

    def stubGetArn: StubMapping = {

      stubFor(get(urlMatching(s"/registration/details\\?arn=FAKE-UTR"))
        .willReturn(
          aResponse()
            .withStatus(200)
            .withBody(
              s"""{
                 |"isAnIndividual" : true,
                 |"individual": {
                 |  "firstName": "First",
                 |  "lastName" : "Last"
                 |}
                 |}""".stripMargin
            )
        )
      )
    }

  def stubGetAuth: StubMapping = {

    stubFor(get(urlMatching("/auth/authorise"))
      .willReturn(
        aResponse()
          .withStatus(200)
          .withBody(
            s"""{
               | "accounts": {
               |  "agent": {
               |   "agentBusinessUtr": "FAKE-UTR"
               |  }
               | },
               | "credentials": {
               |  "gatewayId" : "GID"
               | }
                }""".stripMargin
          )
      )
    )
  }

  def stubPostAuth: StubMapping = {

    stubFor(post(urlMatching(s"/auth/authorise"))
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
               |					"value": "FAKE-UTR"
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
  }

  def stubEmail: StubMapping = {

    stubFor(post(urlMatching("/hmrc/email"))
      .willReturn(
        aResponse()
          .withStatus(202)
      )
    )
  }

  def stubGetSubscription: StubMapping = {

    stubFor(get(urlMatching("/annual-tax-enveloped-dwellings/subscription/XN1200000100001"))
      .willReturn(
        aResponse()
          .withStatus(200)
          .withBody(
            s"""{
               |"safeId" : "Test",
               |"organisationName" : "Test"
               |}""".stripMargin)
      )
    )
  }

  def stubGetSafeId: StubMapping = {

    stubFor(get(urlMatching(s"/registration/details\\?safeid=Test"))
      .willReturn(
        aResponse()
          .withStatus(200)
          .withBody(s"""{
                       |"isAnIndividual" : true,
                       |"individual": {
                       |  "firstName": "First",
                       |  "lastName" : "Last"
                       |}
                       |}""".stripMargin)
      )
    )
  }

  def stubGetAuthOldId: StubMapping = {

    stubFor(get(urlMatching("/auth/authority"))
      .willReturn(
        aResponse()
          .withStatus(200)
          .withBody(
            s"""{
               | "accounts": {
               |  "agent": {
               |   "agentBusinessUtr": "FAKE-UTR"
               |  }
               | },
               | "credentials": {
               |  "gatewayId" : "OLD-GID"
               | }
                }""".stripMargin
          )
      )
    )
  }

  }



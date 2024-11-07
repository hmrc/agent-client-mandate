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

package connectors

import helpers.IntegrationSpec
import uk.gov.hmrc.agentclientmandate.connectors.DefaultTaxEnrolmentConnector
import utils.Stubs

class TaxEnrolmentsConnectorISpec extends IntegrationSpec {

  val connector =  app.injector.instanceOf[DefaultTaxEnrolmentConnector]

  val responseBoth: String =
    """
      |{
      |    "principalGroupIds": [
      |       "c0506dd9-1feb-400a-bf70-6351e1ff7510"
      |    ],
      |    "delegatedGroupIds": [
      |       "c0506dd9-1feb-400a-bf70-6351e1ff7513"
      |    ]
      |}
      |""".stripMargin

  val responseDelegated: String =
    """
      |{
      |    "principalGroupIds": [],
      |    "delegatedGroupIds": [
      |       "c0506dd9-1feb-400a-bf70-6351e1ff7513"
      |    ]
      |}
      |""".stripMargin

  val responsePrincipal: String =
    """
      |{
      |    "principalGroupIds": [
      |       "c0506dd9-1feb-400a-bf70-6351e1ff7510"
      |    ],
      |    "delegatedGroupIds": []
      |}
      |""".stripMargin

  "getGroupsWithEnrolment" should {
    "return a principal groupID when ESP returns both principal and delegated IDs" in {
      Stubs.stubES1Agent(200, responseBoth)
      await(connector.getGroupsWithEnrolment("agentRef")) mustBe Some("c0506dd9-1feb-400a-bf70-6351e1ff7510")
    }

    "return None when ESP returns only delegated IDs" in {
      Stubs.stubES1Agent(200, responseDelegated)
      await(connector.getGroupsWithEnrolment("agentRef")) mustBe None
    }

    "return None when ESP returns 204 status code" in {
      Stubs.stubES1Agent(204, "")
      await(connector.getGroupsWithEnrolment("agentRef")) mustBe None
    }
  }

  "getGroupsWithEnrolmentDelegatedAted" should {
    "return a delegated groupID when ESP returns both principal and delegated IDs" in {
      Stubs.stubES1ATED(200, responseBoth)
      await(connector.getGroupsWithEnrolmentDelegatedAted("atedRef")) mustBe Some("c0506dd9-1feb-400a-bf70-6351e1ff7513")
    }

    "return None when ESP returns only principal IDs" in {
      Stubs.stubES1ATED(200, responsePrincipal)
      await(connector.getGroupsWithEnrolmentDelegatedAted("atedRef")) mustBe None
    }

    "return None when ESP returns 204 status code" in {
      Stubs.stubES1ATED(204, "")
      await(connector.getGroupsWithEnrolmentDelegatedAted("atedRef")) mustBe None
    }
  }
}

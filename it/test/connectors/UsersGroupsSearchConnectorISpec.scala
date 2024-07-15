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
import play.api.libs.json.Json
import uk.gov.hmrc.agentclientmandate.connectors.UsersGroupSearchConnector
import utils.Stubs

class UsersGroupsSearchConnectorISpec extends IntegrationSpec {

  val connector = app.injector.instanceOf[UsersGroupSearchConnector]

  val responseWithAgentCode =
    """
      |{
      |  "_links": [
      |    { "rel": "users", "link": "/groups/:groupdId/users" }
      |  ],
      |  "groupId": ":groupId",
      |  "affinityGroup": "Agent",
      |  "agentCode": "NQJUEJCWT14",
      |  "agentFriendlyName": "JoeBloggs",
      |  "agentId": "?",
      |  "users": [
      |    {
      |      "name": "Joe Doe",
      |      "credId": "0000000246798183",
      |      "email": "abc@abc.com",
      |      "groupId": "groupId",
      |      "credentialRole": "User"
      |    }
      |  ]
      |}
      |""".stripMargin

  val responseNoAgentCode =
    """
      |{
      |  "_links": [
      |    { "rel": "users", "link": "/groups/:groupdId/users" }
      |  ],
      |  "groupId": ":groupId",
      |  "affinityGroup": "Agent",
      |  "agentFriendlyName": "JoeBloggs",
      |  "agentId": "?",
      |  "users": [
      |    {
      |      "name": "Joe Doe",
      |      "credId": "0000000246798183",
      |      "email": "abc@abc.com",
      |      "groupId": "groupId",
      |      "credentialRole": "User"
      |    }
      |  ]
      |}
      |""".stripMargin

  "fetchAgentCode" should {
    "return an agentCode if response from UGS contains an agentCode" in {
      Stubs.stubUGS(203, responseWithAgentCode)
      val result = await(connector.fetchAgentCode("groupId"))
      result mustBe Some("NQJUEJCWT14")
    }

    "return None if response from UGS contains no agentCode" in {
      Stubs.stubUGS(203, responseNoAgentCode)
      val result = await(connector.fetchAgentCode("groupId"))
      result mustBe None
    }

    "return None if response from UGS is not a 203" in {
      Stubs.stubUGS(404, "")
      val result = await(connector.fetchAgentCode("groupId"))
      result mustBe None
    }
  }

}

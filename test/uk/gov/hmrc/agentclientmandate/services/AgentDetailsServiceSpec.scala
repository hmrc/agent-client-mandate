/*
 * Copyright 2016 HM Revenue & Customs
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

import org.mockito.Matchers
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterEach
import org.scalatest.mock.MockitoSugar
import org.scalatestplus.play.{OneServerPerSuite, PlaySpec}
import play.api.libs.json.Json
import uk.gov.hmrc.agentclientmandate.connectors.{AuthConnector, EtmpConnector}
import play.api.test.Helpers._
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

      when(etmpConnectorMock.getAgentDetailsFromEtmp(Matchers.any())) thenReturn {
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

      when(etmpConnectorMock.getAgentDetailsFromEtmp(Matchers.any())) thenReturn {
        Future.successful(successResponseJsonETMP)
      }

      implicit val hc = new HeaderCarrier()
      val result = await(TestAgentDetailsService.getAgentDetails("ac"))
      result.agentName must be("ABC Limited")
    }
  }

  val authConnectorMock = mock[AuthConnector]
  val etmpConnectorMock = mock[EtmpConnector]

  override def beforeEach(): Unit = {
    reset(authConnectorMock)
    reset(etmpConnectorMock)
  }

  object TestAgentDetailsService extends AgentDetailsService {
    override val authConnector = authConnectorMock
    override val etmpConnector = etmpConnectorMock
  }
}

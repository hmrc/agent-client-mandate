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

package uk.gov.hmrc.agentclientmandate.connectors

import org.mockito.Matchers
import org.mockito.Mockito._
import org.scalatest.mock.MockitoSugar
import org.scalatestplus.play.{OneServerPerSuite, PlaySpec}
import org.specs2.specification.BeforeAfterEach
import uk.gov.hmrc.agentclientmandate.models.GsoAdminAllocateAgentXmlInput
import uk.gov.hmrc.play.http._
import uk.gov.hmrc.play.http.ws.{WSGet, WSPost, WSPut}
import play.api.test.Helpers._
import scala.concurrent.ExecutionContext.Implicits.global

import scala.concurrent.Future


class GovernmentGatewayProxyConnectorSpec extends PlaySpec with OneServerPerSuite with MockitoSugar with BeforeAfterEach {

  class MockHttp extends WSGet with WSPost with WSPut {
    override val hooks = NoneRequired
  }

  val mockWSHttp = mock[MockHttp]

  override def before: Any = {
    reset(mockWSHttp)
  }

  object TestGovernmentGatewayProxyConnector extends GovernmentGatewayProxyConnector {
    override val http: HttpGet with HttpPost with HttpPut = mockWSHttp
  }


  "GovernmentGatewayProxyConnector" must {

    "have a service url" in {
      TestGovernmentGatewayProxyConnector.serviceUrl must be("http://localhost:9907")
    }

    "return response with 500" in {

      implicit val hc = new HeaderCarrier()

      when(mockWSHttp.POSTString[HttpResponse](Matchers.any(), Matchers.any(), Matchers.any())(Matchers.any(), Matchers.any()))
        .thenReturn(Future.successful(HttpResponse(500)))

      val result = await(TestGovernmentGatewayProxyConnector.allocateAgent(GsoAdminAllocateAgentXmlInput(List(), "", "")))
      result.status must be(500)
    }

    "return response with 502" in {

      implicit val hc = new HeaderCarrier()

      when(mockWSHttp.POSTString[HttpResponse](Matchers.any(), Matchers.any(), Matchers.any())(Matchers.any(), Matchers.any()))
        .thenReturn(Future.successful(HttpResponse(BAD_GATEWAY)))

      val result = await(TestGovernmentGatewayProxyConnector.allocateAgent(GsoAdminAllocateAgentXmlInput(List(), "", "")))
      result.status must be(502)
    }

    "return response with 404" in {

      implicit val hc = new HeaderCarrier()

      when(mockWSHttp.POSTString[HttpResponse](Matchers.any(), Matchers.any(), Matchers.any())(Matchers.any(), Matchers.any()))
        .thenReturn(Future.successful(HttpResponse(NOT_FOUND)))

      val result = await(TestGovernmentGatewayProxyConnector.allocateAgent(GsoAdminAllocateAgentXmlInput(List(), "", "")))
      result.status must be(404)
    }

    "return response with 200" in {

      implicit val hc = new HeaderCarrier()

      when(mockWSHttp.POSTString[HttpResponse](Matchers.any(), Matchers.any(), Matchers.any())(Matchers.any(), Matchers.any()))
        .thenReturn(Future.successful(HttpResponse(OK)))

      val result = await(TestGovernmentGatewayProxyConnector.allocateAgent(GsoAdminAllocateAgentXmlInput(List(), "", "")))
      result.status must be(200)
    }
  }
}

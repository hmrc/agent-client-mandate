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
import org.scalatest.BeforeAndAfterEach
import org.scalatest.mock.MockitoSugar
import org.scalatestplus.play.{OneServerPerSuite, PlaySpec}
import play.api.libs.json.Json
import play.api.test.Helpers._
import uk.gov.hmrc.play.http._
import uk.gov.hmrc.play.http.ws.{WSGet, WSPost, WSPut}

import scala.concurrent.Future

class AuthConnectorSpec extends PlaySpec with OneServerPerSuite with MockitoSugar with BeforeAndAfterEach {

  class MockHttp extends WSGet with WSPost with WSPut {
    override val hooks = NoneRequired
  }

  val mockWSHttp = mock[MockHttp]

  override def beforeEach: Unit = {
    reset(mockWSHttp)
  }

  "AuthConnector" must {
    "return json response when authority found" in {
      val successResponseJson = Json.parse( """{"accounts": {"agent": {"agentCode":"AGENT-123", "agentBusinessUtr":"JARN1234567"}}}""")
      when(mockWSHttp.GET[HttpResponse](Matchers.any())(Matchers.any(), Matchers.any()))
        .thenReturn(Future.successful(HttpResponse(OK, Some(successResponseJson))))

      val result = await(TestAuthConnector.getAuthority()(new HeaderCarrier()))
      (result \ "accounts" \ "agent" \ "agentBusinessUtr").as[String] must be("JARN1234567")
    }

    "throw exception when response is not OK" in {
      when(mockWSHttp.GET[HttpResponse](Matchers.any())(Matchers.any(), Matchers.any()))
        .thenReturn(Future.successful(HttpResponse(BAD_REQUEST)))

      val thrown = the[RuntimeException] thrownBy await(TestAuthConnector.getAuthority()(new HeaderCarrier()))
      thrown.getMessage must include("No authority found")
    }
  }

  object TestAuthConnector extends AuthConnector {
    val http: HttpGet with HttpPost with HttpPut = mockWSHttp
  }

}

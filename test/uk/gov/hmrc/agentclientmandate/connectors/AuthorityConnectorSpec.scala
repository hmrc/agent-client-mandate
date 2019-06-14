/*
 * Copyright 2019 HM Revenue & Customs
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

import org.mockito.ArgumentMatchers._
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterEach
import org.scalatest.mockito.MockitoSugar
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.libs.json.Json
import play.api.test.Helpers._
import uk.gov.hmrc.agentclientmandate.utils.Generators._
import uk.gov.hmrc.http._
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.bootstrap.http.HttpClient

import scala.concurrent.Future

class AuthorityConnectorSpec extends PlaySpec with GuiceOneServerPerSuite with MockitoSugar with BeforeAndAfterEach {

  val mockWSHttp: CoreGet = mock[HttpClient]
  val mockAuditConnector: AuditConnector = mock[AuditConnector]

  override def beforeEach: Unit = {
    reset(mockWSHttp, mockAuditConnector)
  }

  val testServiceUrl = "test"

  trait Setup {
    class TestAuthorityConnector extends AuthorityConnector {
      val http: CoreGet = mockWSHttp

      override def serviceUrl: String = testServiceUrl
      override val auditConnector: AuditConnector = mockAuditConnector
    }

    val connector = new TestAuthorityConnector
  }

  "AuthConnector" must {
    "return json response when authority found" in new Setup {
      val agentBusinessUtr = agentBusinessUtrGen.sample.get
      val successResponseJson = Json.parse( s"""{"accounts": {"agent": {"agentCode":"${agentCodeGen.sample.get}", "agentBusinessUtr":"${agentBusinessUtr}"}}}""")
      when(mockWSHttp.GET[HttpResponse](any())(any(), any(), any()))
        .thenReturn(Future.successful(HttpResponse(OK, Some(successResponseJson))))

      val result = await(connector.getAuthority()(new HeaderCarrier()))
      (result \ "accounts" \ "agent" \ "agentBusinessUtr").as[String] must be(agentBusinessUtr)
    }

    "throw exception when response is not OK" in new Setup {
      when(mockWSHttp.GET[HttpResponse](any())(any(), any(), any()))
        .thenReturn(Future.successful(HttpResponse(BAD_REQUEST)))

      val thrown = the[RuntimeException] thrownBy await(connector.getAuthority()(HeaderCarrier()))
      thrown.getMessage must include("No authority found")
    }
  }
}

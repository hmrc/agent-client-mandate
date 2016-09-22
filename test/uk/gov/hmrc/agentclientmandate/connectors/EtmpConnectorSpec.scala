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
import uk.gov.hmrc.play.http.{HttpGet, HttpPost, HttpPut, HttpResponse}
import uk.gov.hmrc.play.http.ws.{WSGet, WSPost, WSPut}
import play.api.test.Helpers._

import scala.concurrent.Future


class EtmpConnectorSpec extends PlaySpec with OneServerPerSuite with MockitoSugar with BeforeAndAfterEach {

  class MockHttp extends WSGet with WSPost with WSPut {
    override val hooks = NoneRequired
  }

  val mockWSHttp = mock[MockHttp]

  override def beforeEach = {
    reset(mockWSHttp)
  }

  "EtmpConnector" must {
    "return response" in {
      when(mockWSHttp.GET[HttpResponse](Matchers.any())(Matchers.any(), Matchers.any()))
        .thenReturn(Future.successful(HttpResponse(OK, Some(Json.parse("""{"isAnIndividual":false}""")))))

      val result = await(TestEtmpConnector.getDetailsFromEtmp("ABC"))
      (result \ "isAnIndividual").as[Boolean] must be(false)
    }

    "throw exception when response is not OK" in {
      when(mockWSHttp.GET[HttpResponse](Matchers.any())(Matchers.any(), Matchers.any()))
        .thenReturn(Future.successful(HttpResponse(BAD_REQUEST)))

      val thrown = the[RuntimeException] thrownBy await(TestEtmpConnector.getDetailsFromEtmp("ABC"))
      thrown.getMessage must include("No ETMP details found")
    }
  }

  object TestEtmpConnector extends EtmpConnector {
    val urlHeaderEnvironment: String = ""
    val urlHeaderAuthorization: String = ""
    val http: HttpGet with HttpPost with HttpPut = mockWSHttp
  }

}

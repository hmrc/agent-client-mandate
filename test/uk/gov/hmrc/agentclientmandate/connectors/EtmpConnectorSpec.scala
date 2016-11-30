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

import java.util.UUID

import org.mockito.Matchers
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterEach
import org.scalatest.mock.MockitoSugar
import org.scalatestplus.play.{OneServerPerSuite, PlaySpec}
import play.api.libs.json.{JsValue, Json}
import uk.gov.hmrc.play.http._
import uk.gov.hmrc.play.http.ws.{WSGet, WSPost, WSPut}
import play.api.test.Helpers._
import uk.gov.hmrc.agentclientmandate.metrics.Metrics
import uk.gov.hmrc.agentclientmandate.models.{EtmpAtedAgentClientRelationship, EtmpRelationship}
import uk.gov.hmrc.agentclientmandate.utils.SessionUtils
import uk.gov.hmrc.play.http.logging.SessionId

import scala.concurrent.Future


class EtmpConnectorSpec extends PlaySpec with OneServerPerSuite with MockitoSugar with BeforeAndAfterEach {

  class MockHttp extends WSGet with WSPost with WSPut {
    override val hooks = NoneRequired
  }

  val mockWSHttp = mock[MockHttp]

  override def beforeEach: Unit = {
    reset(mockWSHttp)
  }

  "EtmpConnector" must {
    "getDetails" must {
      "return valid response, for ARN as identifier type" in {
        when(mockWSHttp.GET[HttpResponse](Matchers.any())(Matchers.any(), Matchers.any()))
          .thenReturn(Future.successful(HttpResponse(OK, Some(Json.parse("""{"isAnIndividual":false}""")))))

        val result = await(TestEtmpConnector.getDetails("ABC", "arn"))
        (result \ "isAnIndividual").as[Boolean] must be(false)
      }

      "return valid response, for SafeId as identifier type" in {
        when(mockWSHttp.GET[HttpResponse](Matchers.any())(Matchers.any(), Matchers.any()))
          .thenReturn(Future.successful(HttpResponse(OK, Some(Json.parse("""{"isAnIndividual":false}""")))))

        val result = await(TestEtmpConnector.getDetails("ABC", "safeid"))
        (result \ "isAnIndividual").as[Boolean] must be(false)
      }

      "return valid response, for UTR as identifier type" in {
        when(mockWSHttp.GET[HttpResponse](Matchers.any())(Matchers.any(), Matchers.any()))
          .thenReturn(Future.successful(HttpResponse(OK, Some(Json.parse("""{"isAnIndividual":false}""")))))

        val result = await(TestEtmpConnector.getDetails("ABC", "utr"))
        (result \ "isAnIndividual").as[Boolean] must be(false)
      }

      "throw exception when Invalid identifier type is passed" in {
        val thrown = the[RuntimeException] thrownBy await(TestEtmpConnector.getDetails("ABC", "INVALID"))
        thrown.getMessage must include("Unexpected identifier type supplied - INVALID")
      }

      "throw exception when response is not OK" in {
        when(mockWSHttp.GET[HttpResponse](Matchers.any())(Matchers.any(), Matchers.any()))
          .thenReturn(Future.successful(HttpResponse(BAD_REQUEST)))

        val thrown = the[RuntimeException] thrownBy await(TestEtmpConnector.getDetails("ABC", "arn"))
        thrown.getMessage must include("No ETMP details found")
      }
    }

    "maintainAtedRelationship" must {
      "return valid response, if create/update relationship is successful in ETMP" in {
        val successResponse = Json.parse( """{"processingDate" :  "2014-12-17T09:30:47Z"}""")
        implicit val hc = new HeaderCarrier(sessionId = Some(SessionId(s"session-${UUID.randomUUID}")))
        when(mockWSHttp.POST[JsValue, HttpResponse](Matchers.any(), Matchers.any(), Matchers.any())(Matchers.any(), Matchers.any(), Matchers.any()))
          .thenReturn(Future.successful(HttpResponse(OK, responseJson = Some(successResponse))))

        val etmpRelationship = EtmpRelationship(action = "authorise", isExclusiveAgent = Some(true))
        val agentClientRelationship = EtmpAtedAgentClientRelationship(SessionUtils.getUniqueAckNo, "ATED-123", "AGENT-123", etmpRelationship)
        val response = await(TestEtmpConnector.maintainAtedRelationship(agentClientRelationship))
        response.status must be(OK)
        response.json must be(successResponse)
      }

      "Check for a failure response when we try to create/update ATED relation in ETMP" in {
        val failureResponse = Json.parse( """{"Reason" : "Service Unavailable"}""")
        implicit val hc = new HeaderCarrier(sessionId = Some(SessionId(s"session-${UUID.randomUUID}")))
        when(mockWSHttp.POST[JsValue, HttpResponse](Matchers.any(), Matchers.any(), Matchers.any())(Matchers.any(), Matchers.any(), Matchers.any()))
          .thenReturn(Future.successful(HttpResponse(SERVICE_UNAVAILABLE, responseJson = Some(failureResponse))))

        val etmpRelationship = EtmpRelationship(action = "authorise", isExclusiveAgent = Some(true))
        val agentClientRelationship = EtmpAtedAgentClientRelationship(SessionUtils.getUniqueAckNo, "ATED-123", "AGENT-123", etmpRelationship)
        val result = TestEtmpConnector.maintainAtedRelationship(agentClientRelationship)
        val response = the[RuntimeException] thrownBy await(result)
        response.getMessage must be("ETMP call failed")
      }
    }

    "getAtedSubscriptionDetails" must {
      "return valid response, if success response received from ETMP" in {
        val successResponse = Json.parse( """{"safeId" :  "safe-id"}""")
        implicit val hc = new HeaderCarrier(sessionId = Some(SessionId(s"session-${UUID.randomUUID}")))
        when(mockWSHttp.GET[HttpResponse](Matchers.any())(Matchers.any(), Matchers.any()))
          .thenReturn(Future.successful(HttpResponse(OK, responseJson = Some(successResponse))))

        val response = await(TestEtmpConnector.getAtedSubscriptionDetails("ated-ref-num"))
        response must be(successResponse)
      }

      "throws error, if response status is not OK from ETMP" in {
        val failureResponse = Json.parse( """{"Reason" : "Service Unavailable"}""")
        implicit val hc = new HeaderCarrier(sessionId = Some(SessionId(s"session-${UUID.randomUUID}")))
        when(mockWSHttp.GET[HttpResponse](Matchers.any())(Matchers.any(), Matchers.any()))
          .thenReturn(Future.successful(HttpResponse(SERVICE_UNAVAILABLE, responseJson = Some(failureResponse))))

        val result = TestEtmpConnector.getAtedSubscriptionDetails("ated-ref-num")
        val response = the[RuntimeException] thrownBy await(result)
        response.getMessage must be("Error in getting ATED subscription details from ETMP")
      }
    }

  }

  object TestEtmpConnector extends EtmpConnector {
    override val urlHeaderEnvironment: String = ""
    override val urlHeaderAuthorization: String = ""
    override val http: HttpGet with HttpPost with HttpPut = mockWSHttp
    override val metrics = Metrics
  }

}

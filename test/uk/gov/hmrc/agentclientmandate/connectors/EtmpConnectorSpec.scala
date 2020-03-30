/*
 * Copyright 2020 HM Revenue & Customs
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

import com.codahale.metrics.Timer
import org.mockito.ArgumentMatchers._
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterEach
import org.scalatest.mockito.MockitoSugar
import org.scalatestplus.play.PlaySpec
import play.api.libs.json.{JsValue, Json}
import play.api.test.Helpers._
import uk.gov.hmrc.agentclientmandate.metrics.ServiceMetrics
import uk.gov.hmrc.agentclientmandate.models.{EtmpAtedAgentClientRelationship, EtmpRelationship}
import uk.gov.hmrc.agentclientmandate.utils.SessionUtils
import uk.gov.hmrc.http._
import uk.gov.hmrc.http.logging.SessionId
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.bootstrap.http.HttpClient

import scala.concurrent.Future


class EtmpConnectorSpec extends PlaySpec with MockitoSugar with BeforeAndAfterEach {

  val mockWSHttp: HttpClient = mock[HttpClient]
  val mockMetrics: ServiceMetrics = mock[ServiceMetrics]
  val mockAuditConnector: AuditConnector = mock[AuditConnector]

  override def beforeEach: Unit = {
    reset(mockWSHttp, mockMetrics, mockAuditConnector)

    when(mockMetrics.startTimer(any()))
      .thenReturn(new Timer().time)
  }

  val mockUrl = "test"

  trait Setup {

    class TestEtmpConnector extends EtmpConnector {
      override val urlHeaderEnvironment: String = ""
      override val urlHeaderAuthorization: String = ""
      override val http: CoreGet with CorePost = mockWSHttp
      override val metrics = mockMetrics
      override val etmpUrl: String = mockUrl
      override val auditConnector: AuditConnector = mockAuditConnector
    }

    val connector = new TestEtmpConnector
  }

  "EtmpConnector" must {
    "getDetails" must {
      "return valid response, for ARN as identifier type" in new Setup {
        when(mockWSHttp.GET[HttpResponse](any())(any(), any(), any()))
          .thenReturn(Future.successful(HttpResponse(OK, Some(Json.parse("""{"isAnIndividual":false}""")))))

        val result = await(connector.getRegistrationDetails("ABC", "arn"))
        (result \ "isAnIndividual").as[Boolean] must be(false)
      }

      "return valid response, for SafeId as identifier type" in new Setup {
        when(mockWSHttp.GET[HttpResponse](any())(any(), any(), any()))
          .thenReturn(Future.successful(HttpResponse(OK, Some(Json.parse("""{"isAnIndividual":false}""")))))

        val result = await(connector.getRegistrationDetails("ABC", "safeid"))
        (result \ "isAnIndividual").as[Boolean] must be(false)
      }

      "return valid response, for UTR as identifier type" in new Setup {
        when(mockWSHttp.GET[HttpResponse](any())(any(), any(), any()))
          .thenReturn(Future.successful(HttpResponse(OK, Some(Json.parse("""{"isAnIndividual":false}""")))))

        val result = await(connector.getRegistrationDetails("ABC", "utr"))
        (result \ "isAnIndividual").as[Boolean] must be(false)
      }

      "throw exception when Invalid identifier type is passed" in new Setup {
        val thrown = the[RuntimeException] thrownBy await(connector.getRegistrationDetails("ABC", "INVALID"))
        thrown.getMessage must include("Unexpected identifier type supplied - INVALID")
      }

      "throw exception when response is not OK" in new Setup {
        when(mockWSHttp.GET[HttpResponse](any())(any(), any(), any()))
          .thenReturn(Future.successful(HttpResponse(BAD_REQUEST)))

        val thrown = the[RuntimeException] thrownBy await(connector.getRegistrationDetails("ABC", "arn"))
        thrown.getMessage must include("No ETMP details found")
      }
    }

    "maintainAtedRelationship" must {
      "return valid response, if create/update relationship is successful in ETMP" in new Setup {
        val successResponse = Json.parse( """{"processingDate" :  "2014-12-17T09:30:47Z"}""")
        implicit val hc = new HeaderCarrier(sessionId = Some(SessionId(s"session-${UUID.randomUUID}")))
        when(mockWSHttp.POST[JsValue, HttpResponse](any(), any(), any())(any(), any(), any(), any()))
          .thenReturn(Future.successful(HttpResponse(OK, responseJson = Some(successResponse))))

        val etmpRelationship = EtmpRelationship(action = "authorise", isExclusiveAgent = Some(true))
        val agentClientRelationship = EtmpAtedAgentClientRelationship(SessionUtils.getUniqueAckNo, "ATED-123", "AGENT-123", etmpRelationship)
        val response = await(connector.maintainAtedRelationship(agentClientRelationship))
        response.status must be(OK)
        response.json must be(successResponse)
      }

      "Check for a failure response when we try to create/update ATED relation in ETMP" in new Setup {
        val failureResponse = Json.parse( """{"Reason" : "Service Unavailable"}""")
        implicit val hc = new HeaderCarrier(sessionId = Some(SessionId(s"session-${UUID.randomUUID}")))
        when(mockWSHttp.POST[JsValue, HttpResponse](any(), any(), any())(any(), any(), any(), any()))
          .thenReturn(Future.successful(HttpResponse(INTERNAL_SERVER_ERROR, responseJson = Some(failureResponse))))

        val etmpRelationship = EtmpRelationship(action = "authorise", isExclusiveAgent = Some(true))
        val agentClientRelationship = EtmpAtedAgentClientRelationship(SessionUtils.getUniqueAckNo, "ATED-123", "AGENT-123", etmpRelationship)
        val response = await(connector.maintainAtedRelationship(agentClientRelationship))
        response.status must be(INTERNAL_SERVER_ERROR)
      }
    }

    "getAtedSubscriptionDetails" must {
      "return valid response, if success response received from ETMP" in new Setup {
        val successResponse = Json.parse( """{"safeId" :  "safe-id"}""")
        implicit val hc = new HeaderCarrier(sessionId = Some(SessionId(s"session-${UUID.randomUUID}")))
        when(mockWSHttp.GET[HttpResponse](any())(any(), any(), any()))
          .thenReturn(Future.successful(HttpResponse(OK, responseJson = Some(successResponse))))

        val response = await(connector.getAtedSubscriptionDetails("ated-ref-num"))
        response must be(successResponse)
      }

      "throws error, if response status is not OK from ETMP" in new Setup {
        val failureResponse = Json.parse( """{"Reason" : "Service Unavailable"}""")
        implicit val hc = new HeaderCarrier(sessionId = Some(SessionId(s"session-${UUID.randomUUID}")))
        when(mockWSHttp.GET[HttpResponse](any())(any(), any(), any()))
          .thenReturn(Future.successful(HttpResponse(SERVICE_UNAVAILABLE, responseJson = Some(failureResponse))))

        val result = connector.getAtedSubscriptionDetails("ated-ref-num")
        val response = the[RuntimeException] thrownBy await(result)
        response.getMessage must be("Error in getting ATED subscription details from ETMP")
      }
    }

  }

}

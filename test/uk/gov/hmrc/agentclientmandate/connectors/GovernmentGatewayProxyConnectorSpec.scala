/*
 * Copyright 2018 HM Revenue & Customs
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

//package uk.gov.hmrc.agentclientmandate.connectors
//
//import org.mockito.Matchers
//import org.mockito.Mockito._
//import org.scalatest.BeforeAndAfterEach
//import org.scalatest.mock.MockitoSugar
//import org.scalatestplus.play.{OneServerPerSuite, PlaySpec}
//import play.api.test.Helpers._
//import uk.gov.hmrc.agentclientmandate.metrics.Metrics
//import uk.gov.hmrc.agentclientmandate.models.{GsoAdminAllocateAgentXmlInput, GsoAdminDeallocateAgentXmlInput}
//import uk.gov.hmrc.http._
//
//import scala.concurrent.Future
//
//
//class GovernmentGatewayProxyConnectorSpec extends PlaySpec with OneServerPerSuite with MockitoSugar with BeforeAndAfterEach {
//
//  trait MockedVerbs extends CorePost
//  val mockWSHttp: CorePost = mock[MockedVerbs]
//
//  override def beforeEach = {
//    reset(mockWSHttp)
//  }
//
//  object TestGovernmentGatewayProxyConnector extends GovernmentGatewayProxyConnector {
//    override val http: CorePost = mockWSHttp
//    val metrics = Metrics
//  }
//
//
//  "GovernmentGatewayProxyConnector" must {
//
//    "have a service url" in {
//      TestGovernmentGatewayProxyConnector.serviceUrl must be("http://localhost:9907")
//    }
//
//    "return response with 502" when {
//
//      "call to allocate agent return a BAD_GATEWAY" in {
//
//        implicit val hc = new HeaderCarrier()
//
//        when(mockWSHttp.POSTString[HttpResponse](Matchers.any(), Matchers.any(), Matchers.any())(Matchers.any(), Matchers.any(), Matchers.any()))
//          .thenReturn(Future.successful(HttpResponse(BAD_GATEWAY)))
//
//        val result = await(TestGovernmentGatewayProxyConnector.allocateAgent(GsoAdminAllocateAgentXmlInput(List(), "", "")))
//        result.status must be(502)
//      }
//
//      "call to deallocate agent return a BAD_GATEWAY" in {
//
//        implicit val hc = new HeaderCarrier()
//
//        when(mockWSHttp.POSTString[HttpResponse](Matchers.any(), Matchers.any(), Matchers.any())(Matchers.any(), Matchers.any(), Matchers.any()))
//          .thenReturn(Future.successful(HttpResponse(BAD_GATEWAY)))
//
//        val result = await(TestGovernmentGatewayProxyConnector.deAllocateAgent(GsoAdminDeallocateAgentXmlInput(List(), "", "")))
//        result.status must be(502)
//      }
//    }
//
//    "return response with 404" when {
//
//      "call to allocate agent return a NOT_FOUND" in {
//
//        implicit val hc = new HeaderCarrier()
//
//        when(mockWSHttp.POSTString[HttpResponse](Matchers.any(), Matchers.any(), Matchers.any())(Matchers.any(), Matchers.any(), Matchers.any()))
//          .thenReturn(Future.successful(HttpResponse(NOT_FOUND)))
//
//        val result = await(TestGovernmentGatewayProxyConnector.allocateAgent(GsoAdminAllocateAgentXmlInput(List(), "", "")))
//        result.status must be(404)
//      }
//
//      "call to deallocate agent return a NOT_FOUND" in {
//
//        implicit val hc = new HeaderCarrier()
//
//        when(mockWSHttp.POSTString[HttpResponse](Matchers.any(), Matchers.any(), Matchers.any())(Matchers.any(), Matchers.any(), Matchers.any()))
//          .thenReturn(Future.successful(HttpResponse(NOT_FOUND)))
//
//        val result = await(TestGovernmentGatewayProxyConnector.deAllocateAgent(GsoAdminDeallocateAgentXmlInput(List(), "", "")))
//        result.status must be(404)
//      }
//
//    }
//
//    "return response with 200" when {
//
//      "call to allocate agent return a OK" in {
//
//        implicit val hc = new HeaderCarrier()
//
//        when(mockWSHttp.POSTString[HttpResponse](Matchers.any(), Matchers.any(), Matchers.any())(Matchers.any(), Matchers.any(), Matchers.any()))
//          .thenReturn(Future.successful(HttpResponse(OK)))
//
//        val result = await(TestGovernmentGatewayProxyConnector.allocateAgent(GsoAdminAllocateAgentXmlInput(List(), "", "")))
//        result.status must be(200)
//      }
//
//      "call to deallocate agent return a OK" in {
//
//        implicit val hc = new HeaderCarrier()
//
//        when(mockWSHttp.POSTString[HttpResponse](Matchers.any(), Matchers.any(), Matchers.any())(Matchers.any(), Matchers.any(), Matchers.any()))
//          .thenReturn(Future.successful(HttpResponse(OK)))
//
//        val result = await(TestGovernmentGatewayProxyConnector.deAllocateAgent(GsoAdminDeallocateAgentXmlInput(List(), "", "")))
//        result.status must be(200)
//      }
//    }
//  }
//
//}

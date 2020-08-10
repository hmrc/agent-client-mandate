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

import com.codahale.metrics.Timer
import org.mockito.ArgumentMatchers._
import org.mockito.Mockito.{reset, when}
import org.scalatest.BeforeAndAfterEach
import org.scalatest.mockito.MockitoSugar
import org.scalatestplus.play.PlaySpec
import play.api.libs.json.{JsValue, Json}
import play.api.test.Helpers._
import uk.gov.hmrc.agentclientmandate.metrics.ServiceMetrics
import uk.gov.hmrc.agentclientmandate.models.NewEnrolment
import uk.gov.hmrc.agentclientmandate.utils.Generators._
import uk.gov.hmrc.http._
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.http.HttpClient

import scala.concurrent.Future

class TaxEnrolmentsConnectorSpec extends PlaySpec with MockitoSugar with BeforeAndAfterEach {

  val mockWSHttp: HttpClient = mock[HttpClient]
  val mockMetrics: ServiceMetrics = mock[ServiceMetrics]
  val mockAuditConnector: AuditConnector = mock[AuditConnector]
  val agentCode = agentCodeGen.sample.get
  val clientID = clientIdGen.sample.get
  val groupID = "group-ID"
  val newEnrolment = newEnrolmentGen.sample.get
  val userType = "client"

  override def beforeEach: Unit = {
    reset(mockWSHttp, mockMetrics, mockAuditConnector)

    when(mockMetrics.startTimer(any()))
      .thenReturn(new Timer().time)
  }

  trait MockedVerbs extends CoreDelete with CorePost

  trait Setup {

    val connector = new TestTaxEnrolmentsConnector

    class TestTaxEnrolmentsConnector extends TaxEnrolmentConnector {
      override val http: CoreDelete with CorePost with CoreGet = mockWSHttp
      override val taxEnrolmentsUrl: String = ""
      override val metrics: ServiceMetrics = mockMetrics
      override val auditConnector: AuditConnector = mockAuditConnector

      override def serviceUrl: String = ""
      override def enrolmentStoreProxyURL: String = ""
    }

  }

  "TaxEnrolmentsConnector" must {
    implicit val hc = HeaderCarrier()

    "create allocation" in new Setup {
      val enrolment = NewEnrolment(newEnrolment)
      when(mockWSHttp.POST[JsValue, HttpResponse](any(), any(), any())(any(), any(), any(), any())).
        thenReturn(Future.successful(HttpResponse(CREATED, None)))
      val result = await(connector.allocateAgent(enrolment, groupID, clientID, agentCode))
      result.status mustBe CREATED
    }

    "create allocation error code" in new Setup {
      val enrolment = NewEnrolment(newEnrolment)
      when(mockWSHttp.POST[JsValue, HttpResponse](any(), any(), any())(any(), any(), any(), any())).
        thenReturn(Future.successful(HttpResponse(INTERNAL_SERVER_ERROR, None)))
      val result = await(connector.allocateAgent(enrolment, groupID, clientID, agentCode))
      result.status mustBe INTERNAL_SERVER_ERROR
    }

    "delete allocation" in new Setup {

      val successResponse = Json.parse(
        s"""
           |{
           |
           |    "principalGroupIds":[
           |
           |        "FF5E2869-C291-446C-826F-8A8CF6B8D631"
           |
           |    ]
           |    ,
           |
           |    "delegatedGroupIds":[
           |
           |    ]
           |
           |}
             """.stripMargin
      )

      when(mockWSHttp.DELETE[HttpResponse](any(), any())(any(), any(), any())).
        thenReturn(Future.successful(HttpResponse(NO_CONTENT, None)))
      when(mockWSHttp.GET[HttpResponse](any())(any(), any(), any()))
        .thenReturn(Future.successful(HttpResponse(OK, Some(successResponse))))

      val result = await(connector.deAllocateAgent(groupID, clientID, agentCode, userType))
      result.status mustBe NO_CONTENT
    }

    "delete allocation error code" in new Setup {
      val successResponse = Json.parse(
        s"""
           |{
           |
           |    "principalGroupIds":[
           |
           |        "FF5E2869-C291-446C-826F-8A8CF6B8D631"
           |
           |    ]
           |    ,
           |
           |    "delegatedGroupIds":[
           |
           |    ]
           |
           |}
             """.stripMargin
      )

      when(mockWSHttp.DELETE[HttpResponse](any(), any())(any(), any(), any())).
        thenReturn(Future.successful(HttpResponse(INTERNAL_SERVER_ERROR, None)))
      when(mockWSHttp.GET[HttpResponse](any())(any(), any(), any()))
        .thenReturn(Future.successful(HttpResponse(OK, Some(successResponse))))

      val result = await(connector.deAllocateAgent(groupID, clientID, agentCode, userType))
      result.status mustBe INTERNAL_SERVER_ERROR
    }

    "getGroupsWithEnrolment" must {
      "returns the agent groupID when given an agent reference number" in new Setup {
        val agentGroupID = Some("FF5E2869-C291-446C-826F-8A8CF6B8D631")
        val successResponse = Json.parse(
         s"""
            |{
            |
            |    "principalGroupIds":[
            |
            |        "FF5E2869-C291-446C-826F-8A8CF6B8D631"
            |
            |    ]
            |    ,
            |
            |    "delegatedGroupIds":[
            |
            |    ]
            |
            |}
             """.stripMargin
        )
        when(mockWSHttp.GET[HttpResponse](any())(any(), any(), any()))
          .thenReturn(Future.successful(HttpResponse(OK, Some(successResponse)) ))
        val response = await(connector.getGroupsWithEnrolment("agentRefNum"))
        response must be (agentGroupID)
      }

      "return an exception when unable to return the agent groupID" in new Setup {
        intercept[RuntimeException] {
          when(mockWSHttp.GET[HttpResponse](any())(any(), any(), any()))
            .thenReturn(Future.successful(HttpResponse(INTERNAL_SERVER_ERROR, None)))
          val result = await(connector.getGroupsWithEnrolment("agentRefNum"))
          val response = the[RuntimeException] thrownBy result
          response.getMessage must include("Error retrieving agent group ID")
        }
      }
    }
  }

}
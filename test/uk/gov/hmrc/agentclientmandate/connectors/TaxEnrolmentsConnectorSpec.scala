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
import play.api.libs.json.JsValue
import play.api.test.Helpers.{CREATED, _}
import uk.gov.hmrc.agentclientmandate.metrics.ServiceMetrics
import uk.gov.hmrc.agentclientmandate.models.NewEnrolment
import uk.gov.hmrc.agentclientmandate.utils.Generators._
import uk.gov.hmrc.http._
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.bootstrap.http.HttpClient

import scala.concurrent.Future

class TaxEnrolmentsConnectorSpec extends PlaySpec with MockitoSugar with BeforeAndAfterEach {

  val mockWSHttp: HttpClient = mock[HttpClient]
  val mockMetrics: ServiceMetrics = mock[ServiceMetrics]
  val mockAuditConnector: AuditConnector = mock[AuditConnector]
  val agentCode = agentCodeGen.sample.get
  val clientID = clientIdGen.sample.get
  val newEnrolment = newEnrolmentGen.sample.get

  override def beforeEach: Unit = {
    reset(mockWSHttp, mockMetrics, mockAuditConnector)

    when(mockMetrics.startTimer(any()))
      .thenReturn(new Timer().time)
  }

  trait MockedVerbs extends CoreDelete with CorePost

  trait Setup {

    val connector = new TestTaxEnrolmentsConnector

    class TestTaxEnrolmentsConnector extends TaxEnrolmentConnector {
      override val http: CoreDelete with CorePost = mockWSHttp
      override val enrolmentUrl: String = ""
      override val metrics: ServiceMetrics = mockMetrics
      override val auditConnector: AuditConnector = mockAuditConnector

      override def serviceUrl: String = ""
    }

  }

  "TaxEnrolmentsConnector" must {
    implicit val hc = HeaderCarrier()

    "create allocation" in new Setup {
      val enrolment = NewEnrolment(newEnrolment)
      when(mockWSHttp.POST[JsValue, HttpResponse](any(), any(), any())(any(), any(), any(), any())).
        thenReturn(Future.successful(HttpResponse(CREATED, responseJson = None)))
      val result = await(connector.allocateAgent(enrolment, "group", clientID, agentCode))
      result.status mustBe CREATED
    }

    "create allocation error code" in new Setup {
      val enrolment = NewEnrolment(newEnrolment)
      when(mockWSHttp.POST[JsValue, HttpResponse](any(), any(), any())(any(), any(), any(), any())).
        thenReturn(Future.successful(HttpResponse(INTERNAL_SERVER_ERROR, responseJson = None)))
      val result = await(connector.allocateAgent(enrolment, "group", clientID, agentCode))
      result.status mustBe INTERNAL_SERVER_ERROR
    }

    "delete allocation" in new Setup {
      when(mockWSHttp.DELETE[HttpResponse](any(), any())(any(), any(), any())).
        thenReturn(Future.successful(HttpResponse(NO_CONTENT, responseJson = None)))
      val result = await(connector.deAllocateAgent("group", clientID, agentCode))
      result.status mustBe NO_CONTENT
    }

    "delete allocation error code" in new Setup {
      when(mockWSHttp.DELETE[HttpResponse](any(), any())(any(), any(), any())).
        thenReturn(Future.successful(HttpResponse(INTERNAL_SERVER_ERROR, responseJson = None)))
      val result = await(connector.deAllocateAgent("group", clientID, agentCode))
      result.status mustBe INTERNAL_SERVER_ERROR
    }
  }

}
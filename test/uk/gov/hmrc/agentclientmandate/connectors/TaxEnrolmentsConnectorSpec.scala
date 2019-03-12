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

import org.mockito.ArgumentMatchers
import org.mockito.ArgumentMatchers._
import org.mockito.Mockito.{reset, when}
import org.scalatest.BeforeAndAfterEach
import org.scalatest.mockito.MockitoSugar
import org.scalatestplus.play.{OneServerPerSuite, PlaySpec}
import play.api.libs.json.{JsValue, Json}
import play.api.test.Helpers.CREATED
import uk.gov.hmrc.agentclientmandate.metrics.Metrics
import uk.gov.hmrc.agentclientmandate.models.NewEnrolment
import uk.gov.hmrc.http._
import play.api.test.Helpers._
import play.api.test._
import uk.gov.hmrc.agentclientmandate.utils.Generators._

import scala.concurrent.Future

class TaxEnrolmentsConnectorSpec extends PlaySpec with OneServerPerSuite with MockitoSugar with BeforeAndAfterEach {

  trait MockedVerbs extends  CoreDelete with CorePost
  val mockWSHttp: CoreDelete with CorePost = mock[MockedVerbs]

  object TestTaxEnrolmentsConnector extends TaxEnrolmentConnector {

    override val http: CoreDelete with CorePost = mockWSHttp

    override val enrolmentUrl: String = ""

    override def serviceUrl: String = ""

    override val metrics = Metrics


  }

  override def beforeEach = {
    reset(mockWSHttp)
  }

  val agentCode = agentCodeGen.sample.get
  val clientID = clientIdGen.sample.get
  val newEnrolment = newEnrolmentGen.sample.get

  "TaxEnrolmentsConnector" must {
    implicit val hc = HeaderCarrier()

    "create allocation" in {
      val enrolment = NewEnrolment(newEnrolment)
     when(mockWSHttp.POST[JsValue, HttpResponse](any(), any(), any())(any(), any(), any(), any())).
        thenReturn(Future.successful(HttpResponse(CREATED, responseJson = None)))
      val result = await(TestTaxEnrolmentsConnector.allocateAgent(enrolment,"group",clientID,agentCode))
      result.status mustBe CREATED
    }

    "create allocation error code" in {
      val enrolment = NewEnrolment(newEnrolment)
     when(mockWSHttp.POST[JsValue, HttpResponse](any(), any(), any())(any(), any(), any(), any())).
        thenReturn(Future.successful(HttpResponse(INTERNAL_SERVER_ERROR, responseJson = None)))
      val result = await(TestTaxEnrolmentsConnector.allocateAgent(enrolment,"group",clientID,agentCode))
      result.status mustBe INTERNAL_SERVER_ERROR
    }

    "delete allocation" in {
      when(mockWSHttp.DELETE[HttpResponse](any())(any(), any(), any())).
        thenReturn(Future.successful(HttpResponse(NO_CONTENT, responseJson = None)))
        val result = await(TestTaxEnrolmentsConnector.deAllocateAgent("group",clientID,agentCode))
      result.status mustBe NO_CONTENT
    }

    "delete allocation error code" in {
      when(mockWSHttp.DELETE[HttpResponse](any())(any(), any(), any())).
        thenReturn(Future.successful(HttpResponse(INTERNAL_SERVER_ERROR, responseJson = None)))
        val result = await(TestTaxEnrolmentsConnector.deAllocateAgent("group",clientID,agentCode))
      result.status mustBe INTERNAL_SERVER_ERROR
    }
  }

}
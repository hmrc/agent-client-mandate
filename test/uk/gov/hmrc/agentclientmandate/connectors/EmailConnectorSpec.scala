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
import org.scalatest.mock.MockitoSugar
import org.mockito.Mockito._
import org.scalatestplus.play.{OneServerPerSuite, PlaySpec}
import org.specs2.specification.BeforeAfterEach
import play.api.libs.json.{JsValue, Json}
import uk.gov.hmrc.play.http._
import uk.gov.hmrc.play.http.ws.{WSGet, WSPost, WSPut}
import play.api.test.Helpers._
import uk.gov.hmrc.agentclientmandate.models.SendEmailRequest

import scala.concurrent.Future


class EmailConnectorSpec extends PlaySpec with OneServerPerSuite with MockitoSugar with BeforeAfterEach {

  class MockHttp extends WSGet with WSPost with WSPut {
    override val hooks = NoneRequired
  }

  val mockWSHttp = mock[MockHttp]

  object TestEmailConnector extends EmailConnector {
    val sendEmailUri: String = "send-templated-email"
    val http: HttpGet with HttpPost with HttpPut = mockWSHttp
    val serviceUrl: String = "email"
  }


  "EmailConnector" must {

    "have a service url" in {
      EmailConnector.serviceUrl == "email"
    }

    "return a 202 accepted" when {

      "correct emailId Id is passed" in {
        implicit val hc: HeaderCarrier = HeaderCarrier()
        val emailString = "test@test.com"
        val templateId = "agentClinetNotification"
        val params = Map("emailAddress" -> emailString)

        val sendEmailReq = SendEmailRequest(List(emailString), templateId, params, true)
        val sendEmailReqJson = Json.toJson(sendEmailReq)
        when(mockWSHttp.POST[JsValue, HttpResponse](Matchers.any(), Matchers.eq(sendEmailReqJson), Matchers.any())(Matchers.any(), Matchers.any(), Matchers.any()))
          .thenReturn(Future.successful(HttpResponse(202, responseJson = None)))

        val response = TestEmailConnector.sendTemplatedEmail(emailString)
        await(response).status must be(202)

      }

    }

    "return other status" when {

      "incorrect email Id are passed" in {
        implicit val hc: HeaderCarrier = HeaderCarrier()
        val invalidEmailString = "test@test1.com"
        val templateId = "agentClinetNotification"
        val params = Map("emailAddress" -> invalidEmailString)

        val sendEmailReq = SendEmailRequest(List(invalidEmailString), templateId, params, true)
        val sendEmailReqJson = Json.toJson(sendEmailReq)

        when(mockWSHttp.POST[JsValue, HttpResponse](Matchers.any(), Matchers.eq(sendEmailReqJson), Matchers.any())(Matchers.any(), Matchers.any(), Matchers.any()))
          .thenReturn(Future.successful(HttpResponse(404, responseJson = None)))

        val response = TestEmailConnector.sendTemplatedEmail(invalidEmailString)
        await(response).status must be(404)

      }

    }

  }

  override def before: Any = {reset(mockWSHttp)}
}

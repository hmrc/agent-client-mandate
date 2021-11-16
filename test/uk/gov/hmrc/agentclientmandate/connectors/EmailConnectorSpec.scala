/*
 * Copyright 2021 HM Revenue & Customs
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
import org.mockito.{ArgumentMatchers, MockitoSugar}
import org.scalatest.BeforeAndAfterEach
import org.scalatestplus.play.PlaySpec
import play.api.libs.json.{JsValue, Json}
import play.api.test.Helpers._
import uk.gov.hmrc.agentclientmandate.utils.Generators._
import uk.gov.hmrc.http.{HttpClient, _}
import uk.gov.hmrc.play.audit.http.connector.AuditConnector

import scala.concurrent.{ExecutionContext, Future}


class EmailConnectorSpec extends PlaySpec with MockitoSugar with BeforeAndAfterEach {

  val mockWSHttp: HttpClient = mock[HttpClient]
  val mockAuditConnector: AuditConnector = mock[AuditConnector]

  trait Setup {
    class TestEmailConnector extends EmailConnector {
      val sendEmailUri: String = "send-templated-email"
      val http: CorePost = mockWSHttp
      val serviceUrl: String = "email"
      val ec: ExecutionContext = ExecutionContext.global

      override val auditConnector: AuditConnector = mockAuditConnector
    }
    val connector = new TestEmailConnector
  }

  val serviceString = "AA bb cc dd"

  "EmailConnector" must {

    "return a 202 accepted" when {

      "correct emailId Id is passed" in new Setup {
        implicit val hc: HeaderCarrier = HeaderCarrier()
        val emailString: String = emailGen.sample.get
        val templateId = "client_approves_mandate"

        when(mockWSHttp.POST[JsValue, HttpResponse](any(), any(),
          any())(any(), any(), any(), any()))
          .thenReturn(Future.successful(HttpResponse(202,"")))

        val response: Future[EmailStatus] = connector.sendTemplatedEmail(emailString, templateId, "ATED", None, "Recipient")
        await(response) must be(EmailSent)

      }

      "a uniqueAuthNumber has been added to the request" in new Setup {
        implicit val hc: HeaderCarrier = HeaderCarrier()
        val emailString: String = emailGen.sample.get
        val templateId = "agent_removes_mandate"

        val expectedRequestBody: JsValue = Json.obj(
            "to" -> Json.arr(emailString),
            "templateId" -> "agent_removes_mandate",
            "parameters" -> Json.obj(
               "emailAddress" -> emailString,
               "service" -> "ATED",
               "recipient" -> "Recipient",
               "uniqueAuthNo" -> "123456"
            ),
            "force" -> true
        )

        when(mockWSHttp.POST[JsValue, HttpResponse](any(), ArgumentMatchers.eq[JsValue](expectedRequestBody),
          any())(any(), any(), any(), any()))
          .thenReturn(Future.successful(HttpResponse(202,"")))

        val response: Future[EmailStatus] = connector.sendTemplatedEmail(emailString, templateId, "ATED", Some("123456"), "Recipient")
        await(response) must be(EmailSent)

      }

    }

    "return other status" when {

      "incorrect email Id are passed" in new Setup {
        implicit val hc: HeaderCarrier = HeaderCarrier()
        val invalidEmailString: String = emailGen.sample.get

        when(mockWSHttp.POST[JsValue, HttpResponse](any(), any(),
          any())(any(), any(), any(), any()))
          .thenReturn(Future.successful(HttpResponse(404,"")))

        val response: Future[EmailStatus] = connector.sendTemplatedEmail(invalidEmailString, "test-template-name", "ATED", None, "Recipient")
        await(response) must be(EmailNotSent)

      }

    }

  }

  override def beforeEach() {
    reset(mockWSHttp)
  }
}

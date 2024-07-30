/*
 * Copyright 2024 HM Revenue & Customs
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


import org.mockito.Mockito.when
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.PlaySpec
import uk.gov.hmrc.agentclientmandate.utils.Generators._
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http._
import uk.gov.hmrc.play.audit.http.connector.AuditConnector

import scala.concurrent.{ExecutionContext, Future}


class EmailConnectorSpec extends PlaySpec with MockitoSugar {

  val mockAuditConnector: AuditConnector = mock[AuditConnector]

  trait Setup extends ConnectorTest {
    class TestEmailConnector extends EmailConnector {
      val sendEmailUri: String = "http://localhost:9020/etmp-hod"
      val http: HttpClientV2 = mockHttpClient
      val serviceUrl: String = "http://localhost:9020/etmp-hod"
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

        when(requestBuilderExecute[HttpResponse]).thenReturn(Future.successful(HttpResponse(202, "")))

        val response: Future[EmailStatus] = connector.sendTemplatedEmail(emailString, templateId, "ATED", None, "Recipient")
        await(response) must be(EmailSent)

      }

      "a uniqueAuthNumber has been added to the request" in new Setup {
        implicit val hc: HeaderCarrier = HeaderCarrier()
        val emailString: String = emailGen.sample.get
        val templateId = "agent_removes_mandate"

        when(requestBuilderExecute[HttpResponse]).thenReturn(Future.successful(HttpResponse(202, "")))

        val response: Future[EmailStatus] = connector.sendTemplatedEmail(emailString, templateId, "ATED", Some("123456"), "Recipient")
        await(response) must be(EmailSent)

      }

    }

    "return other status" when {

      "incorrect email Id are passed" in new Setup {
        implicit val hc: HeaderCarrier = HeaderCarrier()
        val invalidEmailString: String = emailGen.sample.get

        when(requestBuilderExecute[HttpResponse]).thenReturn(Future.successful(HttpResponse(404, "")))
        val response: Future[EmailStatus] = connector.sendTemplatedEmail(invalidEmailString, "test-template-name", "ATED", None, "Recipient")
        await(response) must be(EmailNotSent)

      }

    }

  }

}

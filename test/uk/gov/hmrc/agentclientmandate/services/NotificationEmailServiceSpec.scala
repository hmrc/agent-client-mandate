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

package uk.gov.hmrc.agentclientmandate.services

import org.joda.time.DateTime
import org.mockito.Matchers
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterEach
import org.scalatest.mock.MockitoSugar
import org.scalatestplus.play.{OneServerPerSuite, PlaySpec}
import play.api.libs.json.Json
import play.api.test.Helpers._
import uk.gov.hmrc.agentclientmandate.connectors.EmailConnector
import uk.gov.hmrc.agentclientmandate.models._
import uk.gov.hmrc.agentclientmandate.repositories.{ClientMandateFetched, ClientMandateRepository}
import uk.gov.hmrc.play.http.{HeaderCarrier, HttpResponse}

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

class NotificationEmailServiceSpec extends PlaySpec with OneServerPerSuite with MockitoSugar with BeforeAndAfterEach {

  def await[A](future: Future[A]) = Await.result(future, 5 seconds)


  "NotificationEmailService" should {

    "return Some(false)" when {

      "invalid email id is passed" in {

        when(mockEmailConnector.validateEmailId(Matchers.eq(invalidEmail))(Matchers.any())) thenReturn Future.successful(HttpResponse(OK, responseJson = Some(invalidResponse)))

        val response = await(TestNotificationEmailService.validateEmail(invalidEmail))
        response must be(None)

      }
    }

  }


  implicit val hc: HeaderCarrier = HeaderCarrier()

  val validResponse = Json.parse( """{"valid":"true"}""")
  val invalidResponse = Json.parse( """{"valid":"false"}""")

  val invalidEmail = "aa bb cc"

  val mockEmailConnector = mock[EmailConnector]

  object TestNotificationEmailService extends NotificationEmailService {
    override val emailConnector = mockEmailConnector
  }

  override def beforeEach(): Unit = {
    reset(mockEmailConnector)
  }

}

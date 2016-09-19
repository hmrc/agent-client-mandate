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
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterEach
import org.scalatest.mock.MockitoSugar
import org.scalatestplus.play.{OneServerPerSuite, PlaySpec}
import play.api.test.Helpers._
import uk.gov.hmrc.agentclientmandate.connectors.GovernmentGatewayProxyConnector
import uk.gov.hmrc.agentclientmandate.models._
import uk.gov.hmrc.agentclientmandate.repositories.MandateRepository
import org.mockito.Matchers
import uk.gov.hmrc.play.http.{HeaderCarrier, HttpResponse}

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

class AllocateAgentServiceSpec extends PlaySpec with OneServerPerSuite with MockitoSugar with BeforeAndAfterEach {

  def await[A](future: Future[A]) = Await.result(future, 5 seconds)

  val agentCode = "ABC"

  val mandate =
    Mandate(
      id = "123",
      createdBy = User("credid", None),
      agentParty = Party("JARN123456", "Joe Bloggs", "Organisation", ContactDetails("test@test.com", "0123456789")),
      clientParty = Some(Party("ABCD1234", "Client Name", "Client", ContactDetails("somewhere@someplace.com", "98765433210"))),
      currentStatus = MandateStatus(Status.Pending, new DateTime(), "credid"),
      statusHistory = None,
      subscription = Subscription(None, Service("ated", "ATED"))
    )

  override def beforeEach(): Unit = {
    reset(ggProxyMock)
  }

  val ggProxyMock = mock[GovernmentGatewayProxyConnector]

  object TestAllocateAgentService extends AllocateAgentService {
    override val connector = ggProxyMock
  }

  val hc = new HeaderCarrier()

  "AllocateAgentService" should {
    "return a successful response" when {
      "given valid input" in {

        when(ggProxyMock.allocateAgent(Matchers.any())(Matchers.any())) thenReturn Future.successful(HttpResponse(200, None))

        val result = await(TestAllocateAgentService.allocateAgent(mandate, agentCode)(hc))
        result.status must be(OK)
      }
    }
  }

}

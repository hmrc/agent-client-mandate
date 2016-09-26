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
import play.api.test.Helpers._
import uk.gov.hmrc.agentclientmandate.connectors.{EtmpConnector, GovernmentGatewayProxyConnector}
import uk.gov.hmrc.agentclientmandate.models._
import uk.gov.hmrc.play.http.{HeaderCarrier, HttpResponse}

import scala.concurrent.Future

class AllocateAgentServiceSpec extends PlaySpec with OneServerPerSuite with MockitoSugar with BeforeAndAfterEach {

  val agentCode = "ABC"

  val mandate =
    Mandate(
      id = "123",
      createdBy = User("credid", "name", None),
      agentParty = Party("JARN123456", "Joe Bloggs", PartyType.Organisation, ContactDetails("test@test.com", Some("0123456789"))),
      clientParty = Some(Party("ABCD1234", "Client Name", PartyType.Organisation, ContactDetails("somewhere@someplace.com", Some("98765433210")))),
      currentStatus = MandateStatus(Status.New, new DateTime(), "credid"),
      statusHistory = None,
      subscription = Subscription(None, Service("ated", "ATED"))
    )

  override def beforeEach(): Unit = {
    reset(ggProxyMock)
    reset(etmpMock)
  }

  val ggProxyMock = mock[GovernmentGatewayProxyConnector]
  val etmpMock = mock[EtmpConnector]

  object TestAllocateAgentService extends AllocateAgentService {
    override val ggProxyConnector = ggProxyMock
    override val etmpConnector = etmpMock
  }

  val hc = new HeaderCarrier()

  "AllocateAgentService" should {
    "return a successful response given valid input" in {

      when(etmpMock.submitPendingClient(Matchers.any(), Matchers.any())) thenReturn Future.successful(HttpResponse(200, None))
      when(ggProxyMock.allocateAgent(Matchers.any())(Matchers.any())) thenReturn Future.successful(HttpResponse(200, None))

      val result = await(TestAllocateAgentService.allocateAgent(mandate, agentCode)(hc))
      result.status must be(OK)
    }

    "return etmpResponse when etmp call fails" in {
      when(etmpMock.submitPendingClient(Matchers.any(), Matchers.any())) thenReturn Future.successful(HttpResponse(500, None))

      val result = await(TestAllocateAgentService.allocateAgent(mandate, agentCode)(hc))
      result.status must be(INTERNAL_SERVER_ERROR)
    }
  }

}

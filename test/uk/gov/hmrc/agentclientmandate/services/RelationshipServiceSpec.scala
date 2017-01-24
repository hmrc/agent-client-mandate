/*
 * Copyright 2017 HM Revenue & Customs
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
import uk.gov.hmrc.agentclientmandate.connectors.{AuthConnector, EtmpConnector, GovernmentGatewayProxyConnector}
import uk.gov.hmrc.agentclientmandate.metrics.Metrics
import uk.gov.hmrc.agentclientmandate.models._
import uk.gov.hmrc.domain.{AtedUtr, Generator}
import uk.gov.hmrc.play.http.{BadRequestException, HeaderCarrier, HttpResponse}

import scala.concurrent.Future

class RelationshipServiceSpec extends PlaySpec with OneServerPerSuite with MockitoSugar with BeforeAndAfterEach {

  "RelationshipService" should {

    "authorise a relationship" when {

      "return a successful response given valid input" in {

        when(etmpMock.maintainAtedRelationship(Matchers.any())) thenReturn Future.successful(HttpResponse(OK, None))
        when(ggProxyMock.allocateAgent(Matchers.any())(Matchers.any())) thenReturn Future.successful(HttpResponse(OK, None))

        val result = await(TestRelationshipService.maintainRelationship(mandate, agentCode, authoriseAction)(hc))
        result.status must be(OK)
      }

      "return etmp call failed response when etmp call fails" in {
        when(etmpMock.maintainAtedRelationship(Matchers.any())) thenReturn Future.successful(HttpResponse(INTERNAL_SERVER_ERROR, None))

        val response = the[RuntimeException] thrownBy await(TestRelationshipService.maintainRelationship(mandate, agentCode, authoriseAction)(hc))
        response.getMessage must be("ETMP call failed")
      }

      "return gg call failed response when gg call fails" in {
        when(etmpMock.maintainAtedRelationship(Matchers.any())) thenReturn Future.successful(HttpResponse(OK, None))
        when(ggProxyMock.allocateAgent(Matchers.any())(Matchers.any())) thenReturn Future.successful(HttpResponse(INTERNAL_SERVER_ERROR, None))

        val response = the[RuntimeException] thrownBy await(TestRelationshipService.maintainRelationship(mandate, agentCode, authoriseAction)(hc))
        response.getMessage must be("Authorise - GG Proxy call failed")
      }

      "if service not ATED, throw bad request exception" in {
        val sub = Subscription(None, Service("XYZ", "XYZ"))
        val response = the[BadRequestException] thrownBy await(TestRelationshipService.maintainRelationship(mandate.copy(subscription = sub), agentCode, authoriseAction)(hc))
        response.getMessage must be("This is only defined for ATED")
      }
    }

    "deauthorise a relationship" when {

      "return a successful response given valid input" in {

        when(etmpMock.maintainAtedRelationship(Matchers.any())) thenReturn Future.successful(HttpResponse(OK, None))
        when(ggProxyMock.deAllocateAgent(Matchers.any())(Matchers.any())) thenReturn Future.successful(HttpResponse(OK, None))

        val result = await(TestRelationshipService.maintainRelationship(mandate, agentCode, deAuthoriseAction)(hc))
        result.status must be(OK)
      }

      "return etmp call failed response when etmp call fails" in {
        when(etmpMock.maintainAtedRelationship(Matchers.any())) thenReturn Future.successful(HttpResponse(INTERNAL_SERVER_ERROR, None))

        val response = the[RuntimeException] thrownBy await(TestRelationshipService.maintainRelationship(mandate, agentCode, deAuthoriseAction)(hc))
        response.getMessage must be("ETMP call failed")
      }

      "return gg call failed response when gg call fails" in {
        when(etmpMock.maintainAtedRelationship(Matchers.any())) thenReturn Future.successful(HttpResponse(OK, None))
        when(ggProxyMock.deAllocateAgent(Matchers.any())(Matchers.any())) thenReturn Future.successful(HttpResponse(INTERNAL_SERVER_ERROR, None))

        val response = the[RuntimeException] thrownBy await(TestRelationshipService.maintainRelationship(mandate, agentCode, deAuthoriseAction)(hc))
        response.getMessage must be("De-Authorise - GG Proxy call failed")
      }

      "if service not ATED, throw bad request exception" in {
        val sub = Subscription(None, Service("XYZ", "XYZ"))
        val response = the[BadRequestException] thrownBy await(TestRelationshipService.maintainRelationship(mandate.copy(subscription = sub), agentCode, deAuthoriseAction)(hc))
        response.getMessage must be("This is only defined for ATED")
      }
    }

  }

  val agentCode = "ABC"
  val authoriseAction = "Authorise"
  val deAuthoriseAction = "De-Authorise"


  implicit val hc = new HeaderCarrier()

  val atedUtr: AtedUtr = new Generator().nextAtedUtr

  val mandate =
    Mandate(
      id = "123",
      createdBy = User("credid", "name", None),
      agentParty = Party("JARN123456", "Joe Bloggs", PartyType.Organisation, ContactDetails("test@test.com", Some("0123456789"))),
      clientParty = Some(Party("ABCD1234", "Client Name", PartyType.Organisation, ContactDetails("somewhere@someplace.com", Some("98765433210")))),
      currentStatus = MandateStatus(Status.New, new DateTime(), "credid"),
      statusHistory = Nil,
      subscription = Subscription(Some(atedUtr.utr), Service("ated", "ATED")),
      clientDisplayName = "client display name"
    )

  override def beforeEach(): Unit = {
    reset(ggProxyMock)
    reset(etmpMock)
    reset(mockAuthConnector)
  }

  val ggProxyMock = mock[GovernmentGatewayProxyConnector]
  val etmpMock = mock[EtmpConnector]
  val mockAuthConnector = mock[AuthConnector]

  object TestRelationshipService extends RelationshipService {
    override val ggProxyConnector = ggProxyMock
    override val etmpConnector = etmpMock
    override val metrics = Metrics
  }

  val successResponseJsonAuth = Json.parse(
    """
      {
        "accounts": {
          "agent": {
            "agentCode":"AGENT-123",
            "agentBusinessUtr":"JARN1234567"
          }
        }
      }
    """
  )

}

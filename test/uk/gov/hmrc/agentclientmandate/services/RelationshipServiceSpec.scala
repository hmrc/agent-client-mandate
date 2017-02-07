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
import org.scalatest.Matchers._
import org.scalatest._
import org.scalatest.mock.MockitoSugar
import org.scalatestplus.play.{OneServerPerSuite, PlaySpec}
import play.api.libs.json.Json
import play.api.test.Helpers._
import uk.gov.hmrc.agentclientmandate.config.ApplicationConfig.identifiers
import uk.gov.hmrc.agentclientmandate.connectors.{AuthConnector, EtmpConnector, GovernmentGatewayProxyConnector}
import uk.gov.hmrc.agentclientmandate.metrics.Metrics
import uk.gov.hmrc.agentclientmandate.models._
import uk.gov.hmrc.agentclientmandate.tasks.{ActivationTaskExecutor, DeActivationTaskExecutor}
import uk.gov.hmrc.domain.{AtedUtr, Generator}
import uk.gov.hmrc.play.http.{HeaderCarrier, HttpResponse}
import uk.gov.hmrc.tasks._

import scala.concurrent.Future

class RelationshipServiceSpec extends PlaySpec with OneServerPerSuite with MockitoSugar with BeforeAndAfterEach {

  "RelationshipService" should {

    "throw an exception" when {
      "trying to create a relationship but service name is not ATED" in {
        val caught = intercept[uk.gov.hmrc.play.http.BadRequestException] {
          TestRelationshipService.createAgentClientRelationship(mandate1, agentCode)
        }
        caught.getMessage should endWith ("This is only defined for ATED")
      }
      "trying to break a relationship but service name is not ATED" in {
        val caught = intercept[uk.gov.hmrc.play.http.BadRequestException] {
          TestRelationshipService.breakAgentClientRelationship(mandate1, agentCode, "client")
        }
        caught.getMessage should endWith ("This is only defined for ATED")
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

  val task = Task("create", Map("clientId" -> mandate.subscription.referenceNumber.getOrElse(""),
    "agentPartyId" -> mandate.agentParty.id,
    "serviceIdentifier" -> identifiers.getString(s"${ mandate.subscription.service.id.toLowerCase()}.identifier"),
    "agentCode" -> agentCode,
    "mandateId" -> mandate.id,
    "credId" -> "credId"))

  val mandate1 =
    Mandate(
      id = "123",
      createdBy = User("credid", "name", None),
      agentParty = Party("JARN123456", "Joe Bloggs", PartyType.Organisation, ContactDetails("test@test.com", Some("0123456789"))),
      clientParty = Some(Party("ABCD1234", "Client Name", PartyType.Organisation, ContactDetails("somewhere@someplace.com", Some("98765433210")))),
      currentStatus = MandateStatus(Status.New, new DateTime(), "credid"),
      statusHistory = Nil,
      subscription = Subscription(Some(atedUtr.utr), Service("ebc", "ABC")),
      clientDisplayName = "client display name"
    )


  val mockAuthConnector = mock[AuthConnector]
  val tc1mock = mock[TaskControllerT]
  val successResponseJsonAuth = Json.parse(
    """{
               "credentials": {
                 "gatewayId": "cred-id-113244018119",
                 "idaPids": []
               },
               "accounts": {
                 "agent": {
                   "agentCode":"AGENT-123", "agentBusinessUtr":"JARN1234567"
                 }
               }
             }""")

  override def beforeEach(): Unit = {
    reset(mockAuthConnector)
    reset(tc1mock)
  }

  object TestRelationshipService extends RelationshipService {
    override val metrics = Metrics
    override def authConnector: AuthConnector = mockAuthConnector
    //override val taskController: TaskControllerT = tc1mock

  }

}

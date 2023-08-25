/*
 * Copyright 2023 HM Revenue & Customs
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

import com.typesafe.config.{Config, ConfigFactory}
import java.time.Instant
import org.mockito.MockitoSugar
import org.scalatest._
import org.scalatest.matchers.should.Matchers._
import org.scalatestplus.play.PlaySpec
import play.api.libs.json.{JsValue, Json}
import uk.gov.hmrc.agentclientmandate.metrics.ServiceMetrics
import uk.gov.hmrc.agentclientmandate.models._
import uk.gov.hmrc.agentclientmandate.tasks.{ActivationTaskService, DeActivationTaskService}
import uk.gov.hmrc.agentclientmandate.utils.Generators._
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.domain.{AtedUtr, Generator}
import uk.gov.hmrc.http.{BadRequestException, HeaderCarrier}
import uk.gov.hmrc.tasks._

import scala.concurrent.ExecutionContext.Implicits.global

class RelationshipServiceSpec extends PlaySpec with MockitoSugar with BeforeAndAfterEach {

  val mockMetrics: ServiceMetrics = mock[ServiceMetrics]
  val agentCode = "ABC"

  implicit val hc: HeaderCarrier = HeaderCarrier()
  lazy val mockConfig: Config = ConfigFactory.load("identifiers.properties")

  val mockActivationTaskService: ActivationTaskService = mock[ActivationTaskService]
  val mockDeactivationTaskService: DeActivationTaskService = mock[DeActivationTaskService]

  trait Setup {
    val service = new TestRelationshipService

    class TestRelationshipService extends RelationshipService {
      override val authConnector: AuthConnector = mockAuthConnector
      override val serviceMetrics: ServiceMetrics = mockMetrics
      override val identifiers: _root_.com.typesafe.config.Config = mockConfig
      override val activationTaskService: ActivationTaskService = mockActivationTaskService
      override val deactivationTaskService: DeActivationTaskService = mockDeactivationTaskService
    }
  }

  val authoriseAction = "Authorise"
  val deAuthoriseAction = "De-Authorise"
  val atedUtr: AtedUtr = new Generator().nextAtedUtr
  val mandate: Mandate =
    Mandate(
      id = "123",
      createdBy = User("credid", nameGen.sample.get, None),
      agentParty = Party(partyIDGen.sample.get, nameGen.sample.get, PartyType.Organisation, ContactDetails(emailGen.sample.get, telephoneNumberGen.sample)),
      clientParty = Some(Party(partyIDGen.sample.get, "Client Name", PartyType.Organisation, ContactDetails(emailGen.sample.get, telephoneNumberGen.sample))),
      currentStatus = MandateStatus(Status.New, Instant.now(), "credid"),
      statusHistory = Nil,
      subscription = Subscription(Some(atedUtr.utr), Service("ated", "ATED")),
      clientDisplayName = "client display name"
    )

  val task: Task = Task("create", Map("clientId" -> mandate.subscription.referenceNumber.getOrElse(""),
    "agentPartyId" -> mandate.agentParty.id,
    "serviceIdentifier" -> mockConfig.getString(s"${mandate.subscription.service.id.toLowerCase()}.identifier"),
    "agentCode" -> agentCode,
    "mandateId" -> mandate.id,
    "credId" -> "credId"), ActivationTaskMessage(mockActivationTaskService, mockMetrics))

  val mandate1: Mandate =
    Mandate(
      id = "123",
      createdBy = User("credid", "name", None),
      agentParty = Party(partyIDGen.sample.get, nameGen.sample.get, PartyType.Organisation, ContactDetails(emailGen.sample.get, telephoneNumberGen.sample)),
      clientParty = Some(Party(partyIDGen.sample.get, "Client Name", PartyType.Organisation, ContactDetails(emailGen.sample.get, telephoneNumberGen.sample))),
      currentStatus = MandateStatus(Status.New, Instant.now(), "credid"),
      statusHistory = Nil,
      subscription = Subscription(Some(atedUtr.utr), Service("ebc", "ABC")),
      clientDisplayName = "client display name"
    )

  val mockAuthConnector: AuthConnector = mock[AuthConnector]
  val tc1mock: TaskControllerT = mock[TaskControllerT]
  val successResponseJsonAuth: JsValue = Json.parse(
    s"""{
               "credentials": {
                 "gatewayId": "cred-id-113244018119",
                 "idaPids": []
               },
               "accounts": {
                 "agent": {
                   "agentCode":"${agentCodeGen.sample.get}", "agentBusinessUtr":"${agentBusinessUtrGen.sample.get}"
                 }
               }
             }""")

  override def beforeEach(): Unit = {
    reset(mockAuthConnector)
    reset(tc1mock)
  }

  "RelationshipService" should {

    "throw an exception" when {
      "trying to create a relationship but service name is not ATED" in new Setup {
        val caught: BadRequestException = intercept[_root_.uk.gov.hmrc.http.BadRequestException] {
          service.createAgentClientRelationship(mandate1, agentCode)
        }
        caught.getMessage should endWith("This is only defined for ATED")
      }
      "trying to break a relationship but service name is not ATED" in new Setup {
        val caught: BadRequestException = intercept[_root_.uk.gov.hmrc.http.BadRequestException] {
          service.breakAgentClientRelationship(mandate1, agentCode, "client")
        }
        caught.getMessage should endWith("This is only defined for ATED")
      }
    }
  }

}

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

package uk.gov.hmrc.agentclientmandate.services

import org.joda.time.DateTime
import org.mockito.Mockito._
import org.scalatest.Matchers._
import org.scalatest._
import org.scalatest.mockito.MockitoSugar
import org.scalatestplus.play.{OneServerPerSuite, PlaySpec}
import play.api.libs.json.Json
import uk.gov.hmrc.agentclientmandate.config.ApplicationConfig.identifiers
import uk.gov.hmrc.agentclientmandate.metrics.Metrics
import uk.gov.hmrc.agentclientmandate.models._
import uk.gov.hmrc.domain.{AtedUtr, Generator}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.tasks._
import uk.gov.hmrc.agentclientmandate.config.AuthClientConnector
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.agentclientmandate.utils.Generators._

class RelationshipServiceSpec extends PlaySpec with OneServerPerSuite with MockitoSugar with BeforeAndAfterEach {

  "RelationshipService" should {

    "throw an exception" when {
      "trying to create a relationship but service name is not ATED" in {
        val caught = intercept[_root_.uk.gov.hmrc.http.BadRequestException] {
          TestRelationshipService.createAgentClientRelationship(mandate1, agentCode)
        }
        caught.getMessage should endWith ("This is only defined for ATED")
      }
      "trying to break a relationship but service name is not ATED" in {
        val caught = intercept[_root_.uk.gov.hmrc.http.BadRequestException] {
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
      createdBy = User("credid",nameGen.sample.get, None),
      agentParty = Party(partyIDGen.sample.get, nameGen.sample.get, PartyType.Organisation, ContactDetails(emailGen.sample.get, telephoneNumberGen.sample)),
      clientParty = Some(Party(partyIDGen.sample.get, "Client Name", PartyType.Organisation, ContactDetails(emailGen.sample.get, telephoneNumberGen.sample))),
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
      agentParty = Party(partyIDGen.sample.get, nameGen.sample.get, PartyType.Organisation, ContactDetails(emailGen.sample.get, telephoneNumberGen.sample)),
      clientParty = Some(Party(partyIDGen.sample.get, "Client Name", PartyType.Organisation, ContactDetails(emailGen.sample.get, telephoneNumberGen.sample))),
      currentStatus = MandateStatus(Status.New, new DateTime(), "credid"),
      statusHistory = Nil,
      subscription = Subscription(Some(atedUtr.utr), Service("ebc", "ABC")),
      clientDisplayName = "client display name"
    )


  val mockAuthConnector = mock[AuthConnector]
  val tc1mock = mock[TaskControllerT]
  val successResponseJsonAuth = Json.parse(
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

  object TestRelationshipService extends RelationshipService {
    override val metrics = Metrics
    override val authConnector = mockAuthConnector
  }

}

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
import org.scalatestplus.play.{OneAppPerSuite, PlaySpec}
import play.api.libs.json.Json
import play.api.test.Helpers._
import uk.gov.hmrc.agentclientmandate.connectors.{AuthConnector, EtmpConnector}
import uk.gov.hmrc.agentclientmandate.models._
import uk.gov.hmrc.agentclientmandate.repositories.{MandateCreated, MandateRepository}
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.Future

class MandateCreateServiceSpec extends PlaySpec with OneAppPerSuite with MockitoSugar with BeforeAndAfterEach {

  "MandateCreateService" should {

    "create a client mandate status object with a status of pending for new client mandates" in {

      val mandateStatus = TestClientMandateCreateService.createNewStatus("credid")
      mandateStatus must be(MandateStatus(Status.New, mandateStatus.timestamp, "credid"))

    }

    "return success response" when {

      "a Mandate is created for an organisation" in {

        val mandateId = TestClientMandateCreateService.createMandateId
        val successResponseJsonETMP = Json.parse(
          """
            |{
            |  "sapNumber":"1234567890",
            |  "safeId": "EX0012345678909",
            |  "agentReferenceNumber": "AARN1234567",
            |  "isAnIndividual": false,
            |  "organisation": {
            |    "organisationName": "ABC Limited"
            |  }
            |}
          """.stripMargin
        )
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

        when(mandateRepositoryMock.insertMandate(Matchers.any())) thenReturn {
          Future.successful(MandateCreated(mandate(mandateId, DateTime.now())))
        }

        when(authConnectorMock.getAuthority()(Matchers.any())) thenReturn {
          Future.successful(successResponseJsonAuth)
        }

        when(etmpConnectorMock.getDetailsFromEtmp(Matchers.any())) thenReturn {
          Future.successful(successResponseJsonETMP)
        }

        val createdMandateId = TestClientMandateCreateService.createMandate(agentCode, mandateDto)
        await(createdMandateId) must be(mandateId)

      }

      "a Mandate is created for a person" in {

        val mandateId = TestClientMandateCreateService.createMandateId
        val successResponseJsonETMP = Json.parse(
          """
            |{
            |  "sapNumber":"1234567890",
            |  "safeId": "EX0012345678909",
            |  "agentReferenceNumber": "AARN1234567",
            |  "isAnIndividual": true,
            |  "individual" : {
            |    "firstName": "firstName",
            |    "lastName": "lastName"
            |  }
            |}
          """.stripMargin
        )
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

        when(mandateRepositoryMock.insertMandate(Matchers.any())) thenReturn {
          Future.successful(MandateCreated(mandate(mandateId, DateTime.now())))
        }

        when(authConnectorMock.getAuthority()(Matchers.any())) thenReturn {
          Future.successful(successResponseJsonAuth)
        }

        when(etmpConnectorMock.getDetailsFromEtmp(Matchers.any())) thenReturn {
          Future.successful(successResponseJsonETMP)
        }

        val createdMandateId = TestClientMandateCreateService.createMandate(agentCode, mandateDto)
        await(createdMandateId) must be(mandateId)

      }

    }



    "generate a 10 character mandate id" when {

      "a client mandate is created" in {
        val mandateId = TestClientMandateCreateService.createMandateId
        mandateId.length must be(10)
        mandateId.take(2) must be("AS")
      }
    }

  }

  val mandateDto = CreateMandateDto("test@test.com", "ated")

  def mandate(id: String, statusTime: DateTime): Mandate =
    Mandate(id = id, createdBy = User(hc.gaUserId.getOrElse("credid"), "Joe Bloggs", Some(agentCode)),
      agentParty = Party("JARN123456", "Joe Bloggs", PartyType.Organisation, ContactDetails("test@test.com", Some("0123456789"))),
      clientParty = None,
      currentStatus = MandateStatus(Status.New, statusTime, "credid"),
      statusHistory = None,
      subscription = Subscription(None, Service("ated", "ATED"))
    )

  implicit val hc = HeaderCarrier()
  val agentCode = "ac"

  val mandateRepositoryMock = mock[MandateRepository]
  val authConnectorMock = mock[AuthConnector]
  val etmpConnectorMock = mock[EtmpConnector]

  object TestClientMandateCreateService extends MandateCreateService {
    override val mandateRepository = mandateRepositoryMock
    override val authConnector = authConnectorMock
    override val etmpConnector = etmpConnectorMock
  }

  override def beforeEach(): Unit = {
    reset(mandateRepositoryMock)
    reset(authConnectorMock)
    reset(etmpConnectorMock)
  }

}

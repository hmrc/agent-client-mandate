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
import uk.gov.hmrc.agentclientmandate.models._
import uk.gov.hmrc.agentclientmandate.repositories.{ClientMandateRepository, ClientMandateCreated}
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._

class ClientMandateCreateServiceSpec extends PlaySpec with OneAppPerSuite with MockitoSugar with BeforeAndAfterEach {

  "ClientMandateService" should {

    "create a valid ClientMandate object" when {

      "passed a valid Dto from the create API" in {

        val clientMandateFromDto = TestClientMandateCreateService.generateClientMandate(mandateDto)
        clientMandateFromDto must be(clientMandate(clientMandateFromDto.id, clientMandateFromDto.currentStatus.timestamp))

      }

    }

    "create a client mandate status object with a status of pending for new client mandates" in {

      val clientMandateStatus = TestClientMandateCreateService.createPendingStatus("credid")
      clientMandateStatus must be(MandateStatus(Status.Pending, clientMandateStatus.timestamp, "credid"))

    }

    "return success response" when {

      "a ClientMandate is created" in {

        val mandateId = TestClientMandateCreateService.createMandateId

        when(clientMandateRepositoryMock.insertMandate(Matchers.any())) thenReturn {
          Future.successful(ClientMandateCreated(clientMandate(mandateId, DateTime.now())))
        }

        val createdMandateId = TestClientMandateCreateService.createMandate(mandateDto)
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

  val mandateDto: ClientMandateDto =
    ClientMandateDto(
      PartyDto("JARN123456", "Joe Bloggs", "Organisation"),
      ContactDetailsDto("test@test.com", "0123456789"),
      ServiceDto(None, "ATED")
    )

  def clientMandate(id: String, statusTime: DateTime): ClientMandate =
    ClientMandate(id = id, createdBy = hc.gaUserId.getOrElse("credid"),
      agentParty = Party("JARN123456", "Joe Bloggs", "Organisation", ContactDetails("test@test.com", "0123456789")),
      clientParty = None,
      currentStatus = MandateStatus(Status.Pending, statusTime, "credid"),
      statusHistory = None,
      subscription = Subscription(None, Service("ated", "ATED"))
      //service = Service(None, "ATED")
    )

  implicit val hc = HeaderCarrier()

  val clientMandateRepositoryMock = mock[ClientMandateRepository]

  object TestClientMandateCreateService extends ClientMandateCreateService {
    override val clientMandateRepository = clientMandateRepositoryMock
  }

  override def beforeEach(): Unit = {
    reset(clientMandateRepositoryMock)
  }

  def await[A](future: Future[A]): A = Await.result(future, 5 seconds)

}

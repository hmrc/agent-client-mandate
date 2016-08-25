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

import org.mockito.Matchers
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterEach
import org.scalatest.mock.MockitoSugar
import org.scalatestplus.play.{OneAppPerSuite, PlaySpec}
import uk.gov.hmrc.agentclientmandate.{ClientMandateCreated, ClientMandateRepository}
import uk.gov.hmrc.agentclientmandate.controllers.{ContactDetailsDto, PartyDto, ClientMandateDto}
import uk.gov.hmrc.mongo.Saved

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._

class ClientMandateServiceSpec extends PlaySpec with OneAppPerSuite with MockitoSugar with BeforeAndAfterEach {

  def await[A](future: Future[A]) = Await.result(future, 5 seconds)

  "ClientMandateService" should {

    "create a valid ClientMandate object" when {

      "passed a valid Dto from the create API" in {

        val clientMandateFromDto = TestClientMandateService.createBananas(mandateDto)
        clientMandateFromDto must be(clientMandate)

      }

    }

    "return success response" when {

      "a ClientMandate is created" in {

        when(clientMandateRepositoryMock.insertMandate(Matchers.any())) thenReturn {
          Future.successful(ClientMandateCreated(clientMandate))
        }

        val mandateId = TestClientMandateService.createMandate(mandateDto)
        await(mandateId) must be("123")

      }

    }

  }

  val mandateDto: ClientMandateDto = ClientMandateDto(PartyDto("JARN123456", "Joe Bloggs", "Organisation"), ContactDetailsDto("test@test.com", "0123456789"))

  val clientMandate = ClientMandate("123", "credid", Party("JARN123456", "Joe Bloggs", "Organisation"), ContactDetails("test@test.com", "0123456789"))

  val clientMandateRepositoryMock = mock[ClientMandateRepository]

  object TestClientMandateService extends ClientMandateService {
    override val clientMandateRepository = clientMandateRepositoryMock
  }

  override def beforeEach(): Unit = {
    reset(clientMandateRepositoryMock)
  }

}

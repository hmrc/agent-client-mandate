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
import org.scalatestplus.play.{OneAppPerSuite, OneServerPerSuite, PlaySpec}
import uk.gov.hmrc.agentclientmandate.controllers.{ClientMandateDto, ContactDetailsDto, PartyDto}
import uk.gov.hmrc.agentclientmandate.{ClientMandateCreated, ClientMandateFetched, ClientMandateRepository}

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

class FetchClientMandateServiceSpec extends PlaySpec with OneServerPerSuite with MockitoSugar with BeforeAndAfterEach {

  val mandateId = "123"

  "FetchClientMandateService" should {

    "fetch a valid ClientMandate object" when {

      "passed a valid mandate id from the fetch API" in {

        val clientMandateFetched: ClientMandateFetched = TestFetchClientMandateService.fetchBanana(mandateId)
        clientMandateFetched must be(clientMandateFetched)

      }

    }

  }
/*  val clientMandate = ClientMandate("123", "credid", Party("JARN123456", "Joe Bloggs", "Organisation"), ContactDetails("test@test.com", "0123456789"))

  val clientMandateFetched = ClientMandateFetched(clientMandate)

  val mockClientMandateRepository = mock[ClientMandateRepository]*/

  object TestFetchClientMandateService extends FetchClientMandateService {
    //override val clientMandateRepository = mockClientMandateRepository
  }

  /*override def beforeEach(): Unit = {
    reset(mockClientMandateRepository)
  }*/

}

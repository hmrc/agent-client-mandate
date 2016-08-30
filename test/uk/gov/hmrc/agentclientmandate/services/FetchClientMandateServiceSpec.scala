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
import org.scalatestplus.play.{OneServerPerSuite, PlaySpec}
import uk.gov.hmrc.agentclientmandate.{ClientMandateFetched, ClientMandateRepository}

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

class FetchClientMandateServiceSpec extends PlaySpec with OneServerPerSuite with MockitoSugar with BeforeAndAfterEach {

  def await[A](future: Future[A]) = Await.result(future, 5 seconds)

  val mandateId = "123"

  "FetchClientMandateService" should {

    "return a success response" when {

      "a client mandate is found for a valid mandate id in MongoDB" in {

        when(mockClientMandateRepository.fetchMandate(Matchers.any())) thenReturn {
          Future.successful(clientMandateFetched)}

        val reponse = TestFetchClientMandateService.fetchClientMandate(mandateId)
        await(reponse) must be(clientMandateFetched)

      }

    }

  }

  val clientMandate = ClientMandate("123", "credid", Party("JARN123456", "Joe Bloggs", "Organisation"), ContactDetails("test@test.com", "0123456789"))

  val clientMandateFetched = ClientMandateFetched(clientMandate)

  val mockClientMandateRepository = mock[ClientMandateRepository]

  object TestFetchClientMandateService extends FetchClientMandateService {
    override val clientMandateRepository = mockClientMandateRepository
  }

  override def beforeEach(): Unit = {
    reset(mockClientMandateRepository)
  }

}

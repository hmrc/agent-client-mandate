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

package uk.gov.hmrc.agentclientmandate.repositories

import org.joda.time.DateTime
import org.scalatest.BeforeAndAfterEach
import org.scalatestplus.play.{OneAppPerSuite, PlaySpec}
import reactivemongo.api.DB
import uk.gov.hmrc.agentclientmandate.models._
import uk.gov.hmrc.mongo.MongoSpecSupport

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Await, Future}
import scala.concurrent.duration._

class ClientMandateRepositorySpec extends PlaySpec with MongoSpecSupport with OneAppPerSuite with BeforeAndAfterEach {

  "ClientMandateRepository" should {

    "save a client mandate to the repo" when {

      "a new client mandate object is passed" in {
        await(testClientMandateRepository.insertMandate(clientMandate))

        await(testClientMandateRepository.findAll()).head must be(clientMandate)
        await(testClientMandateRepository.count) must be(1)
      }

    }

    "get a client mandate from the repo" when {

      "the correct mandate id is passed" in {
        await(testClientMandateRepository.insertMandate(clientMandate))

        await(testClientMandateRepository.findAll()).head must be(clientMandate)
        await(testClientMandateRepository.count) must be(1)

        await(testClientMandateRepository.fetchMandate(clientMandate.id)) must be(ClientMandateFetched(clientMandate))

      }

    }

  }

  def testClientMandateRepository(implicit mongo: () => DB) = new ClientMandateMongoRepository

  def clientMandate: ClientMandate =
    ClientMandate("AS12345678", createdBy = "credid",
      party = Party("JARN123456", "Joe Bloggs", "Organisation", contactDetails = ContactDetails("test@test.com", "0123456789")),
      currentStatus = MandateStatus(Status.Pending, new DateTime(1472631804869L), "credidupdate"),
      statusHistory = None,
      service = Service(None, "ATED")
    )

  override def beforeEach(): Unit = {
    await(testClientMandateRepository.drop)
  }

  def await[A](future: Future[A]): A = Await.result(future, 5 seconds)

}

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

import org.joda.time.{DateTimeUtils, DateTime}
import org.mockito.Matchers
import org.scalatest.BeforeAndAfterEach
import org.scalatest.mock.MockitoSugar
import org.mockito.Mockito._
import org.scalatestplus.play.{OneAppPerTest, PlaySpec}
import uk.gov.hmrc.agentclientmandate.controllers.{SubscriptionDto, ClientMandateUpdatedDto}
import uk.gov.hmrc.agentclientmandate.models._
import uk.gov.hmrc.agentclientmandate.repositories._
import uk.gov.hmrc.play.http.HeaderCarrier
import scala.concurrent.duration._

import scala.concurrent.{Await, Future}

class ClientMandateUpdateServiceSpec extends PlaySpec with OneAppPerTest with BeforeAndAfterEach with MockitoSugar {

  "ClientMandateUpdateService" should {

    "create a valid update mandate object" when {

      "valid update data is passed from the update API" in {

        DateTimeUtils.setCurrentMillisFixed(12345678912323L)

        val time = DateTime.now
        val updatedMandate = TestClientMandateUpdateService.generateUpdatedMandate(clientMandate("AS12345678", time), clientMandateUpdatedDto)
        updatedMandate must be(updatedClientMandate(updatedMandate.currentStatus.timestamp.toDateTime))

      }

    }

    "add the current status to the status history" when {

      "the status history is already populated" in {

        DateTimeUtils.setCurrentMillisFixed(12345678912323L)

        val time = DateTime.now

        val mandateWithHistory =
          clientMandate("AS12345678", time)
            .copy(currentStatus = MandateStatus(Status.Approved, time, "credid"))
            .copy(statusHistory = Some(Seq(MandateStatus(Status.Pending, time, "credid"))))

        val upm = {
          val a = updatedClientMandate(time)
          a.copy(statusHistory = a.statusHistory.map(MandateStatus(Status.Approved, time, "credid") +: _))
        }

        val updatedMandate = TestClientMandateUpdateService.generateUpdatedMandate(mandateWithHistory, clientMandateUpdatedDto)

        updatedMandate must be(upm)

      }

    }

    "update and save the updated mandate" when {

      "an existing mandate is found to update" in {

        DateTimeUtils.setCurrentMillisFixed(12345678912323L)

        val time = DateTime.now

        when(mockClientMandateFetchService.fetchClientMandate(Matchers.any())) thenReturn {
          Future.successful(ClientMandateFetched(clientMandate("AS12345678", time)))
        }

        when(mockClientMandateRepository.updateMandate(Matchers.any())) thenReturn {
          Future.successful(ClientMandateUpdated(updatedClientMandate(time)))
        }

        val updateMandateDto = ClientMandateUpdatedDto(mandateId = "AS12345678", None, None, None)

        val updatedRecord = await(TestClientMandateUpdateService.updateMandate("AS12345678", updateMandateDto))

        val dateTime = updatedRecord.asInstanceOf[ClientMandateUpdated].clientMandate.currentStatus.timestamp

        verify(mockClientMandateFetchService, times(1)).fetchClientMandate(Matchers.any())

        updatedRecord must be(ClientMandateUpdated(updatedClientMandate(time)))

      }

    }

    "return an error" when {

      "there is not a matching mandate found" in {

        when(mockClientMandateFetchService.fetchClientMandate(Matchers.any())) thenReturn Future.successful(ClientMandateNotFound)

        val result = await(TestClientMandateUpdateService.updateMandate("AS12345678", ClientMandateUpdatedDto(mandateId = "AS12345678", None, None, None)))

        result must be(ClientMandateUpdateError)

      }

    }

  }

  val mockClientMandateFetchService = mock[ClientMandateFetchService]

  val mockClientMandateRepository = mock[ClientMandateRepository]

  object TestClientMandateUpdateService extends ClientMandateUpdateService {
    override val clientMandateFetchService = mockClientMandateFetchService
    override val clientMandateRepository = mockClientMandateRepository
  }

  override def beforeEach: Unit = {
    reset(mockClientMandateFetchService)
    reset(mockClientMandateRepository)
  }

  override def afterEach: Unit = {
    DateTimeUtils.setCurrentMillisFixed(DateTime.now.getMillis)
  }

  implicit val hc = HeaderCarrier()



  def clientMandate(id: String, time: DateTime): ClientMandate =
    ClientMandate(id = id, createdBy = hc.gaUserId.getOrElse("credid"),
      agentParty = Party("JARN123456", "Joe Bloggs", "Organisation", ContactDetails("test@test.com", "0123456789")),
      clientParty = None,
      currentStatus = MandateStatus(Status.Pending, time, "credid"),
      statusHistory = None,
      subscription = Subscription(None, Service("ated", "ATED"))
    )

  def updatedClientMandate(time: DateTime): ClientMandate =
    ClientMandate("AS12345678", createdBy = "credid",
      agentParty = Party("JARN123456", "Joe Bloggs", "Organisation", contactDetails = ContactDetails("test@test.com", "0123456789")),
      clientParty = Some(Party("XBAT00000123456", "Joe Ated", "Organisation", contactDetails = ContactDetails("", ""))),
      currentStatus = MandateStatus(Status.Active, time, "credid"),
      statusHistory = Some(Seq(MandateStatus(Status.Pending, time, "credid"))),
      subscription = Subscription(Some("XBAT00000123456"), Service("ated", "ATED"))
    )

  def clientMandateUpdatedDto: ClientMandateUpdatedDto =
    ClientMandateUpdatedDto(
      mandateId = "AS12345678",
      party = Some(PartyDto("XBAT00000123456", "Joe Ated", "Organisation")),
      subscription = Some(SubscriptionDto("XBAT00000123456")),
      status = Some(Status.Active)
    )

  def await[A](future: Future[A]): A = Await.result(future, 5 seconds)

}

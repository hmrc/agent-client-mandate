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

import org.joda.time.{DateTime, DateTimeUtils}
import org.mockito.Matchers
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterEach
import org.scalatest.mock.MockitoSugar
import org.scalatestplus.play.{OneAppPerTest, PlaySpec}
import uk.gov.hmrc.agentclientmandate.models._
import uk.gov.hmrc.agentclientmandate.repositories.{MandateRepository, MandateUpdateError, MandateUpdated}
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

class MandateUpdateServiceSpec extends PlaySpec with OneAppPerTest with BeforeAndAfterEach with MockitoSugar {

  "ClientMandateUpdateService" should {

    "create a valid update mandate object" when {

      "valid update data is passed from the update API" in {

        DateTimeUtils.setCurrentMillisFixed(12345678912323L)

        val time = DateTime.now
        val updatedMandateLocal = TestMandateUpdateService.generateUpdatedMandate(mandate("AS12345678", time), mandateUpdatedDto)
        updatedMandateLocal must be(updatedMandate(updatedMandateLocal.currentStatus.timestamp.toDateTime))

      }

    }

    "add the current status to the status history" when {

      "the status history is already populated" in {

        DateTimeUtils.setCurrentMillisFixed(12345678912323L)

        val time = DateTime.now

        val mandateWithHistory =
          mandate("AS12345678", time)
            .copy(currentStatus = MandateStatus(Status.Approved, time, "credid"))
            .copy(statusHistory = Some(Seq(MandateStatus(Status.Pending, time, "credid"))))

        val upm = {
          val a = updatedMandate(time)
          a.copy(statusHistory = a.statusHistory.map(MandateStatus(Status.Approved, time, "credid") +: _))
        }

        val updatedMandateLocal = TestMandateUpdateService.generateUpdatedMandate(mandateWithHistory, mandateUpdatedDto)

        updatedMandateLocal must be(upm)

      }

    }

    "update and save the updated mandate" when {

      "an existing mandate is found to update" in {

        DateTimeUtils.setCurrentMillisFixed(12345678912323L)

        val time = DateTime.now

        when(mockMandateRepository.updateMandate(Matchers.any())) thenReturn {
          Future.successful(MandateUpdated(updatedMandate(time)))
        }

        val updateMandateDto = MandateUpdatedDto(mandateId = "AS12345678", None, None, None)

        val updatedRecord = await(TestMandateUpdateService.updateMandate(mandate("AS12345678", time), updateMandateDto))

        val dateTime = updatedRecord.asInstanceOf[MandateUpdated].mandate.currentStatus.timestamp

        updatedRecord must be(MandateUpdated(updatedMandate(time)))

      }
    }
  }

  val mockMandateRepository = mock[MandateRepository]

  object TestMandateUpdateService extends MandateUpdateService {
    override val mandateRepository = mockMandateRepository
  }

  override def beforeEach: Unit = {
    reset(mockMandateRepository)
  }

  override def afterEach: Unit = {
    DateTimeUtils.setCurrentMillisFixed(DateTime.now.getMillis)
  }

  implicit val hc = HeaderCarrier()


  def mandate(id: String, time: DateTime): Mandate =
    Mandate(id = id, createdBy = User(hc.gaUserId.getOrElse("credid"), None),
      agentParty = Party("JARN123456", "Joe Bloggs", "Organisation", ContactDetails("test@test.com", "0123456789")),
      clientParty = None,
      currentStatus = MandateStatus(Status.Pending, time, "credid"),
      statusHistory = None,
      subscription = Subscription(None, Service("ated", "ATED"))
    )

  def updatedMandate(time: DateTime): Mandate =
    Mandate("AS12345678", createdBy = User("credid",None),
      agentParty = Party("JARN123456", "Joe Bloggs", "Organisation", contactDetails = ContactDetails("test@test.com", "0123456789")),
      clientParty = Some(Party("XBAT00000123456", "Joe Ated", "Organisation", contactDetails = ContactDetails("", ""))),
      currentStatus = MandateStatus(Status.Active, time, "credid"),
      statusHistory = Some(Seq(MandateStatus(Status.Pending, time, "credid"))),
      subscription = Subscription(Some("XBAT00000123456"), Service("ated", "ATED"))
    )

  def mandateUpdatedDto: MandateUpdatedDto =
    MandateUpdatedDto(
      mandateId = "AS12345678",
      party = Some(PartyDto("XBAT00000123456", "Joe Ated", "Organisation")),
      subscription = Some(SubscriptionDto("XBAT00000123456")),
      status = Some(Status.Active)
    )

  def await[A](future: Future[A]): A = Await.result(future, 5 seconds)

}

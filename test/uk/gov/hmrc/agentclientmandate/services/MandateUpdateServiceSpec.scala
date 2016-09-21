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
import org.scalatestplus.play.{OneServerPerSuite, PlaySpec}
import play.api.test.Helpers._
import uk.gov.hmrc.agentclientmandate.models._
import uk.gov.hmrc.agentclientmandate.repositories.{MandateRepository, MandateUpdated}
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.Future

class MandateUpdateServiceSpec extends PlaySpec with OneServerPerSuite with BeforeAndAfterEach with MockitoSugar {

  "MandateUpdateService" should {

    "update data in mongo with given data provided" when {
      "requested to do so - updateMandate" in {
        when(mockMandateRepository.updateMandate(Matchers.eq(mandate))).thenReturn(Future.successful(MandateUpdated(mandate)))
        await(TestMandateUpdateService.updateMandate(mandate)) must be(MandateUpdated(mandate))
      }
    }

  }

  val timeToUse = DateTime.now()

  val mandate = Mandate("AS12345678",
    User("credid", "Joe Bloggs", None),
    agentParty = Party("JARN123456", "Joe Bloggs", PartyType.Organisation, ContactDetails("", "")),
    currentStatus = MandateStatus(Status.New, timeToUse, "credid"),
    subscription = Subscription(None, Service("ated", "ATED"))
  )

  val mockMandateRepository = mock[MandateRepository]

  object TestMandateUpdateService extends MandateUpdateService {
    override val mandateRepository = mockMandateRepository
  }

  override def beforeEach: Unit = {
    reset(mockMandateRepository)
  }

  implicit val hc = HeaderCarrier()

}

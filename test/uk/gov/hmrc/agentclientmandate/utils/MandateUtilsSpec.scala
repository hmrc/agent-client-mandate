/*
 * Copyright 2017 HM Revenue & Customs
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

package uk.gov.hmrc.agentclientmandate.utils

import org.joda.time.DateTime
import org.scalatestplus.play.PlaySpec
import uk.gov.hmrc.agentclientmandate.models._

class MandateUtilsSpec extends PlaySpec {

  "MandateUtils" should {

    "return true" when {
      "status = APPROVED is NOT FOUND in status History" in {
        val mandate1 = Mandate("AS12345678",
          User("credid", "Joe Bloggs", None),
          agentParty = Party("JARN123456", "Joe Bloggs", PartyType.Organisation, ContactDetails("", Some(""))),
          clientParty = Some(Party("safe-id", "client-name", PartyType.Organisation, ContactDetails("client@mail.com"))),
          currentStatus = MandateStatus(Status.Active, new DateTime(), "credid"),
          statusHistory = Seq(MandateStatus(Status.PendingActivation, new DateTime(), "credid")),
          subscription = Subscription(Some("ated-ref-no"), Service("ated", "ATED")),
          clientDisplayName = "client display name"
        )
        MandateUtils.whetherSelfAuthorised(mandate1) must be (true)
      }
    }

    "return false" when {
      "status = APPROVED is FOUND in status History" in {
        val mandate1 =
          Mandate(
            id = "123",
            createdBy = User("credid", "name", None),
            agentParty = Party("JARN123456", "Joe Bloggs", PartyType.Organisation, ContactDetails("test@test.com", Some("0123456789"))),
            clientParty = Some(Party("ABCD1234", "Client Name", PartyType.Organisation, ContactDetails("somewhere@someplace.com", Some("98765433210")))),
            currentStatus = MandateStatus(Status.PendingActivation, new DateTime(), "credid"),
            statusHistory = Seq(MandateStatus(Status.Approved, new DateTime(), "credid"), MandateStatus(Status.New, new DateTime(), "credid2")),
            subscription = Subscription(Some("1111111111"), Service("ebc", "ABC")),
            clientDisplayName = "client display name"
          )
        MandateUtils.whetherSelfAuthorised(mandate1) must be (false)
      }
    }
  }
}

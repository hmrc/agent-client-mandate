/*
 * Copyright 2018 HM Revenue & Customs
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

package uk.gov.hmrc.agentclientmandate.models

import org.joda.time.DateTime
import org.scalatestplus.play.PlaySpec

class MandateSpec extends PlaySpec {

  val mandate = Mandate(id = "ABC123", createdBy = User("credId", "Joe Bloggs", None),
    agentParty = Party("JARN123456", "Joe Bloggs", PartyType.Organisation, ContactDetails("test@test.com", Some("0123456789"))),
    clientParty = None,
    currentStatus = MandateStatus(Status.New, DateTime.now, "credId"),
    statusHistory = Nil,
    subscription = Subscription(None, Service("ated", "ATED")),
    clientDisplayName = "client display name"
  )

  "MandateSpec" must {
    "add new status to history" in {
      val newStatus = MandateStatus(Status.PendingCancellation, DateTime.now, "credId")
      val updatedMandate = mandate.updateStatus(newStatus)
      updatedMandate.statusHistory.size must be(1)
      updatedMandate.statusHistory.head.status must be(Status.New)
      updatedMandate.currentStatus.status must be(Status.PendingCancellation)
    }
  }
}

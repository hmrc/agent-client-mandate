/*
 * Copyright 2021 HM Revenue & Customs
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

package repositories

import helpers.IntegrationSpec
import org.joda.time.DateTime
import play.api.test.Injecting
import uk.gov.hmrc.agentclientmandate.models._
import uk.gov.hmrc.agentclientmandate.repositories._

import java.util.UUID
import scala.concurrent.ExecutionContext.Implicits.global

class MandateRepositoryISpec
  extends IntegrationSpec
    with Injecting {

  lazy val repository: MandateRepository = inject[MandateRepositoryImpl].repository

  private def newId: String = UUID.randomUUID.toString

  private def mandateWithId(id: String): Mandate =
    Mandate(
      id = id,
      createdBy = User("credid", "name", None),
      agentParty = Party("partyId", "name", PartyType.Organisation, ContactDetails("a@b.com")),
      clientParty = None,
      currentStatus = MandateStatus(Status.Active, new DateTime(), "user"),
      statusHistory = Nil,
      subscription = Subscription(None, Service("ated", "ATED")),
      clientDisplayName = "client display name"
    )

  "MandateRepository" when {
    "insert" when {
      "no mandate with id present" must {
        "insert it" in {
          val id = newId
          val mandate = mandateWithId(id)
          await(repository.insertMandate(mandate)) mustBe MandateCreated(mandate)

          await(repository.fetchMandate(id)) mustBe MandateFetched(mandate)
        }
      }

      "mandate with id already present" must {
        "return an error" in {
          val mandate = mandateWithId(newId)
          await(repository.insertMandate(mandate))

          await(repository.insertMandate(mandate)) mustBe MandateCreateError
        }
      }
    }

    "update" when {
      "mandate with id exists" must {
        "update it" in {
          val id = newId
          val mandate = mandateWithId(id)
          await(repository.insertMandate(mandate))

          val mandateUpdated = mandate.copy(clientDisplayName = "something else")
          await(repository.updateMandate(mandateUpdated)) mustBe MandateUpdated(mandateUpdated)
          await(repository.fetchMandate(id)) mustBe MandateFetched(mandateUpdated)
        }
      }

      "mandate with id does not exist" must {
        "return an error" in {
          val mandate = mandateWithId(newId)
          await(repository.updateMandate(mandate)) mustBe MandateUpdateError
        }
      }
    }
  }
}

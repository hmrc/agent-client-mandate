/*
 * Copyright 2019 HM Revenue & Customs
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

package uk.gov.hmrc.agentclientmandate.config

import org.scalatestplus.play.{OneServerPerSuite, PlaySpec}
import scala.concurrent.duration._

class MicroserviceGlobalSpec extends PlaySpec with OneServerPerSuite {

  "MicroserviceGlobal" must {
    "have one scheduled job" in {
      MicroserviceGlobal.scheduledJobs.size must be(1)
    }

    "expiration job must be present and set up for daily interval" in {
      MicroserviceGlobal.scheduledJobs.head.name must be("ExpirationService")
      MicroserviceGlobal.scheduledJobs.head.interval must be(1 day)
    }
  }
}

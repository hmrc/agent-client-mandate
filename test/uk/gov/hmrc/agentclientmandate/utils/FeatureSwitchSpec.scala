/*
 * Copyright 2026 HM Revenue & Customs
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

import org.scalatestplus.play.PlaySpec

import scala.io.Source
import scala.util.Using

class FeatureSwitchSpec extends PlaySpec {

  "HIP Switch feature flag should be false by default" in {
    val applicationConfFileContents = Using.resource(Source.fromFile("conf/application.conf")) { source => source.getLines().mkString("") }
    val hipSwitchFlagSetToFalse = applicationConfFileContents.contains("feature.hipSwitch = false")

    withClue("HIP Switch feature flag should be false by default in application.conf:") {
      hipSwitchFlagSetToFalse mustBe true
    }
  }
}

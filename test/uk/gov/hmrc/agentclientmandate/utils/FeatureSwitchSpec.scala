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

package uk.gov.hmrc.agentclientmandate.utils

import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.inject.guice.GuiceApplicationBuilder

class FeatureSwitchSpec  extends PlaySpec with GuiceOneAppPerSuite {

  override def fakeApplication() = new GuiceApplicationBuilder().configure(Map("features.allocation.usingGG"
    -> "true","features.deallocation.usingGG" -> "false")).build()

  "Feature switch returns correct config values" when {

    "Return true for allocation" in {
      FeatureSwitch.isEnabled("allocation.usingGG") must be (true)
    }

    "return false for deallocation" in {
      FeatureSwitch.isEnabled("deallocation.usingGG") must be (false)
    }

    "set prop works for dummy key " in {
      FeatureSwitch.setProp("dummy-value",true)
      FeatureSwitch.isEnabled("dummy-value") must be (true)
      FeatureSwitch.disable(FeatureSwitch("dummy-value",true))
      FeatureSwitch.isEnabled("dummy-value") must be (false)
      FeatureSwitch.enable(FeatureSwitch("dummy-value",false))
      FeatureSwitch.isEnabled("dummy-value") must be (true)
    }
  }
}

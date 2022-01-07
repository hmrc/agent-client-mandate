/*
 * Copyright 2022 HM Revenue & Customs
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

import play.api.Configuration
import play.api.libs.json.Json

import scala.util.Try

case class FeatureSwitch(name: String, enabled: Boolean)

object FeatureSwitch {
  def forName(name: String)(implicit config: Configuration): FeatureSwitch = {
    FeatureSwitch(name, isEnabled(name))
  }

  def isEnabled(name: String)(implicit config: Configuration): Boolean = {
    val sysPropValue = sys.props.get(systemPropertyName(name))
    sysPropValue match {
      case Some(x)  => x.toBoolean
      case None     => Try(config.get[Boolean](confPropertyName(name))).getOrElse(false)
    }
  }

  def enable(switch: FeatureSwitch)(implicit config: Configuration): FeatureSwitch = {
    setProp(switch.name, value = true)
  }

  def disable(switch: FeatureSwitch)(implicit config: Configuration): FeatureSwitch = setProp(switch.name, value = false)

  def setProp(name: String, value: Boolean)(implicit config: Configuration): FeatureSwitch = {
    sys.props.+= ((systemPropertyName(name), value.toString))
    forName(name)
  }

  def confPropertyName(name: String): String = s"features.$name"
  def systemPropertyName(name: String): String = s"features.$name"

  implicit val format = Json.format[FeatureSwitch]
}


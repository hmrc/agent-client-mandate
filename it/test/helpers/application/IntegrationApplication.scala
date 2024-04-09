/*
 * Copyright 2024 HM Revenue & Customs
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

package helpers.application

import helpers.wiremock.WireMockConfig
import org.scalatest.TestSuite
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.ws.{WSClient, WSRequest}

trait IntegrationApplication extends GuiceOneServerPerSuite with WireMockConfig {
  self: TestSuite =>

  val currentAppBaseUrl: String = "agent-client-mandate"
  val testAppUrl: String        = s"http://localhost:$port"

  lazy val ws: WSClient = app.injector.instanceOf[WSClient]

  override lazy val app: Application = new GuiceApplicationBuilder()
    .configure("play.http.router"                    -> "testOnlyDoNotUseInAppConf.Routes",
      "mongo.uri"                                           -> "mongodb://localhost:27017/test-agent-client-mandate",
      "microservice.metrics.graphite.host"                  -> "localhost",
      "microservice.metrics.graphite.port"                  -> 2003,
      "microservice.metrics.graphite.prefix"                -> "play.agent-client-mandate.",
      "microservice.metrics.graphite.enabled"               -> true,
      "microservice.services.auth.host"                     -> wireMockHost,
      "microservice.services.auth.port"                     -> wireMockPort,
      "microservice.services.etmp-hod.host"                 -> wireMockHost,
      "microservice.services.etmp-hod.port"                 -> wireMockPort,
      "microservice.services.email.host"                    -> wireMockHost,
      "microservice.services.email.port"                    -> wireMockPort,
      "metrics.name"                                        -> "agent-client-mandate",
      "metrics.rateUnit"                                    -> "SECONDS",
      "metrics.durationUnit"                                -> "SECONDS",
      "metrics.showSamples"                                 -> true,
      "metrics.jvm"                                         -> true,
      "metrics.enabled"                                     -> true
    )
    .build()

  def makeRequest(uri: String): WSRequest = ws.url(s"http://localhost:$port/$uri")
}

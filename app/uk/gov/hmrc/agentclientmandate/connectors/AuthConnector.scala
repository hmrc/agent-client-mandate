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

package uk.gov.hmrc.agentclientmandate.connectors

import play.api.http.Status._
import play.api.libs.json.JsValue
import uk.gov.hmrc.agentclientmandate.Auditable
import uk.gov.hmrc.agentclientmandate.config.WSHttp
import uk.gov.hmrc.play.config.ServicesConfig
import uk.gov.hmrc.play.http._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait AuthConnector extends ServicesConfig with RawResponseReads with Auditable {

  def serviceUrl:String = baseUrl("auth")
  def http: HttpGet with HttpPost with HttpPut
  val authorityUri: String = "auth/authority"

  def getAuthority()(implicit hc: HeaderCarrier): Future[JsValue] = {

    val getUrl = s"""$serviceUrl/$authorityUri"""
    http.GET[HttpResponse](getUrl) map { response =>
      response.status match {
        case OK => response.json
        case status =>
          doFailedAudit("authFailed", getUrl, response.body)
          throw new RuntimeException("No authority found")
      }
    }
  }
}

object AuthConnector extends AuthConnector {
  // $COVERAGE-OFF$
  val http: HttpGet with HttpPost with HttpPut = WSHttp
  // $COVERAGE-ON$
}

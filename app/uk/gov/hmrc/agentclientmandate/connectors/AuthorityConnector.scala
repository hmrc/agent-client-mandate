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

package uk.gov.hmrc.agentclientmandate.connectors

import javax.inject.Inject
import play.api.http.Status._
import play.api.libs.json.JsValue
import uk.gov.hmrc.agentclientmandate.Auditable
import uk.gov.hmrc.http._
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import uk.gov.hmrc.play.bootstrap.http.HttpClient

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class DefaultAuthorityConnector @Inject()(val auditConnector: AuditConnector,
                                          val servicesConfig: ServicesConfig,
                                          val http: HttpClient) extends AuthorityConnector {
  def serviceUrl: String = servicesConfig.baseUrl("auth")
}

trait AuthorityConnector extends RawResponseReads with Auditable {
  def http: CoreGet
  def serviceUrl: String
  val authorityUri: String = "auth/authority"

  def getAuthority()(implicit hc: HeaderCarrier): Future[JsValue] = {
    val getUrl = s"""$serviceUrl/$authorityUri"""
    http.GET[HttpResponse](getUrl) map { response =>
      response.status match {
        case OK =>
          doResponseAudit("authSuccess", response)
          response.json
        case _ =>
          doFailedAudit("authFailed", getUrl, response.body)
          throw new RuntimeException("No authority found")
      }
    }
  }
}


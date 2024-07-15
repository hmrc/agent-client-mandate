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

package uk.gov.hmrc.agentclientmandate.connectors

import play.api.http.Status.{NON_AUTHORITATIVE_INFORMATION, OK}
import uk.gov.hmrc.agentclientmandate.Auditable
import uk.gov.hmrc.agentclientmandate.metrics.ServiceMetrics
import uk.gov.hmrc.http.{HeaderCarrier, HttpClient, HttpResponse}
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}
import play.api.libs.json._

class UsersGroupSearchConnector @Inject()(val auditConnector: AuditConnector,
                                          metrics: ServiceMetrics,
                                          servicesConfig: ServicesConfig,
                                          http: HttpClient
                                         )(implicit ec: ExecutionContext) extends RawResponseReads with Auditable {
  val serviceUrl: String = servicesConfig.baseUrl("users-groups-search")

  def fetchAgentCode(groupId: String)(implicit hc: HeaderCarrier): Future[Option[String]] = {
    val getUrl: String = s"$serviceUrl/groups/$groupId"
    http.GET[HttpResponse](getUrl) map {
      response =>
        response.status match {
          case NON_AUTHORITATIVE_INFORMATION => (response.json \ "agentCode").asOpt[String]
          case _ => None
        }
    }
  }
}

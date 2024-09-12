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

import play.api.http.Status.NON_AUTHORITATIVE_INFORMATION
import uk.gov.hmrc.agentclientmandate.Auditable
import uk.gov.hmrc.agentclientmandate.metrics.ServiceMetrics
import uk.gov.hmrc.agentclientmandate.utils.LoggerUtil.logWarn
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse, StringContextOps}
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import uk.gov.hmrc.http.HttpReads.Implicits._

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class UsersGroupSearchConnector @Inject()(val auditConnector: AuditConnector,
                                          metrics: ServiceMetrics,
                                          servicesConfig: ServicesConfig,
                                          http: HttpClientV2
                                         )(implicit ec: ExecutionContext) extends Auditable {
  val serviceUrl: String = servicesConfig.baseUrl("users-groups-search")

  def fetchAgentCode(groupId: String)(implicit hc: HeaderCarrier): Future[Option[String]] = {
    val getUrl = s"$serviceUrl/groups/$groupId"
    http.get(url"$getUrl").execute[HttpResponse].map { response =>
        response.status match {
          case NON_AUTHORITATIVE_INFORMATION => (response.json \ "agentCode").asOpt[String]
          case _ =>
            logWarn("[UsersGroupSearchConnector][fetchAgentCode]: No record found for provided groupId : " + groupId)
            None
        }
    }
  }
}

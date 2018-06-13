/*
 * Copyright 2018 HM Revenue & Customs
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

import play.api.Logger
import play.api.http.ContentTypes.XML
import play.api.http.HeaderNames.CONTENT_TYPE
import play.api.http.Status._
import uk.gov.hmrc.agentclientmandate.Auditable
import uk.gov.hmrc.agentclientmandate.config.WSHttp
import uk.gov.hmrc.agentclientmandate.metrics.{Metrics, MetricsEnum}
import uk.gov.hmrc.agentclientmandate.models.{ GsoAdminAllocateAgentXmlInput, GsoAdminDeallocateAgentXmlInput}
import uk.gov.hmrc.http._
import uk.gov.hmrc.play.config.ServicesConfig

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future


trait GovernmentGatewayProxyConnector extends ServicesConfig with RawResponseReads with Auditable {

  def serviceUrl: String = baseUrl("government-gateway-proxy")

  val ggUri = "government-gateway-proxy"

  def http: CorePost

  def metrics: Metrics

  def allocateAgent(input: GsoAdminAllocateAgentXmlInput)(implicit hc: HeaderCarrier): Future[HttpResponse] = {
    val timerContext = metrics.startTimer(MetricsEnum.GGProxyAllocate)
    http.POSTString(serviceUrl + s"/$ggUri/api/admin/GsoAdminAllocateAgent", input.toXml.toString, Seq(CONTENT_TYPE -> XML))
      .map({ response =>
        timerContext.stop()
        response.status match {
          case OK =>
            metrics.incrementSuccessCounter(MetricsEnum.GGProxyAllocate)
            response
          case status =>
            Logger.warn("allocateAgent failed")
            metrics.incrementFailedCounter(MetricsEnum.GGProxyAllocate)
            doFailedAudit("allocateAgentFailed", input.toXml.toString, response.body)
            response
        }
      })
  }

  def deAllocateAgent(input: GsoAdminDeallocateAgentXmlInput)(implicit hc: HeaderCarrier): Future[HttpResponse] = {
    val timerContext = metrics.startTimer(MetricsEnum.GGProxyDeallocate)
    http.POSTString(serviceUrl + s"/$ggUri/api/admin/GsoAdminDeallocateAgent", input.toXml.toString, Seq(CONTENT_TYPE -> XML))
      .map({ response =>
        timerContext.stop()
        response.status match {
          case OK =>
            metrics.incrementSuccessCounter(MetricsEnum.GGProxyDeallocate)
            response
          case status =>
            Logger.warn("deAllocateAgent failed")
            metrics.incrementFailedCounter(MetricsEnum.GGProxyDeallocate)
            doFailedAudit("deAllocateAgentFailed", input.toXml.toString, response.body)
            response
        }
      })
  }

}


object GovernmentGatewayProxyConnector extends GovernmentGatewayProxyConnector {
  // $COVERAGE-OFF$
  val http: HttpPost = WSHttp
  val metrics = Metrics
  // $COVERAGE-ON$
}

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
import play.api.Logger
import play.api.http.Status._
import play.api.libs.json.{JsValue, Json}
import uk.gov.hmrc.agentclientmandate.Auditable
import uk.gov.hmrc.agentclientmandate.metrics.{MetricsEnum, ServiceMetrics}
import uk.gov.hmrc.agentclientmandate.models.NewEnrolment
import uk.gov.hmrc.agentclientmandate.utils.MandateConstants
import uk.gov.hmrc.http._
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import uk.gov.hmrc.play.bootstrap.http.HttpClient

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future


class DefaultTaxEnrolmentConnector @Inject()(val metrics: ServiceMetrics,
                                             val auditConnector: AuditConnector,
                                             val servicesConfig: ServicesConfig,
                                             val http: HttpClient) extends TaxEnrolmentConnector {
  val serviceUrl: String = servicesConfig.baseUrl("tax-enrolments")
  val enrolmentUrl = s"$serviceUrl/tax-enrolments"
}

trait TaxEnrolmentConnector extends RawResponseReads with Auditable {

  def serviceUrl: String
  def enrolmentUrl: String
  def http: CoreDelete with CorePost
  def metrics: ServiceMetrics

  def allocateAgent(input: NewEnrolment, groupId: String, clientId: String, agentCode: String)(implicit hc: HeaderCarrier): Future[HttpResponse] = {

    val enrolmentKey = s"${MandateConstants.AtedServiceContractName}~${MandateConstants.AtedIdentifier}~$clientId"
    val postUrl = s"""$enrolmentUrl/groups/$groupId/enrolments/$enrolmentKey?legacy-agentCode=$agentCode"""
    val jsonData = Json.toJson(input)

    val timerContext = metrics.startTimer(MetricsEnum.TaxEnrolmentAllocate)
    http.POST[JsValue, HttpResponse](postUrl, jsonData) map { response =>
      timerContext.stop()
      response.status match {
        case CREATED =>
          metrics.incrementSuccessCounter(MetricsEnum.TaxEnrolmentAllocate)
        case _ =>
          Logger.warn("allocateAgent failed")
          metrics.incrementFailedCounter(MetricsEnum.TaxEnrolmentAllocate)
          doFailedAudit("allocateAgentFailed", jsonData.toString, response.body)
      }
      response
    }
  }

  def deAllocateAgent(groupId: String, clientId: String, agentCode: String)(implicit hc: HeaderCarrier): Future[HttpResponse] = {

    val enrolmentKey = s"${MandateConstants.AtedServiceContractName}~${MandateConstants.AtedIdentifier}~$clientId"
    val deleteUrl = s"""$enrolmentUrl/groups/$groupId/enrolments/$enrolmentKey?legacy-agentCode=$agentCode"""

    val timerContext = metrics.startTimer(MetricsEnum.TaxEnrolmentDeallocate)

    http.DELETE[HttpResponse](deleteUrl).map({ response =>
      timerContext.stop()
      response.status match {
        case NO_CONTENT =>
          metrics.incrementSuccessCounter(MetricsEnum.TaxEnrolmentDeallocate)
        case status =>
          Logger.warn("deAllocateAgent failed")
          metrics.incrementFailedCounter(MetricsEnum.TaxEnrolmentDeallocate)
          doFailedAudit("deAllocateAgentFailed", s"$groupId-$clientId", response.body)
      }
      response
    })
  }
}
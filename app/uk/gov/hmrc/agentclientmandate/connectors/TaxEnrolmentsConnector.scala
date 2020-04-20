/*
 * Copyright 2020 HM Revenue & Customs
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
import scala.concurrent.{ExecutionContext, Future}


class DefaultTaxEnrolmentConnector @Inject()(val metrics: ServiceMetrics,
                                             val auditConnector: AuditConnector,
                                             val servicesConfig: ServicesConfig,
                                             val http: HttpClient) extends TaxEnrolmentConnector {
  val serviceUrl: String = servicesConfig.baseUrl("tax-enrolments")
  val enrolmentStoreProxyURL = s"${servicesConfig.baseUrl("enrolment-store-proxy")}/enrolment-store-proxy"
  val taxEnrolmentsUrl = s"$serviceUrl/tax-enrolments"
}

trait TaxEnrolmentConnector extends RawResponseReads with Auditable {

  def serviceUrl: String
  def enrolmentStoreProxyURL: String
  def taxEnrolmentsUrl: String
  def http: CoreDelete with CorePost with CoreGet
  def metrics: ServiceMetrics

  def allocateAgent(input: NewEnrolment, groupId: String, clientId: String, agentCode: String)(implicit hc: HeaderCarrier): Future[HttpResponse] = {
    val enrolmentKey = s"${MandateConstants.AtedServiceContractName}~${MandateConstants.AtedIdentifier}~$clientId"
    val postUrl = s"""$taxEnrolmentsUrl/groups/$groupId/enrolments/$enrolmentKey?legacy-agentCode=$agentCode"""
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

  def deAllocateAgent(agentPartyId: String, clientId: String, agentCode: String)(implicit hc: HeaderCarrier): Future[HttpResponse] = {
    val enrolmentKey = s"${MandateConstants.AtedServiceContractName}~${MandateConstants.AtedIdentifier}~$clientId"
    val agentGroupId = getGroupsWithEnrolment(agentPartyId)
    val deleteUrl = s"""$taxEnrolmentsUrl/groups/$agentGroupId/enrolments/$enrolmentKey?legacy-agentCode=$agentCode"""
    val timerContext = metrics.startTimer(MetricsEnum.TaxEnrolmentDeallocate)

    http.DELETE[HttpResponse](deleteUrl).map({ response =>
      timerContext.stop()
      response.status match {
        case NO_CONTENT =>
          metrics.incrementSuccessCounter(MetricsEnum.TaxEnrolmentDeallocate)
        case _ =>
          Logger.warn("deAllocateAgent failed")
          Logger.warn(s"AgentParty = $agentPartyId, Enrol Key = $enrolmentKey, AgentGroupId = $agentGroupId, DeleteUrl = $deleteUrl, Status = ${response.status}")
          metrics.incrementFailedCounter(MetricsEnum.TaxEnrolmentDeallocate)
          doFailedAudit("deAllocateAgentFailed", s"$agentGroupId-$clientId", response.body)
      }
      response
    })
  }

  def getGroupsWithEnrolment(agentRefNumber: String)(implicit hc: HeaderCarrier): Future[List[String]] = {
    val enrolmentKey = s"${MandateConstants.AgentServiceContractName}~${MandateConstants.AgentIdentifier}~$agentRefNumber"
    val getUrl = s"""$enrolmentStoreProxyURL/enrolment-store/enrolments/$enrolmentKey/groups"""

    http.GET[HttpResponse](s"$getUrl") map { response =>
      response.status match {
        case OK =>
          Logger.error(s"[getGroupsWithEnrolments]: successfully retrieved group ID")
          (response.json \ "principalGroupIds").as[List[String]]
        case _ =>
          Logger.error(s"[getGroupsWithEnrolments]: error retrieving group ID")
          Logger.warn(s"AgentREf = $agentRefNumber, Enrl Key = $enrolmentKey, GetuRL = $getUrl, Status = ${response.status}")
          throw new RuntimeException("Error retrieving agent group ID")
      }
    }
  }
}
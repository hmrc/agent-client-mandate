/*
 * Copyright 2023 HM Revenue & Customs
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
import play.api.libs.json.{JsValue, Json}
import uk.gov.hmrc.agentclientmandate.Auditable
import uk.gov.hmrc.agentclientmandate.metrics.{MetricsEnum, ServiceMetrics}
import uk.gov.hmrc.agentclientmandate.models.{NewEnrolment, UserGroupIDs}
import uk.gov.hmrc.agentclientmandate.utils.LoggerUtil.{logError, logInfo, logWarn}
import uk.gov.hmrc.agentclientmandate.utils.MandateConstants
import uk.gov.hmrc.http.{HttpClient, _}
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import scala.concurrent.{ExecutionContext, Future}


class DefaultTaxEnrolmentConnector @Inject()(val metrics: ServiceMetrics,
                                             val auditConnector: AuditConnector,
                                             val servicesConfig: ServicesConfig,
                                             val ec: ExecutionContext,
                                             val http: HttpClient) extends TaxEnrolmentConnector {
  val serviceUrl: String = servicesConfig.baseUrl("tax-enrolments")
  val enrolmentStoreProxyURL = s"${servicesConfig.baseUrl("enrolment-store-proxy")}/enrolment-store-proxy"
  val taxEnrolmentsUrl = s"$serviceUrl/tax-enrolments"
}

trait TaxEnrolmentConnector extends RawResponseReads with Auditable {

  implicit val ec: ExecutionContext

  def serviceUrl: String
  def enrolmentStoreProxyURL: String
  def taxEnrolmentsUrl: String
  def http: CoreDelete with CorePost with CoreGet
  def metrics: ServiceMetrics

  def allocateAgent(input: NewEnrolment, agentGroupId: String, clientAgentRef: String, agentCode: String)(implicit hc: HeaderCarrier): Future[HttpResponse] = {
    val enrolmentKey = s"${MandateConstants.AtedServiceContractName}~${MandateConstants.AtedIdentifier}~$clientAgentRef"
    val postUrl = s"""$taxEnrolmentsUrl/groups/$agentGroupId/enrolments/$enrolmentKey?legacy-agentCode=$agentCode"""
    val jsonData = Json.toJson(input)

    val timerContext = metrics.startTimer(MetricsEnum.TaxEnrolmentAllocate)
    http.POST[JsValue, HttpResponse](postUrl, jsonData) map { response =>
      timerContext.stop()
      response.status match {
        case CREATED =>
          logInfo("allocateAgent succeeded")
          metrics.incrementSuccessCounter(MetricsEnum.TaxEnrolmentAllocate)
        case _ =>
          logWarn(s"allocateAgent failed for clientAgentRef: $clientAgentRef with agentGroupID: $agentGroupId")
          metrics.incrementFailedCounter(MetricsEnum.TaxEnrolmentAllocate)
          doFailedAudit("allocateAgentFailed", jsonData.toString, response.body)
      }
      response
    }
  }

  def deAllocateAgent(agentPartyId: String, clientAgentRef: String, agentCode: String, userType: String)(implicit hc: HeaderCarrier): Future[HttpResponse] = {
    val enrolmentKey = s"${MandateConstants.AtedServiceContractName}~${MandateConstants.AtedIdentifier}~$clientAgentRef"

    getGroupsWithEnrolment(agentPartyId).flatMap { agentGroupId =>
      agentGroupId match {
        case Some(groupId) =>
          val deleteUrl = s"""$taxEnrolmentsUrl/groups/$groupId/enrolments/$enrolmentKey?legacy-agentCode=$agentCode"""
          val timerContext = metrics.startTimer(MetricsEnum.TaxEnrolmentDeallocate)
          http.DELETE[HttpResponse](deleteUrl).map { response =>
            timerContext.stop()
            response.status match {
              case NO_CONTENT =>
                logInfo("deAllocateAgent succeeded")
              case NOT_FOUND =>
                logWarn(s"$userType deAllocateAgent succeeded - did not find agent to deallocate in EACD for $agentGroupId")
              case _ =>
                logWarn(s"$userType deAllocateAgent clientAgentRef: $clientAgentRef with agentGroupID: $agentGroupId")
                metrics.incrementFailedCounter(MetricsEnum.TaxEnrolmentDeallocate)
                doFailedAudit("deAllocateAgentFailed", s"$agentGroupId-$clientAgentRef", response.body)
            }
            response
          }
        case None => throw new RuntimeException("No GroupID returned")
      }
    }
  }

  def getGroupsWithEnrolment(agentRefNumber: String)(implicit hc: HeaderCarrier): Future[Option[String]] = {
    val enrolmentKey = s"${MandateConstants.AgentServiceContractName}~${MandateConstants.AgentIdentifier}~$agentRefNumber"
    val getUrl = s"""$enrolmentStoreProxyURL/enrolment-store/enrolments/$enrolmentKey/groups"""

    http.GET[HttpResponse](s"$getUrl") map { response =>
      response.status match {

        case OK =>
          logInfo(s"[getGroupsWithEnrolments]: successfully retrieved group ID")
          response.json.as[UserGroupIDs].principalGroupIds.headOption
        case NO_CONTENT =>
          logWarn("[getGroupsWithEnrolments]: group ID not found")
          None
        case _ =>
          logError(s"[getGroupsWithEnrolments]: error retrieving group ID")
          throw new RuntimeException("Error retrieving agent group ID")
      }
    }
  }

  def getGroupsWithEnrolmentDelegatedAted(atedRefNumber: String)(implicit hc: HeaderCarrier): Future[Option[String]] = {
    val enrolmentKey = s"${MandateConstants.AtedServiceContractName}~${MandateConstants.AtedIdentifier}~$atedRefNumber"
    val getUrl = s"""$enrolmentStoreProxyURL/enrolment-store/enrolments/$enrolmentKey/groups"""

    http.GET[HttpResponse](s"$getUrl") map { response =>
      response.status match {
        case OK =>
          logInfo(s"[getGroupsWithEnrolments]: successfully retrieved group ID")
          response.json.as[UserGroupIDs].delegatedGroupIds.headOption
        case NO_CONTENT =>
          logWarn("[getGroupsWithEnrolments]: group ID not found")
          None
        case _ =>
          logError(s"[getGroupsWithEnrolments]: error retrieving group ID")
          throw new RuntimeException("Error retrieving agent group ID")
      }
    }
  }
}

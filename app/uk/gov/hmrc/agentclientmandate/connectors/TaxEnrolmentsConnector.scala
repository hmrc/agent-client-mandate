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
import play.api.http.Status._
import play.api.libs.json.{JsValue, Json}
import uk.gov.hmrc.agentclientmandate.Auditable
import uk.gov.hmrc.agentclientmandate.config.WSHttp
import uk.gov.hmrc.agentclientmandate.metrics.{Metrics, MetricsEnum}
import uk.gov.hmrc.agentclientmandate.models.{GsoAdminDeallocateAgentXmlInput, NewEnrolment}
import uk.gov.hmrc.agentclientmandate.utils.MandateConstants
import uk.gov.hmrc.http._
import uk.gov.hmrc.play.config.ServicesConfig

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future


trait TaxEnrolmentConnector extends ServicesConfig with RawResponseReads with Auditable {

  def serviceUrl: String

  def enrolmentUrl: String

  def http: CoreDelete with CorePost

  def metrics: Metrics

  def allocateAgent(input: NewEnrolment, groupId: String, credId: String)(implicit hc: HeaderCarrier): Future[HttpResponse] = {

    Logger.debug("****DB*****"+" calling allocate enrolment")
    val enrolmentKey = s"${MandateConstants.AtedServiceContractName}~${MandateConstants.AtedIdentifier}~$credId"
    val postUrl = s"""$enrolmentUrl/groups/$groupId/enrolments/$enrolmentKey"""
    val jsonData = Json.toJson(input)

    val timerContext = metrics.startTimer(MetricsEnum.TaxEnrolmentAllocate)
    http.POST[JsValue, HttpResponse](postUrl, jsonData) map { response =>
      timerContext.stop()
      response.status match {
        case CREATED =>
          metrics.incrementSuccessCounter(MetricsEnum.TaxEnrolmentAllocate)
          response
        case status =>
          Logger.warn("allocateAgent failed")
          metrics.incrementFailedCounter(MetricsEnum.TaxEnrolmentAllocate)
          doFailedAudit("allocateAgentFailed", jsonData.toString, response.body)
          response //TODO: Do we have process this response in some way
      }
    }
  }

  def deAllocateAgent(groupId: String, credId: String)(implicit hc: HeaderCarrier): Future[HttpResponse] =   {

    val enrolmentKey = s"${MandateConstants.AtedServiceContractName}~${MandateConstants.AtedIdentifier}~$credId"
    val deleteUrl = s"""$enrolmentUrl/groups/$groupId/enrolments/$enrolmentKey"""

    val timerContext = metrics.startTimer(MetricsEnum.GGProxyDeallocate)
    http.DELETE[HttpResponse](deleteUrl).map({ response =>
        timerContext.stop()
        response.status match {
          case NO_CONTENT =>
            metrics.incrementSuccessCounter(MetricsEnum.GGProxyDeallocate)
            response
          case status =>
            Logger.warn("deAllocateAgent failed")
            metrics.incrementFailedCounter(MetricsEnum.GGProxyDeallocate)
            doFailedAudit("deAllocateAgentFailed",s"$groupId-$credId", response.body)
            response
        }
      })
  }

}



object TaxEnrolmentConnector extends TaxEnrolmentConnector {
  // $COVERAGE-OFF$
  val http: CoreDelete with CorePost = WSHttp
  val metrics = Metrics
  val serviceUrl = baseUrl("enrolment-store-proxy")
  val enrolmentUrl = s"$serviceUrl/enrolment-store-proxy/enrolment-store"
  // $COVERAGE-ON$
}

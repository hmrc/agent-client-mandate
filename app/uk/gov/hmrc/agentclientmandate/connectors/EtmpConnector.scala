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
import uk.gov.hmrc.agentclientmandate.models.EtmpAtedAgentClientRelationship
import uk.gov.hmrc.http._
import uk.gov.hmrc.http.logging.Authorization
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import uk.gov.hmrc.play.bootstrap.http.HttpClient

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class DefaultEtmpConnector @Inject()(val metrics: ServiceMetrics,
                                     val auditConnector: AuditConnector,
                                     val servicesConfig: ServicesConfig,
                                     val http: HttpClient) extends EtmpConnector {
  val urlHeaderEnvironment: String = servicesConfig.getConfString("etmp-hod.environment", "")
  val urlHeaderAuthorization: String = s"Bearer ${servicesConfig.getConfString("etmp-hod.authorization-token", "")}"
  val etmpUrl: String = servicesConfig.baseUrl("etmp-hod")
}

trait EtmpConnector extends RawResponseReads with Auditable {

  val etmpUrl: String

  def urlHeaderEnvironment: String
  def urlHeaderAuthorization: String
  def http: CoreGet with CorePost
  def metrics: ServiceMetrics

  def maintainAtedRelationship(agentClientRelationship: EtmpAtedAgentClientRelationship): Future[HttpResponse] = {

    implicit val headerCarrier = createHeaderCarrier

    val jsonData = Json.toJson(agentClientRelationship)
    val postUrl = s"""$etmpUrl/annual-tax-enveloped-dwellings/relationship"""
    val timerContext = metrics.startTimer(MetricsEnum.MaintainAtedRelationship)
    http.POST(postUrl, jsonData) map { response =>
      timerContext.stop()
      response.status match {
        case OK | NO_CONTENT =>
          metrics.incrementSuccessCounter(MetricsEnum.MaintainAtedRelationship)
          response
        case status =>
          Logger.warn("maintainAtedRelationship failed")
          metrics.incrementFailedCounter(MetricsEnum.MaintainAtedRelationship)
          doFailedAudit("maintainRelationshipFailed", jsonData.toString, response.body)
          response
      }
    }
  }

  def getRegistrationDetails(identifier: String, identifierType: String): Future[JsValue] = {
    def getDetailsFromEtmp(getUrl: String): Future[JsValue] = {
      implicit val hc = createHeaderCarrier
      val timerContext = metrics.startTimer(MetricsEnum.EtmpGetDetails)
      http.GET[HttpResponse](getUrl).map { response =>
        timerContext.stop()
        response.status match {
          case OK =>
            metrics.incrementSuccessCounter(MetricsEnum.EtmpGetDetails)
            response.json
          case status =>
            metrics.incrementFailedCounter(MetricsEnum.EtmpGetDetails)
            doFailedAudit("getDetailsFromEtmpFailed", getUrl, response.body)
            throw new RuntimeException("No ETMP details found")
        }
      }
    }

    identifierType match {
      case "arn" => getDetailsFromEtmp(s"$etmpUrl/registration/details?arn=$identifier")
      case "safeid" => getDetailsFromEtmp(s"$etmpUrl/registration/details?safeid=$identifier")
      case "utr" => getDetailsFromEtmp(s"$etmpUrl/registration/details?utr=$identifier")
      case unknownIdentifier =>
        Logger.warn(s"[EtmpConnector][getDetails] - unexpected identifier type supplied of $unknownIdentifier")
        throw new RuntimeException(s"Unexpected identifier type supplied - $unknownIdentifier")
    }
  }

  def getAtedSubscriptionDetails(atedRefNo: String): Future[JsValue] = {
    implicit val headerCarrier = createHeaderCarrier
    val getUrl = s"""$etmpUrl/annual-tax-enveloped-dwellings/subscription/$atedRefNo"""
    val timerContext = metrics.startTimer(MetricsEnum.AtedSubscriptionDetails)
    http.GET[HttpResponse](s"$getUrl") map { response =>
      timerContext.stop()
      response.status match {
        case OK =>
          metrics.incrementSuccessCounter(MetricsEnum.AtedSubscriptionDetails)
          response.json
        case status =>
          metrics.incrementFailedCounter(MetricsEnum.AtedSubscriptionDetails)
          doFailedAudit("getAtedSubscriptionDetailsFailed", getUrl, response.body)
          throw new RuntimeException("Error in getting ATED subscription details from ETMP")
      }
    }
  }

  private def createHeaderCarrier: HeaderCarrier = {
    HeaderCarrier(extraHeaders = Seq("Environment" -> urlHeaderEnvironment),
      authorization = Some(Authorization(urlHeaderAuthorization)))
  }

}

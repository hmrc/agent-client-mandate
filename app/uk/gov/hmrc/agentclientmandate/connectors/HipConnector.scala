/*
 * Copyright 2026 HM Revenue & Customs
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

import play.api.http.Status
import play.api.http.Status._
import play.api.libs.json.{JsValue, Json}
import play.api.Logging
import uk.gov.hmrc.agentclientmandate.Auditable
import uk.gov.hmrc.agentclientmandate.metrics.{MetricsEnum, ServiceMetrics}
import uk.gov.hmrc.agentclientmandate.models.EtmpAtedAgentClientRelationship
import uk.gov.hmrc.agentclientmandate.utils.HipUtilities
import uk.gov.hmrc.agentclientmandate.utils.LoggerUtil.logWarn
import uk.gov.hmrc.http._
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import uk.gov.hmrc.http.HttpReads.Implicits._

import java.time.format.DateTimeFormatter
import java.time.{ZoneId, ZonedDateTime}
import java.util.{Base64, UUID}
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class DefaultHipConnector @Inject()(val metrics: ServiceMetrics,
                                     val auditConnector: AuditConnector,
                                     val ec: ExecutionContext,
                                     val servicesConfig: ServicesConfig,
val http: HttpClientV2) extends HipConnector {

  override val clientId: String = servicesConfig.getConfString("hip.clientId", "")
  override val clientSecret: String = servicesConfig.getConfString("hip.clientSecret", "")
  override val originatingSystem: String = servicesConfig.getConfString("hip.originatingSystem", "ATED")
  val hipPrefix: String = servicesConfig.baseUrl("hip")
  val hipUrl = s"${hipPrefix}/etmp/RESTAdapter/ated"
}

trait HipConnector extends Auditable with Logging {

  implicit val ec: ExecutionContext
  implicit val headerCarrier: HeaderCarrier = HeaderCarrier()

  def hipPrefix: String
  def clientId: String
  def clientSecret: String
  def authorizationToken: String = Base64.getEncoder.encodeToString(s"$clientId:$clientSecret".getBytes("UTF-8"))

  val transmittingSystem: String = "HIP"
  val originatingSystem: String
  val hipUrl: String

  def http: HttpClientV2

  def metrics: ServiceMetrics

  def headers: Seq[(String, String)] = Seq(
    "correlationid" -> UUID.randomUUID().toString,
    "X-Originating-System" -> originatingSystem,
    "X-Receipt-Date" -> retrieveCurrentTime,
    "X-Transmitting-System" -> transmittingSystem,
    "Authorization" -> s"Basic $authorizationToken"
  )

  private def retrieveCurrentTime: String = {
    val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'")
    formatter.format(ZonedDateTime.now(ZoneId.of("UTC")))
  }

  def maintainAtedRelationship(agentClientRelationship: EtmpAtedAgentClientRelationship): Future[HttpResponse] = {
    val jsonData = HipUtilities.removeAcknowledgementReferenceField(Json.toJson(agentClientRelationship))
    val postUrl = s"""$hipUrl/relationship"""
    val timerContext = metrics.startTimer(MetricsEnum.MaintainAtedRelationship)

    http.post(url"$postUrl").withBody(jsonData).setHeader(headers: _*).execute[HttpResponse].map{ response =>
      timerContext.stop()
      response.status match {
        case CREATED =>
          metrics.incrementSuccessCounter(MetricsEnum.MaintainAtedRelationship)
          val strippedJson = HipUtilities.stripSuccessWrapper(response.json)
          HttpResponse(
            status = OK,
            body = Json.stringify(strippedJson),
            headers = response.headers
          )
        case UNPROCESSABLE_ENTITY =>

          val badRequestErrorCodes = Set("001", "002", "003")

          HipUtilities.extractHipErrorCode(response.body) match {
            case Some((code, text)) if badRequestErrorCodes.contains(code) =>
              metrics.incrementFailedCounter(MetricsEnum.MaintainAtedRelationship)
              logWarn(s"[HipDetailsConnector][maintainAtedRelationship] - $text")
              doFailedAudit("maintainAtedRelationshipFailed", postUrl, response.body)
              HttpResponse(
                status = Status.BAD_REQUEST,
                body = response.body,
                headers = response.headers
              )

            case status =>
              metrics.incrementFailedCounter(MetricsEnum.MaintainAtedRelationship)
              logWarn(s"[HipConnector][maintainAtedRelationship] - Unsuccessful return of data. Status : $status")
              doFailedAudit("maintainAtedRelationshipFailed", postUrl, response.body)
              HttpResponse(
                status = Status.INTERNAL_SERVER_ERROR,
                body = response.body,
                headers = response.headers
              )
          }
        case _ =>
          logWarn("maintainAtedRelationship failed")
          metrics.incrementFailedCounter(MetricsEnum.MaintainAtedRelationship)
          doFailedAudit("maintainRelationshipFailed", jsonData.toString, response.body)
          response
      }
    }
  }

  def getAtedSubscriptionDetails(atedRefNo: String): Future[JsValue] = {
    val getUrl = s"""$hipUrl/subscription/$atedRefNo"""
    val timerContext = metrics.startTimer(MetricsEnum.AtedSubscriptionDetails)
    http.get(url"$getUrl").setHeader(headers: _*).execute[HttpResponse].map{ response =>
      timerContext.stop()

      response.status match {
        case OK =>
          metrics.incrementSuccessCounter(MetricsEnum.AtedSubscriptionDetails)
          HipUtilities.stripSuccessWrapper(response.json)
        case _ =>
          metrics.incrementFailedCounter(MetricsEnum.AtedSubscriptionDetails)
          doFailedAudit("getAtedSubscriptionDetailsFailed", getUrl, response.body)
          throw new RuntimeException("Error in getting ATED subscription details from ETMP")
      }
    }
  }
}

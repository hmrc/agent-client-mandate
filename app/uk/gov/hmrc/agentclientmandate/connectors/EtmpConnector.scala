/*
 * Copyright 2016 HM Revenue & Customs
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
import uk.gov.hmrc.agentclientmandate.config.WSHttp
import uk.gov.hmrc.agentclientmandate.models.EtmpAtedAgentClientRelationship
import uk.gov.hmrc.play.config.ServicesConfig
import uk.gov.hmrc.play.http._
import uk.gov.hmrc.play.http.logging.Authorization

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait EtmpConnector extends ServicesConfig with RawResponseReads {

  val etmpUrl: String = baseUrl("etmp-hod")
  def urlHeaderEnvironment: String
  def urlHeaderAuthorization: String
  def http: HttpGet with HttpPost with HttpPut


  def maintainAtedRelationship(agentClientRelationship: EtmpAtedAgentClientRelationship): Future[HttpResponse] = {

    implicit val headerCarrier = createHeaderCarrier

    val jsonData = Json.toJson(agentClientRelationship)
    val postUrl = s"""$etmpUrl/annual-tax-enveloped-dwellings/relationship"""
    Logger.debug(s"[EtmpConnector][maintainAtedRelationship] - POST $postUrl & payload = $jsonData")
    http.POST(postUrl, jsonData) map { response =>
      response.status match {
        case OK | NO_CONTENT =>
          response
        case status =>
          Logger.warn(s"[EtmpConnector][maintainAtedRelationship] - status: $status Error ${response.body}")
          throw new RuntimeException("ETMP call failed")
      }
    }
  }

  def getDetailsFromEtmp(arn: String): Future[JsValue] = {

    implicit val hc = createHeaderCarrier

    http.GET[HttpResponse](s"$etmpUrl/registration/details?arn=$arn") map { response =>
      Logger.debug(s"[EtmpConnector][getDetailsFromEtmp] - response.status = ${response.status} && response.body = ${response.body}")
      response.status match {
        case OK => response.json
        case status => throw new RuntimeException("No ETMP details found")
      }
    }
  }

  private def createHeaderCarrier: HeaderCarrier = {
    HeaderCarrier(extraHeaders = Seq("Environment" -> urlHeaderEnvironment),
      authorization = Some(Authorization(urlHeaderAuthorization)))
  }
}

object EtmpConnector extends EtmpConnector {
  // $COVERAGE-OFF$
  val urlHeaderEnvironment: String = config("etmp-hod").getString("environment").fold("")(x => x)
  val urlHeaderAuthorization: String = s"Bearer ${config("etmp-hod").getString("authorization-token").fold("")(x => x)}"
  val http: HttpGet with HttpPost with HttpPut = WSHttp
  // $COVERAGE-ON$
}

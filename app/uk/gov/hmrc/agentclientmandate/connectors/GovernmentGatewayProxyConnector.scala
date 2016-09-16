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

import play.api.http.HeaderNames.CONTENT_TYPE
import play.api.http.ContentTypes.XML
import uk.gov.hmrc.agentclientmandate.WSHttp
import uk.gov.hmrc.agentclientmandate.models.{GsoAdminAllocateAgentXmlInput, Identifier}
import uk.gov.hmrc.play.config.ServicesConfig
import uk.gov.hmrc.play.http._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future


trait GovernmentGatewayProxyConnector extends ServicesConfig {

  def serviceUrl = baseUrl("government-gateway-proxy")
  def http: HttpGet with HttpPost with HttpPut = WSHttp

  def allocateAgent(input: GsoAdminAllocateAgentXmlInput)(implicit hc: HeaderCarrier): Future[HttpResponse] = {
    http.POSTString(serviceUrl + "/api/admin/GsoAdminAllocateAgent", input.toXml.toString, Seq(CONTENT_TYPE -> XML))
      .map({ r =>
        logResponse(input.agentCode, input.serviceName, input.identifiers, r.body)
        r
      })
  }

  def logResponse(agentCode: String, serviceName: String, identifiers: List[Identifier], body: String)(implicit hc: HeaderCarrier): Unit = {
    //TODO
  }

}


object GovernmentGatewayProxyConnector extends GovernmentGatewayProxyConnector {
}

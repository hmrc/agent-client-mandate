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

package uk.gov.hmrc.agentclientmandate.services

import uk.gov.hmrc.agentclientmandate.connectors.GovernmentGatewayProxyConnector
import uk.gov.hmrc.agentclientmandate.models.{GsoAdminAllocateAgentXmlInput, Identifier, Mandate}
import uk.gov.hmrc.play.http.{HeaderCarrier, HttpResponse}

import scala.concurrent.Future

trait AllocateAgentService {

  def connector: GovernmentGatewayProxyConnector

  def allocateAgent(mandate: Mandate, agentCode: String)(implicit hc: HeaderCarrier):Future[HttpResponse] = {

    //TODO replace ATED value with config lookup
    connector.allocateAgent(GsoAdminAllocateAgentXmlInput(List(Identifier("ATEDRefNumber", mandate.clientParty.get.id)), agentCode, mandate.subscription.service.name))
  }
}

object AllocateAgentService extends AllocateAgentService {
  // $COVERAGE-OFF$
  val connector = GovernmentGatewayProxyConnector
  // $COVERAGE-ON$
}
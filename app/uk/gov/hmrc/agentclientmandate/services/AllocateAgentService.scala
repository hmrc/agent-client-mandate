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

import uk.gov.hmrc.agentclientmandate.config.ApplicationConfig._
import uk.gov.hmrc.agentclientmandate.connectors.{EtmpConnector, GovernmentGatewayProxyConnector}
import uk.gov.hmrc.agentclientmandate.models._
import uk.gov.hmrc.agentclientmandate.utils.SessionUtils
import uk.gov.hmrc.play.http.{HeaderCarrier, HttpResponse}
import play.api.http.Status._
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

trait AllocateAgentService {

  def ggProxyConnector: GovernmentGatewayProxyConnector
  def etmpConnector: EtmpConnector

  def allocateAgent(mandate: Mandate, agentCode: String)(implicit hc: HeaderCarrier): Future[HttpResponse] = {

    val identifier = identifiers.getString(s"${mandate.subscription.service.id}.identifier")
    val clientId = mandate.clientParty.get.id

    etmpConnector.submitPendingClient(createEtmpRelationship(clientId, mandate.agentParty.id), mandate.subscription.service.name).flatMap { etmpResponse =>
      etmpResponse.status match {
        case OK => ggProxyConnector.allocateAgent(
                      GsoAdminAllocateAgentXmlInput(
                        List(Identifier(identifier, clientId)),
                        agentCode,
                        mandate.subscription.service.name
                      )
                    )
        case _ => Future.successful(etmpResponse)
      }
    }
  }

  private def createEtmpRelationship(clientId: String, agentId: String) = {
    val etmpRelationship = EtmpRelationship(action = "Authorise", isExclusiveAgent = true)
    Some(EtmpAgentClientRelationship(SessionUtils.getUniqueAckNo, clientId, agentId, etmpRelationship))
  }
}

object AllocateAgentService extends AllocateAgentService {
  // $COVERAGE-OFF$
  val ggProxyConnector = GovernmentGatewayProxyConnector
  val etmpConnector = EtmpConnector
  // $COVERAGE-ON$
}

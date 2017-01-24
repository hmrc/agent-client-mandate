/*
 * Copyright 2017 HM Revenue & Customs
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

import play.api.http.Status._
import uk.gov.hmrc.agentclientmandate.config.ApplicationConfig._
import uk.gov.hmrc.agentclientmandate.connectors.{AuthConnector, EtmpConnector, GovernmentGatewayProxyConnector}
import uk.gov.hmrc.agentclientmandate.metrics.{Metrics, MetricsEnum}
import uk.gov.hmrc.agentclientmandate.models._
import uk.gov.hmrc.agentclientmandate.utils.SessionUtils
import uk.gov.hmrc.domain.AtedUtr
import uk.gov.hmrc.play.http.{BadRequestException, HeaderCarrier, HttpResponse}
import uk.gov.hmrc.agentclientmandate.utils.MandateConstants._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait RelationshipService {

  def ggProxyConnector: GovernmentGatewayProxyConnector

  def etmpConnector: EtmpConnector

  def metrics: Metrics

  def maintainRelationship(mandate: Mandate, agentCode: String, action: String)(implicit hc: HeaderCarrier): Future[HttpResponse] = {

    if (mandate.subscription.service.name.toUpperCase == AtedService) {
      val serviceId = mandate.subscription.service.id
      val identifier = identifiers.getString(s"${serviceId.toLowerCase()}.identifier")
      val clientId = mandate.subscription.referenceNumber.getOrElse("")

      etmpConnector.maintainAtedRelationship(createEtmpRelationship(clientId, mandate.agentParty.id, action)).flatMap { etmpResponse =>
        etmpResponse.status match {
          case OK =>
            action match {
              case  Authorise =>
                ggProxyConnector.allocateAgent(
                  GsoAdminAllocateAgentXmlInput(
                    List(Identifier(identifier, clientId)),
                    agentCode,
                    AtedServiceContractName)).map { resp =>
                  resp.status match {
                    case OK =>
                      metrics.incrementSuccessCounter(MetricsEnum.GGProxyAllocate)
                      resp
                    case _ =>
                      metrics.incrementFailedCounter(MetricsEnum.GGProxyAllocate)
                      throw new RuntimeException("Authorise - GG Proxy call failed")
                  }
                }
              case DeAuthorise =>
                ggProxyConnector.deAllocateAgent(
                  GsoAdminDeallocateAgentXmlInput(
                    List(Identifier(identifier, clientId)),
                    agentCode,
                    AtedServiceContractName)).map { resp =>
                  resp.status match {
                    case OK =>
                      metrics.incrementSuccessCounter(MetricsEnum.GGProxyDeallocate)
                      resp
                    case _ =>
                      metrics.incrementFailedCounter(MetricsEnum.GGProxyDeallocate)
                      throw new RuntimeException("De-Authorise - GG Proxy call failed")
                  }
                }
            }
          case _ => throw new RuntimeException("ETMP call failed")
        }
      }
    }
    else {
      throw new BadRequestException("This is only defined for ATED")
    }
  }

  private def createEtmpRelationship(clientId: String, agentId: String, action: String) = {
    action match {
      case Authorise => EtmpAtedAgentClientRelationship(SessionUtils.getUniqueAckNo, clientId, agentId,
        EtmpRelationship(action = action, isExclusiveAgent = Some(true)))
      case DeAuthorise => EtmpAtedAgentClientRelationship(SessionUtils.getUniqueAckNo, clientId, agentId,
        EtmpRelationship(action = action, isExclusiveAgent = None))
    }
  }

}

object RelationshipService extends RelationshipService {
  // $COVERAGE-OFF$
  val ggProxyConnector: GovernmentGatewayProxyConnector = GovernmentGatewayProxyConnector
  val etmpConnector: EtmpConnector = EtmpConnector
  val authConnector: AuthConnector = AuthConnector

  val metrics = Metrics
  // $COVERAGE-ON$
}

package uk.gov.hmrc.agentclientmandate.tasks

import play.api.http.Status._
import uk.gov.hmrc.agentclientmandate.{Auditable, models}
import uk.gov.hmrc.agentclientmandate.connectors.{EtmpConnector, GovernmentGatewayProxyConnector}
import uk.gov.hmrc.agentclientmandate.metrics.MetricsEnum
import uk.gov.hmrc.agentclientmandate.models.{EtmpAtedAgentClientRelationship, EtmpRelationship, GsoAdminAllocateAgentXmlInput, Identifier}
import uk.gov.hmrc.agentclientmandate.repositories._
import uk.gov.hmrc.agentclientmandate.services.{MandateFetchService, MandateUpdateService}
import uk.gov.hmrc.agentclientmandate.utils.MandateConstants._
import uk.gov.hmrc.agentclientmandate.utils.{MandateConstants, SessionUtils}
import uk.gov.hmrc.tasks._

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.util.{Failure, Success, Try}

class ActivationTaskExecutor extends TaskExecutor with Auditable {

  val etmpConnector: EtmpConnector = EtmpConnector
  val ggProxyConnector: GovernmentGatewayProxyConnector = GovernmentGatewayProxyConnector
  val updateService: MandateUpdateService = MandateUpdateService
  val fetchService: MandateFetchService = MandateFetchService

  override def execute(signal: Signal): Try[Signal] = {

    signal match {
      case Start(args) => {
        val request = createRelationship(args("clientId"), args("agentPartyId"))
        val result = Await.result(etmpConnector.maintainAtedRelationship(request), 1 second)
        result.status match {
          case OK => Success(Next("gg-proxy", args))
          case _ => Failure(new Exception("ETMP call failed, status: " + result.status))
        }
      }

      case Next("gg-proxy", args) => {
        val request = GsoAdminAllocateAgentXmlInput(
                    List(Identifier(args("serviceIdentifier"), args("clientId"))),
                    args("agentCode"), AtedServiceContractName)
        val result = Await.result(ggProxyConnector.allocateAgent(request), 1 second)
        result.status match {
          case OK => Success(Next("finalize", args))
          case _ => Failure(new Exception("GG Proxy call failed, status: " + result.status))
        }
      }

      case Next("finalize", args) => {
        val fetchResult = Await.result(fetchService.fetchClientMandate(args("mandateId")), 1 second)
        fetchResult match {
          case MandateFetched(mandate) => {
            val updateResult = Await.result(updateService.updateMandate(mandate, Some(models.Status.Active)), 1 second)
            updateResult match {
              case MandateUpdated(x) => Success(Finish)
              case MandateUpdateError => Failure(new Exception("Could not update mandate to activate"))
            }
          }
          case MandateNotFound => Failure(new Exception("Could not find mandate to activate"))
        }
      }
    }
  }

  override def onFailed(lastSignal: Signal): Unit = {
    throw new Exception("ActivationTaskExecutor has failed")
  }

  private def createRelationship(clientId: String, agentId: String) = {
    EtmpAtedAgentClientRelationship(SessionUtils.getUniqueAckNo, clientId, agentId, EtmpRelationship(action = "Authorise", isExclusiveAgent = Some(true)))
  }

}

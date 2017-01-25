package uk.gov.hmrc.agentclientmandate.tasks

import play.api.Logger
import play.api.http.Status._
import uk.gov.hmrc.agentclientmandate.connectors.{EtmpConnector, GovernmentGatewayProxyConnector}
import uk.gov.hmrc.agentclientmandate.models.{EtmpAtedAgentClientRelationship, EtmpRelationship, GsoAdminAllocateAgentXmlInput, Identifier}
import uk.gov.hmrc.agentclientmandate.repositories._
import uk.gov.hmrc.agentclientmandate.services.{MandateFetchService, MandateUpdateService, NotificationEmailService}
import uk.gov.hmrc.agentclientmandate.utils.MandateConstants._
import uk.gov.hmrc.agentclientmandate.utils.SessionUtils
import uk.gov.hmrc.agentclientmandate.{Auditable, models}
import uk.gov.hmrc.tasks._

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}

class ActivationTaskExecutor extends TaskExecutor with Auditable {

  val etmpConnector: EtmpConnector = EtmpConnector
  val ggProxyConnector: GovernmentGatewayProxyConnector = GovernmentGatewayProxyConnector
  val updateService: MandateUpdateService = MandateUpdateService
  val fetchService: MandateFetchService = MandateFetchService
  val emailNotificationService: NotificationEmailService = NotificationEmailService

  override def execute(signal: Signal): Try[Signal] = {

    signal match {
      case Start(args) => {
        val request = createRelationship(args("clientId"), args("agentPartyId"))
        val result = Await.result(etmpConnector.maintainAtedRelationship(request), 5 seconds)
        result.status match {
          case OK => Success(Next("gg-proxy", args))
          case _ => {
            Logger.warn(s"[ActivationTaskExecutor] - call to ETMP failed with status ${result.status} with body ${result.body}")
            Failure(new Exception("ETMP call failed, status: " + result.status))
          }
        }
      }

      case Next("gg-proxy", args) => {
        val request = GsoAdminAllocateAgentXmlInput(
                    List(Identifier(args("serviceIdentifier"), args("clientId"))),
                    args("agentCode"), AtedServiceContractName)
        val result = Await.result(ggProxyConnector.allocateAgent(request), 5 seconds)
        result.status match {
          case OK => Success(Next("finalize", args))
          case _ => {
            Logger.warn(s"[ActivationTaskExecutor] - call to gg-proxy failed with status ${result.status} with body ${result.body}")
            Failure(new Exception("GG Proxy call failed, status: " + result.status))
          }
        }
      }

      case Next("finalize", args) => {
        val fetchResult = Await.result(fetchService.fetchClientMandate(args("mandateId")), 3 seconds)
        fetchResult match {
          case MandateFetched(mandate) => {
            val updateResult = Await.result(updateService.updateMandate(mandate, Some(models.Status.Active)), 3 seconds)
            updateResult match {
              case MandateUpdated(m) => {
                val clientEmail = m.clientParty.map(_.contactDetails.email).getOrElse("")
                val service = m.subscription.service.id
                emailNotificationService.sendMail(clientEmail, models.Status.Active, service = service)
                doAudit("activated", args("agentCode"), m)
                Success(Finish)
              }
              case MandateUpdateError => {
                Logger.warn(s"[ActivationTaskExecutor] - could not update mandate with id ${args("mandateId")}")
                Failure(new Exception("Could not update mandate to activate"))
              }
            }
          }
          case MandateNotFound => {
            Logger.warn(s"[ActivationTaskExecutor] - could not find mandate with id ${args("mandateId")}")
            Failure(new Exception("Could not find mandate to activate"))
          }
        }
      }
    }
  }

  override def onFailed(lastSignal: Signal): Unit = {
    lastSignal match {
      case Start(args) => {
        Logger.warn("[ActivationTaskExecutor] start failed")
        // setting back to Approved status so agent can try again
        val fetchResult = Await.result(fetchService.fetchClientMandate(args("mandateId")), 3 seconds)
        fetchResult match {
          case MandateFetched(mandate) => {
            Await.result(updateService.updateMandate(mandate, Some(models.Status.Approved)), 1 second)
          }
        }
      }
      case Next("gg-proxy", args) => {
        Logger.warn("[ActivationTaskExecutor] stage 1 failed")
        // rolling back ETMP as we have failed GG proxy call
      }
      case Next("finalize", args) => {
        Logger.error("[ActivationTaskExecutor] stage 2 failed")
        // leaving for manual intervention as etmp and gg proxy were successful
      }
    }
  }

  private def createRelationship(clientId: String, agentId: String) = {
    EtmpAtedAgentClientRelationship(SessionUtils.getUniqueAckNo, clientId, agentId, EtmpRelationship(action = "Authorise", isExclusiveAgent = Some(true)))
  }

}

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

package uk.gov.hmrc.agentclientmandate.tasks

import org.joda.time.DateTime
import uk.gov.hmrc.agentclientmandate.connectors.{EtmpConnector, GovernmentGatewayProxyConnector}
import uk.gov.hmrc.agentclientmandate.models._
import uk.gov.hmrc.agentclientmandate.repositories._
import uk.gov.hmrc.agentclientmandate.services.{MandateFetchService, MandateUpdateService, NotificationEmailService}
import uk.gov.hmrc.agentclientmandate.utils.MandateConstants._
import uk.gov.hmrc.agentclientmandate.utils.MandateUtils._
import uk.gov.hmrc.agentclientmandate.{Auditable, models}
import uk.gov.hmrc.tasks._

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}
import play.api.Logger
import play.api.http.Status._
import uk.gov.hmrc.agentclientmandate.metrics.{Metrics, MetricsEnum}
import uk.gov.hmrc.play.http.HeaderCarrier

class DeActivationTaskExecutor extends TaskExecutor with Auditable {

  val etmpConnector: EtmpConnector = EtmpConnector
  val ggProxyConnector: GovernmentGatewayProxyConnector = GovernmentGatewayProxyConnector
  val updateService: MandateUpdateService = MandateUpdateService
  val fetchService: MandateFetchService = MandateFetchService
  val emailNotificationService: NotificationEmailService = NotificationEmailService
  val mandateRepository: MandateRepository = MandateRepository()
  override val metrics: Metrics = Metrics

  implicit val hc = new HeaderCarrier()

  override def execute(signal: Signal): Try[Signal] = {
    signal match {
      case Start(args) => {
        val request = breakRelationship(args("clientId"), args("agentPartyId"))
        val result = Await.result(etmpConnector.maintainAtedRelationship(request), 5 seconds)
        result.status match {
          case OK => Success(Next("gg-proxy-deactivation", args))
          case _ => {
            Logger.warn(s"[DeActivationTaskExecutor] - call to ETMP failed with status ${result.status} with body ${result.body}")
            Failure(new Exception("ETMP call failed, status: " + result.status))
          }
        }
      }
      case Next("gg-proxy-deactivation", args) => {
        val request = GsoAdminDeallocateAgentXmlInput(
          List(Identifier(args("serviceIdentifier"), args("clientId"))),
          args("agentCode"), AtedServiceContractName)
        val result = Await.result(ggProxyConnector.deAllocateAgent(request), 5 seconds)
        result.status match {
          case OK =>
            metrics.incrementSuccessCounter(MetricsEnum.GGProxyDeallocate)
            Success(Next("finalize-deactivation", args))
          case _ => {
            metrics.incrementFailedCounter(MetricsEnum.GGProxyDeallocate)
            Logger.warn(s"[DeActivationTaskExecutor] - call to gg-proxy failed with status ${result.status} with body ${result.body}")
            Failure(new Exception("GG Proxy call failed, status: " + result.status))
          }
        }
      }
      case Next("finalize-deactivation", args) => {
        val fetchResult = Await.result(fetchService.fetchClientMandate(args("mandateId")), 3 seconds)
        fetchResult match {
          case MandateFetched(mandate) => {
            val updatedMandate = mandate.updateStatus(MandateStatus(Status.Cancelled, DateTime.now, args("credId")))
            val updateResult = Await.result(mandateRepository.updateMandate(updatedMandate), 3 seconds)
            updateResult match {
              case MandateUpdated(m) => {
                args("userType") match {
                  case "agent" =>
                    val clientEmail = m.clientParty.map(_.contactDetails.email).getOrElse("")
                    val service = m.subscription.service.id
                    emailNotificationService.sendMail(clientEmail, models.Status.Cancelled, Some(args("userType")), service)
                  case _ =>
                    val agentEmail = m.agentParty.contactDetails.email
                    val service = m.subscription.service.id
                    emailNotificationService.sendMail(agentEmail, models.Status.Cancelled, Some(args("userType")), service, Some(mandate.currentStatus.status))
                }
                doAudit("removed", args("agentCode"), m)
                Success(Finish)
              }
              case MandateUpdateError => {
                Logger.warn(s"[DeActivationTaskExecutor] - could not update mandate with id ${args("mandateId")}")
                Failure(new Exception("Could not update mandate to activate"))
              }
            }
          }
          case MandateNotFound => {
            Logger.warn(s"[DeActivationTaskExecutor] - could not find mandate with id ${args("mandateId")}")
            Failure(new Exception("Could not find mandate to activate"))
          }
        }
      }
    }
  }

  override def rollback(signal: Signal): Try[Signal] = {
    signal match {
      case Start(args) => {
        Logger.warn("[DeActivationTaskExecutor] start failed")
        // setting back to Active from PendingCancellation status so agent can try again
        val fetchResult = Await.result(fetchService.fetchClientMandate(args("mandateId")), 3 seconds)
        fetchResult match {
          case MandateFetched(mandate) => {
            val updatedMandate = mandate.updateStatus(MandateStatus(Status.Active, DateTime.now, args("credId")))
            Await.result(mandateRepository.updateMandate(updatedMandate), 1 second)
            Success(Finish)
          }
        }
      }
      //failed doing allocate agent in GG
      case Next("gg-proxy-deactivation", args) => {
        Logger.warn("[DeActivationTaskExecutor] gg-proxy de-allocate agent failed")
        // rolling back ETMP as we have failed GG proxy call
        val request = createRelationship(args("clientId"), args("agentPartyId"))
        val result = Await.result(etmpConnector.maintainAtedRelationship(request), 5 seconds)
        Success(Start(args))
      }
      //failed to update the status in Mongo from PendingCancellation to Cancelled
      case Next("finalize-deactivation", args) => {
        Logger.error("[DeActivationTaskExecutor] Mongo update failed")
        // leaving for manual intervention as etmp and gg proxy were successful
        Success(Next("gg-proxy-deactivation", args))
      }
    }
  }

  override def onRollbackFailure(lastSignal: Signal) = {
    Logger.error("[DeActivationTaskExecutor] Rollback action failed")
  }

}

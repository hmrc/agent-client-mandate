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
import play.api.Logger
import play.api.http.Status._
import uk.gov.hmrc.agentclientmandate.connectors.{EtmpConnector, GovernmentGatewayProxyConnector}
import uk.gov.hmrc.agentclientmandate.metrics.{Metrics, MetricsEnum}
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
import uk.gov.hmrc.play.http.HeaderCarrier

class ActivationTaskExecutor extends TaskExecutor with Auditable {

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
        Logger.warn(s"[ActivationTaskExecutor] Entering Start(args)....")
        val request = createRelationship(args("clientId"), args("agentPartyId"))
        val result = Await.result(etmpConnector.maintainAtedRelationship(request), 5 seconds)
        Logger.warn(s"[ActivationTaskExecutor] Inside Start(args)---etmpConnector.maintainAtedRelationship::${result.status}")
        result.status match {
          case OK =>
            Logger.warn(s"[ActivationTaskExecutor] Inside Start(args)---etmpConnector.maintainAtedRelationship::SUCCESS")
            Success(Next("gg-proxy-activation", args))
          case _ => {
            Logger.warn(s"[ActivationTaskExecutor] - call to ETMP failed with status ${result.status} with body ${result.body}")
            Failure(new Exception("ETMP call failed, status: " + result.status))
          }
        }
      }

      case Next("gg-proxy-activation", args) => {
        Logger.warn(s"[ActivationTaskExecutor] Entering Next('gg-proxy-activation', args)....")
        val request = GsoAdminAllocateAgentXmlInput(
          List(Identifier(args("serviceIdentifier"), args("clientId"))),
          args("agentCode"), AtedServiceContractName)
        Try(Await.result(ggProxyConnector.allocateAgent(request), 5 seconds)) match {
          case Success(resp) =>
            resp.status match {
              case OK =>
                Logger.warn(s"[ActivationTaskExecutor] Inside Next('gg-proxy-activation', args)---ggProxyConnector.allocateAgent::SUCCESS")
                metrics.incrementSuccessCounter(MetricsEnum.GGProxyAllocate)
                Success(Next("finalize-activation", args))
              case _ =>
                Logger.warn(s"[ActivationTaskExecutor] Inside Next('gg-proxy-activation', args)---ggProxyConnector.allocateAgent::NOT SUCCESS")
                metrics.incrementFailedCounter(MetricsEnum.GGProxyAllocate)
                Failure(new Exception("GG Proxy call failed, status: " + resp.status))
            }
          case Failure(ex) =>
            // $COVERAGE-OFF$
            Logger.warn(s"[ActivationTaskExecutor] execption while calling allocateAgent :: ${ex.getMessage}")
            Failure(new Exception("GG Proxy call failed, status: " + ex.getMessage))
          // $COVERAGE-ON$
        }
      }

      case Next("finalize-activation", args) => {
        val fetchResult = Await.result(fetchService.fetchClientMandate(args("mandateId")), 3 seconds)
        Logger.warn(s"[ActivationTaskExecutor] Entering Next('finalize-activation', args)---after fetchService.fetchClientMandate")
        fetchResult match {
          case MandateFetched(mandate) => {
            Logger.warn(s"[ActivationTaskExecutor] Entering Next('finalize-activation', args)---after case MandateFetched(mandate):::$mandate")
            val updatedMandate = mandate.updateStatus(MandateStatus(Status.Active, DateTime.now, args("credId")))
            val updateResult = Await.result(mandateRepository.updateMandate(updatedMandate), 3 seconds)
            Logger.warn(s"[ActivationTaskExecutor] Inside Next('finalize-activation', args)---after cmandateRepository.updateMandate(updatedMandate)")
            updateResult match {
              case MandateUpdated(m) => {
                Logger.warn(s"[ActivationTaskExecutor] Entering Next('finalize-activation', args)---after  case MandateUpdated(m):::$m")
                val clientEmail = m.clientParty.map(_.contactDetails.email).getOrElse("")
                val service = m.subscription.service.id
                Try(emailNotificationService.sendMail(clientEmail, models.Status.Active, service = service)) match {
                  case Success(v) =>
                    Logger.warn(s"[ActivationTaskExecutor] Email Sent SUCCESS")
                    doAudit("emailSent", args("agentCode"), m)
                  case Failure(reason) =>
                    // $COVERAGE-OFF$
                    Logger.warn(s"[ActivationTaskExecutor] Email Sent FAILURE")
                    doFailedAudit("emailSentFailed", clientEmail + models.Status.Active + service, reason.getMessage)
                  // $COVERAGE-ON$
                }
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

  override def rollback(signal: Signal): Try[Signal] = {
    signal match {
      case Start(args) => {
        Logger.warn("[ActivationTaskExecutor] start failed")
        // setting back to Approved from PendingActivation status so agent can try again
        val fetchResult = Await.result(fetchService.fetchClientMandate(args("mandateId")), 3 seconds)
        fetchResult match {
          case MandateFetched(mandate) => {
            val updatedMandate = mandate.updateStatus(MandateStatus(Status.Approved, DateTime.now, args("credId")))
            Await.result(mandateRepository.updateMandate(updatedMandate), 1 second)
            Success(Finish)
          }
        }
      }
      //failed doing allocate agent in GG
      case Next("gg-proxy-activation", args) => {
        Logger.warn("[ActivationTaskExecutor] gg-proxy allocate failed")
        // rolling back ETMP as we have failed GG proxy call
        val request = breakRelationship(args("clientId"), args("agentPartyId"))
        val result = Await.result(etmpConnector.maintainAtedRelationship(request), 5 seconds)
        Success(Start(args))
      }
      //failed to update the status in Mongo from PendingActivation to Active
      case Next("finalize-activation", args) => {
        Logger.error("[ActivationTaskExecutor] Mongo update failed")
        // leaving for manual intervention as etmp and gg proxy were successful
        Success(Next("gg-proxy-activation", args))
      }
    }
  }

  override def onRollbackFailure(lastSignal: Signal) = {
    Logger.error("[ActivationTaskExecutor] Rollback action failed")
  }

}

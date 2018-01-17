/*
 * Copyright 2018 HM Revenue & Customs
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
import uk.gov.hmrc.agentclientmandate.connectors.{EtmpConnector, GovernmentGatewayProxyConnector, TaxEnrolmentConnector}
import uk.gov.hmrc.agentclientmandate.metrics.{Metrics, MetricsEnum}
import uk.gov.hmrc.agentclientmandate.models._
import uk.gov.hmrc.agentclientmandate.repositories._
import uk.gov.hmrc.agentclientmandate.services.{MandateFetchService, MandateUpdateService, NotificationEmailService}
import uk.gov.hmrc.agentclientmandate.utils.FeatureSwitch
import uk.gov.hmrc.agentclientmandate.utils.MandateConstants._
import uk.gov.hmrc.agentclientmandate.utils.MandateUtils._
import uk.gov.hmrc.agentclientmandate.{Auditable, models}
import uk.gov.hmrc.tasks._

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}
import uk.gov.hmrc.http.HeaderCarrier

class ActivationTaskExecutor extends TaskExecutor with Auditable {

  val etmpConnector: EtmpConnector = EtmpConnector
  val ggProxyConnector: GovernmentGatewayProxyConnector = GovernmentGatewayProxyConnector
  val updateService: MandateUpdateService = MandateUpdateService
  val fetchService: MandateFetchService = MandateFetchService
  val emailNotificationService: NotificationEmailService = NotificationEmailService
  val mandateRepository: MandateRepository = MandateRepository()
  val taxEnrolmentConnector: TaxEnrolmentConnector = TaxEnrolmentConnector
  val isGGEnabled: Boolean = FeatureSwitch.isEnabled("allocation.usingGG")

  override val metrics: Metrics = Metrics

  implicit val hc = new HeaderCarrier()

  override def execute(signal: Signal): Try[Signal] = {

    signal match {
      case Start(args) => {
        val request = createRelationship(args("clientId"), args("agentPartyId"))
        val result = Await.result(etmpConnector.maintainAtedRelationship(request), 60 seconds)
        result.status match {
          case OK =>
            Success(Next("gg-proxy-activation", args))
          case _ => {
            Logger.warn(s"[ActivationTaskExecutor] - call to ETMP failed with status ${result.status} for mandate reference::${args("mandateId")}")
            Failure(new Exception("ETMP call failed, status: " + result.status))
          }
        }
      }

      case Next("gg-proxy-activation", args) => {
        if (isGGEnabled) {
          val request = GsoAdminAllocateAgentXmlInput(
            List(Identifier(args("serviceIdentifier"), args("clientId"))),
            args("agentCode"), AtedServiceContractName)
          Try(Await.result(ggProxyConnector.allocateAgent(request), 120 seconds)) match {
            case Success(resp) =>
              resp.status match {
                case OK =>
                  Logger.debug("****DB***** " + "Ok is returned")
                  metrics.incrementSuccessCounter(MetricsEnum.GGProxyAllocate)
                  Success(Next("finalize-activation", args))
                case INTERNAL_SERVER_ERROR if parseErrorResp(resp) == "7004" =>
                  Logger.debug("****DB***** " + "Internal server error")
                  // this error means GG already has a relationship for this client
                  metrics.incrementSuccessCounter(MetricsEnum.GGProxyAllocate)
                  Success(Next("finalize-activation", args))
                case _ =>
                  Logger.debug("****DB***** " + "Warn returned")
                  Logger.warn(s"[ActivationTaskExecutor] - call to gg-proxy failed with status ${resp.status} for mandate reference::${args("mandateId")}")
                  metrics.incrementFailedCounter(MetricsEnum.GGProxyAllocate)
                  Failure(new Exception("GG Proxy call failed, status: " + resp.status))
              }
            case Failure(ex) =>
              // $COVERAGE-OFF$
              Logger.debug("****DB***** " + "Failure")
              Logger.warn(s"[ActivationTaskExecutor] exception while calling allocateAgent :: ${ex.getMessage}")
              Failure(new Exception("GG Proxy call failed, status: " + ex.getMessage))
            // $COVERAGE-ON$
          }
        } else {

          Logger.debug("****TX***** " + " client id " + args("clientId").toString)
          Logger.debug("****TX***** " + " group id " + args.getOrElse("groupId","NotFound"))
          Logger.debug("*TX* - HEader Carriers " + hc.toString)

          val request = NewEnrolment(args("clientId"))

          Try(Await.result(taxEnrolmentConnector.allocateAgent(request,args("groupId"),args("clientId")), 120 seconds)) match {
            case Success(resp) =>
              resp.status match {
                case CREATED =>
                  Logger.debug("****DB***** " + " tax created returned")
                  metrics.incrementSuccessCounter(MetricsEnum.TaxEnrolmentAllocate)
                  Success(Next("finalize-activation", args))
                case _ =>
                  Logger.debug("****DB***** " + " tax Warn returned")
                  Logger.warn(s"[ActivationTaskExecutor] - call to gg-proxy failed with status ${resp.status} for mandate reference::${args("mandateId")}")
                  metrics.incrementFailedCounter(MetricsEnum.TaxEnrolmentAllocate)
                  Failure(new Exception("GG Proxy call failed, status: " + resp.status))
              }
            case Failure(ex) =>
              // $COVERAGE-OFF$
              Logger.debug("****TX***** " + " tax failure returned " + ex.getMessage)
              Logger.warn(s"[ActivationTaskExecutor] execption while calling allocateAgent :: ${ex.getMessage}")
              Failure(new Exception("GG Proxy call failed, status: " + ex.getMessage))
            // $COVERAGE-ON$
          }
        }
      }
      case Next("finalize-activation", args) => {
        val fetchResult = Await.result(fetchService.fetchClientMandate(args("mandateId")), 5 seconds)
        fetchResult match {
          case MandateFetched(mandate) => {
            val updatedMandate = mandate.updateStatus(MandateStatus(Status.Active, DateTime.now, args("credId")))
            val updateResult = Await.result(mandateRepository.updateMandate(updatedMandate), 5 seconds)
            updateResult match {
              case MandateUpdated(m) => {
                val receiverParty = if(whetherSelfAuthorised(m)) (m.agentParty.contactDetails.email, Some("agent"))
                else (m.clientParty.map(_.contactDetails.email).getOrElse(""), None)
                val service = m.subscription.service.id
                Try(emailNotificationService.sendMail(emailString = receiverParty._1, models.Status.Active, userType = receiverParty._2, service = service)) match {
                  case Success(v) =>
                    doAudit("emailSent", args("agentCode"), m)
                  case Failure(reason) =>
                    // $COVERAGE-OFF$
                    doFailedAudit("emailSentFailed", s"receiver email::${receiverParty._1} status:: ${models.Status.Active} service::$service", reason.getMessage)
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

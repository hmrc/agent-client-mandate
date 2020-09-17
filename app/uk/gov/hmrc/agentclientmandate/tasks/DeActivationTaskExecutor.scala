/*
 * Copyright 2020 HM Revenue & Customs
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

import javax.inject.Inject
import org.joda.time.DateTime
import play.api.Logging
import play.api.http.Status._
import uk.gov.hmrc.agentclientmandate.connectors.{EtmpConnector, TaxEnrolmentConnector}
import uk.gov.hmrc.agentclientmandate.metrics.{MetricsEnum, ServiceMetrics}
import uk.gov.hmrc.agentclientmandate.models._
import uk.gov.hmrc.agentclientmandate.repositories._
import uk.gov.hmrc.agentclientmandate.services.{MandateFetchService, MandateUpdateService, NotificationEmailService}
import uk.gov.hmrc.agentclientmandate.utils.MandateUtils._
import uk.gov.hmrc.agentclientmandate.{Auditable, models}
import uk.gov.hmrc.http.logging.Authorization
import uk.gov.hmrc.http.{HeaderCarrier, UserId}
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.tasks._
import utils.ScheduledService

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}

class DeactivationTaskExecutor extends TaskExecutor
class DeActivationTaskService @Inject()(val etmpConnector: EtmpConnector,
                                         val mandateUpdateService: MandateUpdateService,
                                         val taxEnrolmentConnector: TaxEnrolmentConnector,
                                         val metrics: ServiceMetrics,
                                         val emailNotificationService: NotificationEmailService,
                                         val auditConnector: AuditConnector,
                                         val fetchService: MandateFetchService,
                                         val mandateRepo: MandateRepo) extends ScheduledService with Auditable with Logging {

  val mandateRepository: MandateRepository = mandateRepo.repository

  override def execute(signal: Signal): Try[Signal] = {
    val auth: String = signal.args.getOrElse("authorization", "dummy auth")
    val credId = signal.args.getOrElse("credId", "your-dummy-id")

    implicit val hc: HeaderCarrier = HeaderCarrier(authorization = Some(Authorization(auth)), userId = Some(UserId(credId)))

    signal match {
      case Start(args)                          => start(args)
      case Next("gg-proxy-deactivation", args)  => unenrolTaxEnrolments(args)
      case Next("finalize-deactivation", args)  => finalize(args)
      case _                                    => throw new Exception("Unknown signal type")
    }
  }

  private def start(args: Map[String, String]): Try[Signal] = {
    val request = breakRelationship(args("clientId"), args("agentPartyId"))
    val result = Await.result(etmpConnector.maintainAtedRelationship(request), 60 seconds)
    result.status match {
      case OK => Success(Next("gg-proxy-deactivation", args))
      case _ =>
    logger.warn(s"[DeActivationTaskExecutor] - call to ETMP failed with status ${result.status} for mandate reference::${args("mandateId")}")
    Failure(new Exception("ETMP call failed, status: " + result.status))
    }
  }

  private def unenrolTaxEnrolments(args: Map[String, String])(implicit hc: HeaderCarrier): Try[Signal] = {
    Try(Await.result(taxEnrolmentConnector.deAllocateAgent(args("agentPartyId"), args("clientId"), args("agentCode"), args("userType")), 120 seconds)) match {
      case Success(resp) =>
        resp.status match {
          case NO_CONTENT =>
            metrics.incrementSuccessCounter(MetricsEnum.TaxEnrolmentDeallocate)
            Success(Next("finalize-deactivation", args))
          case NOT_FOUND =>
            metrics.incrementSuccessCounter(MetricsEnum.TaxEnrolmentDeallocate)
            Success(Next("finalize-deactivation", args))
          case _ =>
            logger.warn(s"[DeActivationTaskExecutor] - call to tax-enrolments failed with status ${resp.status} for mandate reference::${args("mandateId")}")
            metrics.incrementFailedCounter(MetricsEnum.TaxEnrolmentDeallocate)
            Failure(new Exception("Tax Enrolment call failed, status: " + resp.status))
        }
      case Failure(ex) =>
        logger.warn(s"[DeActivationTaskExecutor] execption while calling deAllocateAgent :: ${ex.getMessage}")
        Failure(new Exception("Tax Enrolment call failed, status: " + ex.getMessage))

    }
  }

  private def finalize(args: Map[String, String])(implicit hc: HeaderCarrier): Try[Signal] = {
    val fetchResult = Await.result(fetchService.fetchClientMandate(args("mandateId")), 5 seconds)
    fetchResult match {
      case MandateFetched(mandate) =>
        val updatedMandate = mandate.updateStatus(MandateStatus(Status.Cancelled, DateTime.now, args("credId")))
        val updateResult = Await.result(mandateRepository.updateMandate(updatedMandate), 5 seconds)
        updateResult match {
          case MandateUpdated(m) =>
            val service = m.subscription.service.id
            args("userType") match {
              case "agent" =>
                val receiverParty = if(whetherSelfAuthorised(m)) (m.agentParty.contactDetails.email, Some("agent"))
                else (m.clientParty.map(_.contactDetails.email).getOrElse(""), Some("client"))
                Try(emailNotificationService.sendMail(emailString = receiverParty._1, models.Status.Cancelled, userType = Some("agent"), recipient = receiverParty._2,service)) match {
                  case Success(v) =>
                    doAudit("emailSent", args("agentCode"), m)
                  case Failure(reason) =>
                    doFailedAudit("emailSentFailed", s"receiver email::${receiverParty._1} status:: ${models.Status.Cancelled} service::$service", reason.getMessage)
                }
              case _ =>
                val agentEmail = m.agentParty.contactDetails.email
                Try(emailNotificationService.sendMail(agentEmail, models.Status.Cancelled, Some(args("userType")), Some("agent"), service, Some(mandate.currentStatus.status))) match {
                  case Success(v) => doAudit("emailSent", args("agentCode"), m)
                  case Failure(reason) =>
                    doFailedAudit("emailSentFailed", s"agent email::$agentEmail status:: ${models.Status.Cancelled} service::$service", reason.getMessage)
                }
            }
            doAudit("removed", args("agentCode"), m)
            Success(Finish)
          case MandateUpdateError =>
            logger.warn(s"[DeActivationTaskExecutor] - could not update mandate with id ${args("mandateId")}")
            Failure(new Exception("Could not update mandate to activate"))
          case _ =>
            throw new Exception("Unknown update result")
        }
      case MandateNotFound =>
        logger.warn(s"[DeActivationTaskExecutor] - could not find mandate with id ${args("mandateId")}")
        Failure(new Exception("Could not find mandate to activate"))
    }
  }

  override def rollback(signal: Signal): Try[Signal] = {

    logger.warn(s"[DeActivationTaskExecutor] Performing rollback")

    signal match {
      case Start(args) =>
        logger.warn("[DeActivationTaskExecutor] start failed. Rolling back.")
        // setting back to Active from PendingCancellation status so agent can try again
        val fetchResult = Await.result(fetchService.fetchClientMandate(args("mandateId")), 3 seconds)
        fetchResult match {
          case MandateFetched(mandate) =>
            val updatedMandate = mandate.updateStatus(MandateStatus(Status.Active, DateTime.now, args("credId")))
            Await.result(mandateRepository.updateMandate(updatedMandate), 1 second)
            Success(Finish)
          case _ => throw new Exception("Unknown fetch result")
        }
      //failed doing allocate agent in GG
      case Next("gg-proxy-deactivation", args) =>
        logger.warn("[DeActivationTaskExecutor] gg-proxy de-allocate agent failed. Rolling back.")
        // rolling back ETMP as we have failed GG proxy call
        val request = createRelationship(args("clientId"), args("agentPartyId"))
        Await.result(etmpConnector.maintainAtedRelationship(request), 5 seconds)
        Success(Start(args))
      //failed to update the status in Mongo from PendingCancellation to Cancelled
      case Next("finalize-deactivation", args) =>
        logger.error("[DeActivationTaskExecutor] Mongo update failed. Leaving for manual intervention.")
        // leaving for manual intervention as etmp and gg proxy were successful
        Success(Next("gg-proxy-deactivation", args))
      case _ => throw new Exception("Unknown signal")
    }
  }

  override def onRollbackFailure(lastSignal: Signal): Unit = {
    logger.error("[DeActivationTaskExecutor] Rollback action failed")
  }

}

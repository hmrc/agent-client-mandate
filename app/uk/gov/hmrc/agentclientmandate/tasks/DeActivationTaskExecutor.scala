/*
 * Copyright 2021 HM Revenue & Customs
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
import play.api.http.Status._
import uk.gov.hmrc.agentclientmandate.connectors.{EtmpConnector, TaxEnrolmentConnector}
import uk.gov.hmrc.agentclientmandate.metrics.{MetricsEnum, ServiceMetrics}
import uk.gov.hmrc.agentclientmandate.models.Status.Status
import uk.gov.hmrc.agentclientmandate.models._
import uk.gov.hmrc.agentclientmandate.repositories._
import uk.gov.hmrc.agentclientmandate.services.{MandateFetchService, MandateUpdateService, NotificationEmailService}
import uk.gov.hmrc.agentclientmandate.utils.LoggerUtil.{logError, logWarn}
import uk.gov.hmrc.agentclientmandate.utils.MandateUtils._
import uk.gov.hmrc.agentclientmandate.{Auditable, models}
import uk.gov.hmrc.http.logging.Authorization
import uk.gov.hmrc.http.HeaderCarrier
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
                                         val mandateRepo: MandateRepo) extends ScheduledService with Auditable {

  val mandateRepository: MandateRepository = mandateRepo.repository

  override def execute(signal: Signal): Try[Signal] = {
    val auth: String = signal.args.getOrElse("authorization", "dummy auth")

    implicit val hc: HeaderCarrier = HeaderCarrier(authorization = Some(Authorization(auth)))

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
    logWarn(s"[DeActivationTaskExecutor] - call to ETMP failed with status ${result.status} for mandate reference::${args("mandateId")}")
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
            logWarn(s"[DeActivationTaskExecutor] - call to tax-enrolments failed with status ${resp.status} for mandate reference::${args("mandateId")}")
            metrics.incrementFailedCounter(MetricsEnum.TaxEnrolmentDeallocate)
            Failure(new Exception("Tax Enrolment call failed, status: " + resp.status))
        }
      case Failure(ex) =>
        logWarn(s"[DeActivationTaskExecutor] exception while calling deAllocateAgent :: ${ex.getMessage}")
        Failure(new Exception("Tax Enrolment call failed, status: " + ex.getMessage))

    }
  }

  private def finalize(args: Map[String, String])(implicit hc: HeaderCarrier): Try[Signal] = {
    val fetchResult = Await.result(fetchService.fetchClientMandate(args("mandateId")), 5 seconds)
    fetchResult match {
      case MandateFetched(mandate) =>
        val previousStatus: Option[Status] = mandate.statusHistory.lastOption.fold[Option[Status]](None)(mandateStatus => Some(mandateStatus.status))
        val updatedMandate = mandate.updateStatus(MandateStatus(Status.Cancelled, DateTime.now, args("credId")))
        val updateResult = Await.result(mandateRepository.updateMandate(updatedMandate), 5 seconds)
        updateResult match {
          case MandateUpdated(m) =>
            args("userType") match {
              case "agent" =>
                handleRemoveMandateEmailRequest(m.agentParty.contactDetails.email, Some("agent"), mandate.agentParty.name, args, mandate, Some("agent"), previousStatus)
                m.clientParty.foreach( client =>
                  if(client.contactDetails.email != ""){
                    handleRemoveMandateEmailRequest(client.contactDetails.email, Some("client"),
                      mandate.clientParty.fold("")(_.name), args, mandate, Some("agent"), previousStatus)
                  }
                )
              case _ =>
                handleRemoveMandateEmailRequest(m.agentParty.contactDetails.email, Some("agent"), mandate.agentParty.name, args, mandate, Some("client"), previousStatus)
            }
            doAudit("removed", args("agentCode"), m)
            Success(Finish)
          case MandateUpdateError =>
            logWarn(s"[DeActivationTaskExecutor] - could not update mandate with id ${args("mandateId")}")
            Failure(new Exception("Could not update mandate to activate"))
          case _ =>
            throw new Exception("Unknown update result")
        }
      case MandateNotFound =>
        logWarn(s"[DeActivationTaskExecutor] - could not find mandate with id ${args("mandateId")}")
        Failure(new Exception("Could not find mandate to activate"))
    }
  }

  private def handleRemoveMandateEmailRequest(email: String, recipient: Option[String], recipientName: String, args: Map[String, String],
                                              mandate: Mandate, userType: Option[String], prevStatus: Option[Status])(implicit hc: HeaderCarrier): Unit = {

    val service = mandate.subscription.service.id
    val uniqueAuthNo: Option[String] = if(recipient.contains("client")) Some(mandate.id) else None

    Try(emailNotificationService.sendMail(email, models.Status.Cancelled, userType,
      recipient, recipientName = recipientName, service, uniqueAuthNo = uniqueAuthNo, prevStatus = prevStatus)) match {
        case Success(_) =>
          doAudit("emailSent", args("agentCode"), mandate)
        case Failure(reason) =>
          doFailedAudit("emailSentFailed", s"receiver email::$email " +
            s"status:: ${models.Status.Cancelled} service::$service", reason.getMessage)
      }
  }

  override def rollback(signal: Signal): Try[Signal] = {

    logWarn(s"[DeActivationTaskExecutor] Performing rollback")

    signal match {
      case Start(args) =>
        logWarn("[DeActivationTaskExecutor] start failed. Rolling back.")
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
        logWarn("[DeActivationTaskExecutor] gg-proxy de-allocate agent failed. Rolling back.")
        // rolling back ETMP as we have failed GG proxy call
        val request = createRelationship(args("clientId"), args("agentPartyId"))
        Await.result(etmpConnector.maintainAtedRelationship(request), 5 seconds)
        Success(Start(args))
      //failed to update the status in Mongo from PendingCancellation to Cancelled
      case Next("finalize-deactivation", args) =>
        logError("[DeActivationTaskExecutor] Mongo update failed. Leaving for manual intervention.")
        // leaving for manual intervention as etmp and gg proxy were successful
        Success(Next("gg-proxy-deactivation", args))
      case _ => throw new Exception("Unknown signal")
    }
  }

  override def onRollbackFailure(lastSignal: Signal): Unit = {
    logError("[DeActivationTaskExecutor] Rollback action failed")
  }

}

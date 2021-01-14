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

class ActivationTaskExecutor extends TaskExecutor

class ActivationTaskService @Inject()(val etmpConnector: EtmpConnector,
                                      val mandateUpdateService: MandateUpdateService,
                                      val taxEnrolmentConnector: TaxEnrolmentConnector,
                                      val metrics: ServiceMetrics,
                                      val emailNotificationService: NotificationEmailService,
                                      val auditConnector: AuditConnector,
                                      val fetchService: MandateFetchService,
                                      val mandateRepo: MandateRepo) extends Auditable with ScheduledService {

  val mandateRepository: MandateRepository = mandateRepo.repository

  def execute(signal: Signal): Try[Signal] = {
    implicit val hc: HeaderCarrier = createHeaderCarrier(signal)

    signal match {
      case Start(args) => start(args)
      case Next("gg-proxy-activation", args) => enrolTaxEnrolments(args)
      case Next("finalize-activation", args) => finalize(args)
      case _ => throw new Exception("Unknown signal type")
    }
  }

  private def createHeaderCarrier(signal: Signal): HeaderCarrier = {
    HeaderCarrier(authorization = Some(Authorization(signal.args.getOrElse("authorization", "dummy auth"))))
  }

  private def start(args: Map[String, String]): Try[Signal] = {
    val request = createRelationship(args("clientId"), args("agentPartyId"))
    val result = Await.result(etmpConnector.maintainAtedRelationship(request), 60 seconds)
    result.status match {
      case OK =>
        Success(Next("gg-proxy-activation", args))
      case _ =>
        logWarn(s"[ActivationTaskExecutor] - call to ETMP failed with status ${result.status} for mandate reference::${args("mandateId")}")
        Failure(new Exception("ETMP call failed, status: " + result.status))
    }
  }

  private def enrolTaxEnrolments(args: Map[String, String])(implicit hc: HeaderCarrier): Try[Signal] = {
    Try(Await.result(taxEnrolmentConnector.allocateAgent(
      NewEnrolment(args("credId")), args("groupId"), args("clientId"), args("agentCode")), 120 seconds)
    ) match {
      case Success(resp) =>
        resp.status match {
          case CREATED =>
            metrics.incrementSuccessCounter(MetricsEnum.TaxEnrolmentAllocate)
            Success(Next("finalize-activation", args))
          case _ =>
            logWarn(s"[ActivationTaskExecutor] - call to tax-enrolments failed with status ${resp.status} for mandate reference::${args("mandateId")}")
            metrics.incrementFailedCounter(MetricsEnum.TaxEnrolmentAllocate)
            Failure(new Exception("GG Proxy call failed, status: " + resp.status))
        }
      case Failure(ex) =>
        logWarn(s"[ActivationTaskExecutor] execption while calling allocateAgent :: ${ex.getMessage}")
        Failure(new Exception("GG Proxy call failed, status: " + ex.getMessage))
    }
  }

  private def finalize(args: Map[String, String])(implicit hc: HeaderCarrier): Try[Signal] = {
    val fetchResult = Await.result(fetchService.fetchClientMandate(args("mandateId")), 5 seconds)
    fetchResult match {
      case MandateFetched(mandate) =>
        val updatedMandate = mandate.updateStatus(MandateStatus(Status.Active, DateTime.now, args("credId")))
        val updateResult = Await.result(mandateRepository.updateMandate(updatedMandate), 5 seconds)
        updateResult match {
          case MandateUpdated(m) =>
            val receiverParty = if (whetherSelfAuthorised(m)) (m.agentParty.contactDetails.email, Some("agent"), m.agentParty.name)
            else (m.clientParty.map(_.contactDetails.email).getOrElse(""), Some("client"), mandate.clientParty.fold("")(_.name))
            val service = m.subscription.service.id
            Try(emailNotificationService.sendMail(emailString = receiverParty._1, models.Status.Active,
              userType = Some("agent"), recipient = receiverParty._2,service = service, recipientName = receiverParty._3)) match {
              case Success(v) =>
                doAudit("emailSent", args("agentCode"), m)
              case Failure(reason) =>
                doFailedAudit("emailSentFailed", s"receiver email::${receiverParty._1} status:: ${models.Status.Active} service::$service", reason.getMessage)
            }
            doAudit("activated", args("agentCode"), m)
            Success(Finish)
          case MandateUpdateError =>
            logWarn(s"[ActivationTaskExecutor] - could not update mandate with id ${args("mandateId")}")
            Failure(new Exception("Could not update mandate to activate"))
          case _ => throw new Exception("Unknown update result type")
        }
      case MandateNotFound =>
        logWarn(s"[ActivationTaskExecutor] - could not find mandate with id ${args("mandateId")}")
        Failure(new Exception("Could not find mandate to activate"))
      case _ => throw new Exception("Unknown fetch result")
    }
  }

  def rollback(signal: Signal): Try[Signal] = {
    signal match {
      case Start(args) =>
        logWarn("[ActivationTaskExecutor] start failed. Rolling back")
        val fetchResult = Await.result(fetchService.fetchClientMandate(args("mandateId")), 3 seconds)
        fetchResult match {
          case MandateFetched(mandate) =>
            val updatedMandate = mandate.updateStatus(MandateStatus(Status.Approved, DateTime.now, args("credId")))
            Await.result(mandateRepository.updateMandate(updatedMandate), 1 second)
            Success(Finish)
          case _ => throw new Exception("Unknown fetch result")
        }
      case Next("gg-proxy-activation", args) =>
        logWarn("[ActivationTaskExecutor] gg-proxy allocate failed. Rolling back")
        // rolling back ETMP as we have failed GG proxy call
        val request = breakRelationship(args("clientId"), args("agentPartyId"))
        Await.result(etmpConnector.maintainAtedRelationship(request), 5 seconds)
        Success(Start(args))
      case Next("finalize-activation", args) =>
        logError("[ActivationTaskExecutor] Mongo update failed. Rolling back")
        Success(Next("gg-proxy-activation", args))
      case _ => throw new Exception("Unknown signal type")
    }
  }

  def onRollbackFailure(lastSignal: Signal): Unit = {
    logError("[ActivationTaskExecutor] Rollback action failed")
  }
}

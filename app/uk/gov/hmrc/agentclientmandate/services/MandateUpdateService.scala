/*
 * Copyright 2023 HM Revenue & Customs
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

import javax.inject.Inject
import org.joda.time.DateTime
import uk.gov.hmrc.agentclientmandate.Auditable
import uk.gov.hmrc.agentclientmandate.auth.AuthRetrieval
import uk.gov.hmrc.agentclientmandate.connectors.EtmpConnector
import uk.gov.hmrc.agentclientmandate.models.Status.Status
import uk.gov.hmrc.agentclientmandate.models._
import uk.gov.hmrc.agentclientmandate.repositories._
import uk.gov.hmrc.agentclientmandate.utils.LoggerUtil.logWarn
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import scala.concurrent.{ExecutionContext, Future}

class DefaultMandateUpdateService @Inject()(val etmpConnector: EtmpConnector,
                                            val auditConnector: AuditConnector,
                                            val mandateRepo: MandateRepo,
                                            val ec: ExecutionContext,
                                            val servicesConfig: ServicesConfig) extends MandateUpdateService {
  val mandateRepository: MandateRepository = mandateRepo.repository
  lazy val expiryAfterDays: Int = servicesConfig.getInt("expiry-after-days")
}

trait MandateUpdateService extends Auditable {
  implicit val ec: ExecutionContext

  val expiryAfterDays: Int

  def mandateRepository: MandateRepository
  def etmpConnector: EtmpConnector

  def approveMandate(approvedMandate: Mandate)(implicit ar: AuthRetrieval): Future[MandateUpdate] = {
    val service = approvedMandate.subscription.service.id.toLowerCase
    service match {
      case "ated" =>
        mandateRepository.fetchMandate(approvedMandate.id) flatMap {
          case MandateFetched(m) if m.currentStatus.status == Status.New =>
              etmpConnector.getAtedSubscriptionDetails(ar.atedUtr.value) flatMap { subscriptionJson =>
                val clientPartyId = (subscriptionJson \ "safeId").as[String]
                val clientPartyName = (subscriptionJson \ "organisationName").as[String]
                val approvedBy = User(ar.govGatewayId, clientPartyName)
                val clientParty = approvedMandate.clientParty.getOrElse(throw new RuntimeException("Client party not found"))
                val clientPartyUpdated = clientParty.copy(id = clientPartyId, name = clientPartyName)
                val subscription = approvedMandate.subscription.copy(referenceNumber = Some(ar.atedUtr.value))
                val updatedMandate = approvedMandate.copy(
                  approvedBy = Some(approvedBy),
                  clientParty = Some(clientPartyUpdated),
                  subscription = subscription
                )
                updateMandate(updatedMandate, Some(Status.Approved))
              }
          case MandateNotFound =>
            logWarn(s"[MandateUpdateService][approveMandate] - mandate not found")
            throw new RuntimeException(s"mandate not found for mandate id::${approvedMandate.id}")
        }
      case any =>
        logWarn(s"[MandateUpdateService][approveMandate] - $any service not supported yet")
        throw new RuntimeException("currently supported only for ATED")
    }
  }

  def updateMandate(mandate: Mandate, setStatus: Option[Status] = None)(implicit ar: AuthRetrieval): Future[MandateUpdate] = {
      val updatedMandate = setStatus match {
        case Some(x) => mandate.updateStatus(MandateStatus(x, DateTime.now, ar.govGatewayId))
        case None => mandate
      }
      mandateRepository.updateMandate(updatedMandate)
  }

  def updateAgentEmail(arn: String, email: String, service: String): Future[MandateUpdate] = {
    mandateRepository.findMandatesMissingAgentEmail(arn, service).flatMap { x =>
      mandateRepository.updateAgentEmail(x, email)
    }
  }

  def updateClientEmail(mandateId: String, email: String): Future[MandateUpdate] = {
    mandateRepository.updateClientEmail(mandateId, email)
  }

  def updateAgentCredId(oldCredId: String)(implicit ar: AuthRetrieval): Future[MandateUpdate] = {
      mandateRepository.updateAgentCredId(oldCredId, ar.govGatewayId)
  }

  def checkStaleDocuments(): Future[_] = {
    val dateFrom = DateTime.now().minusDays(expiryAfterDays)
    for {
      mandates <- mandateRepository.findOldMandates(dateFrom)
    } yield {
      mandates.map { mandate =>
        val updatedMandate = mandate.updateStatus(MandateStatus(Status.Expired, DateTime.now, "SYSTEM"))
        mandateRepository.updateMandate(updatedMandate).map {
          case MandateUpdated(m) =>
            implicit val hc: HeaderCarrier = HeaderCarrier()
            doAudit("expire", "", m)
          case MandateUpdateError => logWarn("Could not expire mandate")
          case _ => throw new Exception("Unknown update status")
        }
      }
    }
  }
}
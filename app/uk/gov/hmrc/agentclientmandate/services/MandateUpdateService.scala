/*
 * Copyright 2016 HM Revenue & Customs
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

import org.joda.time.DateTime
import play.api.Logger
import uk.gov.hmrc.agentclientmandate.connectors.{AuthConnector, EmailStatus, EtmpConnector}
import uk.gov.hmrc.agentclientmandate.models.Status.Status
import uk.gov.hmrc.agentclientmandate.models._
import uk.gov.hmrc.agentclientmandate.repositories._
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait MandateUpdateService {

  def mandateRepository: MandateRepository

  def emailNotificationService: NotificationEmailService

  def etmpConnector: EtmpConnector

  def authConnector: AuthConnector

  def approveMandate(approvedMandate: Mandate)(implicit hc: HeaderCarrier): Future[MandateUpdate] = {
    val service = approvedMandate.subscription.service.id.toLowerCase
    service match {
      case "ated" =>
        mandateRepository.fetchMandate(approvedMandate.id) flatMap {
          case MandateFetched(m) if m.currentStatus.status == Status.New =>
            authConnector.getAuthority() flatMap { authority =>
              val subscriptionId = (authority \ "accounts" \ "ated" \ "utr").as[String]
              val credId = (authority \ "credentials" \ "gatewayId").as[String]
              etmpConnector.getAtedSubscriptionDetails(subscriptionId) flatMap { subscriptionJson =>
                val clientPartyId = (subscriptionJson \ "safeId").as[String]
                val clientPartyName = (subscriptionJson \ "organisationName").as[String]
                val approvedBy = User(credId, clientPartyName)
                val clientParty = approvedMandate.clientParty.getOrElse(throw new RuntimeException("Client party not found"))
                val clientPartyUpdated = clientParty.copy(id = clientPartyId, name = clientPartyName)
                val currentStatus = createApprovedStatus(credId)
                val subscription = approvedMandate.subscription.copy(referenceNumber = Some(subscriptionId))
                val updatedMandate = approvedMandate.copy(
                  approvedBy = Some(approvedBy),
                  clientParty = Some(clientPartyUpdated),
                  currentStatus = currentStatus, // TODO :: Fix here to call updateStatus
                  statusHistory = Seq(approvedMandate.currentStatus),
                  subscription = subscription
                )
                updateMandate(updatedMandate, "agent")
              }
            }
          case MandateNotFound =>
            Logger.warn(s"[MandateUpdateService][approveMandate] - mandate not found")
            throw new RuntimeException(s"mandate not found for mandate id::${approvedMandate.id}")
        }
      case any =>
        Logger.warn(s"[MandateUpdateService][approveMandate] - $any service not supported yet")
        throw new RuntimeException("currently supported only for ATED")
    }
  }

  private def createApprovedStatus(credId: String): MandateStatus = MandateStatus(Status.Approved, DateTime.now(), credId)

  def updateMandate(updatedMandate: Mandate, userType: String)(implicit hc: HeaderCarrier): Future[MandateUpdate] = {
    for {
      update <- mandateRepository.updateMandate(updatedMandate)
      _ <- sendNotificationEmail(updatedMandate, userType)
    } yield update
  }

  def sendNotificationEmail(mandate: Mandate, userType: String)(implicit hc: HeaderCarrier): Future[EmailStatus] = {
    userType match {
      case "agent" => emailNotificationService.sendMail(mandate.id, "client")
      case "client" => emailNotificationService.sendMail(mandate.id, "agent")
    }
  }

  def updateStatus(mandate: Mandate, status: Status)(implicit hc: HeaderCarrier): Future[MandateUpdate] = {
    authConnector.getAuthority() flatMap { authority =>
      val credId = (authority \ "credentials" \ "gatewayId").as[String]
      val userType = {
        authority \ "accounts" \ "agent" \ "agentBusinessUtr"
      }.asOpt[String].fold("client")(a => "agent")
      val updatedMandate = mandate.updateStatus(MandateStatus(status, DateTime.now, credId))
      updateMandate(updatedMandate, userType)
    }
  }

}

object MandateUpdateService extends MandateUpdateService {
  // $COVERAGE-OFF$
  val mandateRepository = MandateRepository()
  val mandateFetchService = MandateFetchService
  val emailNotificationService = NotificationEmailService
  val etmpConnector: EtmpConnector = EtmpConnector
  val authConnector: AuthConnector = AuthConnector
  // $COVERAGE-ON$
}

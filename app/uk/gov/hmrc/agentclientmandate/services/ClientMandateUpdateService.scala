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
import uk.gov.hmrc.agentclientmandate.controllers.ClientMandateUpdatedDto
import uk.gov.hmrc.agentclientmandate.models._
import uk.gov.hmrc.agentclientmandate.repositories._
import uk.gov.hmrc.agentclientmandate.utils.DateTimeUtils
import uk.gov.hmrc.play.http.HeaderCarrier
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait ClientMandateUpdateService {

  def clientMandateRepository: ClientMandateRepository

  def clientMandateFetchService: ClientMandateFetchService

  def generateUpdatedMandate(currentMandate: ClientMandate, mandateUpdate: ClientMandateUpdatedDto)(implicit hc: HeaderCarrier): ClientMandate = {
    val credId = hc.gaUserId.getOrElse("credid")

    val clientParty = mandateUpdate.party map {
      party =>
        Party(party.id, party.name, party.`type`, ContactDetails("", ""))
    }

    val subscription = mandateUpdate.subscription map {
      s =>
        Subscription(Some(s.referenceNumber), currentMandate.subscription.service)
    } getOrElse currentMandate.subscription

    val currentStatus = mandateUpdate.status map {
      st =>
        MandateStatus(st, DateTime.now, credId)
    } getOrElse MandateStatus(currentMandate.currentStatus.status, DateTimeUtils.currentDateTime, credId)

    val statusHistory = currentMandate.statusHistory match {
      case Some(x) => currentMandate.currentStatus +: x
      case None => Seq(currentMandate.currentStatus)
    }

    currentMandate.copy(
      clientParty = clientParty,
      subscription = subscription,
      currentStatus = currentStatus,
      statusHistory = Some(statusHistory)
    )
  }

  def updateMandate(mandateId: String, mandateUpdate: ClientMandateUpdatedDto)(implicit hc: HeaderCarrier): Future[ClientMandateUpdate] = {
    clientMandateFetchService.fetchClientMandate(mandateId) flatMap {
      case ClientMandateFetched(x) =>
        val updatedMandate = generateUpdatedMandate(x, mandateUpdate)
        clientMandateRepository.updateMandate(updatedMandate)
      case _ => Future.successful(ClientMandateUpdateError)
    }
  }

}

object ClientMandateUpdateService extends ClientMandateUpdateService {
  val clientMandateRepository = ClientMandateRepository()
  val clientMandateFetchService = ClientMandateFetchService
}

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

import uk.gov.hmrc.agentclientmandate.connectors.{EmailStatus, EmailNotSent}
import uk.gov.hmrc.agentclientmandate.models._
import uk.gov.hmrc.agentclientmandate.repositories._
import uk.gov.hmrc.play.http.HeaderCarrier
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait MandateUpdateService {

  def mandateRepository: MandateRepository

  def emailNotificationService: NotificationEmailService

  def updateMandate(updatedMandate: Mandate)(implicit hc: HeaderCarrier): Future[MandateUpdate] = {
    for {
      update <- mandateRepository.updateMandate(updatedMandate)
      _ <- sendNotificationEmail(updatedMandate)
    } yield update
  }

  def sendNotificationEmail(mandate: Mandate)(implicit hc: HeaderCarrier): Future[EmailStatus] = {
    import uk.gov.hmrc.agentclientmandate.models.Status._

    val statusesToNotify = Seq(Approved -> "agent", PendingCancellation -> "client")

    statusesToNotify.toStream.find(_._1 == mandate.currentStatus.status).map(a => emailNotificationService.sendMail(mandate.id, a._2))
      .getOrElse(Future.successful(EmailNotSent))
  }

}

object MandateUpdateService extends MandateUpdateService {
  // $COVERAGE-OFF$
  val mandateRepository = MandateRepository()
  val mandateFetchService = MandateFetchService
  val emailNotificationService = NotificationEmailService
  // $COVERAGE-ON$
}

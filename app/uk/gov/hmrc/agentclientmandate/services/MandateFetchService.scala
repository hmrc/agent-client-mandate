/*
 * Copyright 2022 HM Revenue & Customs
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
import uk.gov.hmrc.agentclientmandate.auth.AuthRetrieval
import uk.gov.hmrc.agentclientmandate.models.Mandate
import uk.gov.hmrc.agentclientmandate.repositories.{MandateFetchStatus, MandateRepo, MandateRepository}
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import scala.concurrent.{ExecutionContext, Future}

class DefaultMandateFetchService @Inject()(val mandateRepo: MandateRepo,
                                           val servicesConfig: ServicesConfig) extends MandateFetchService {
  val mandateRepository: MandateRepository = mandateRepo.repository
  lazy val clientCancelledMandateNotification: Int = servicesConfig.getInt("client-cancelled-mandate-notification-days")
}

trait MandateFetchService {
  val clientCancelledMandateNotification: Int

  def mandateRepository: MandateRepository

  def fetchClientMandate(mandateId: String)(implicit ec: ExecutionContext): Future[MandateFetchStatus] = {
    mandateRepository.fetchMandate(mandateId)
  }

  def fetchClientMandate(clientId: String, service: String)(implicit ec: ExecutionContext): Future[MandateFetchStatus] = {
    mandateRepository.fetchMandateByClient(clientId, service)
  }

  def getAllMandates(arn: String, serviceName: String, credId: Option[String], displayName: Option[String])
                    (implicit ar: AuthRetrieval, ec :ExecutionContext): Future[Seq[Mandate]] = {
    if (credId.isDefined) {
        val otherCredId = ar.govGatewayId
        mandateRepository.getAllMandatesByServiceName(arn, serviceName, credId, Some(otherCredId), displayName)
    }
    else {
      mandateRepository.getAllMandatesByServiceName(arn, serviceName, credId, None, displayName)
    }
  }

  def getMandatesMissingAgentsEmails(arn: String, service: String)(implicit ec: ExecutionContext): Future[Seq[String]] = {
    mandateRepository.findMandatesMissingAgentEmail(arn, service)
  }

  def fetchClientCancelledMandates(arn: String, serviceName: String)(implicit ec: ExecutionContext): Future[Seq[String]] = {
    val dateFrom = DateTime.now().minusDays(clientCancelledMandateNotification)
    mandateRepository.getClientCancelledMandates(dateFrom, arn, serviceName)
  }
}

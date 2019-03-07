/*
 * Copyright 2019 HM Revenue & Customs
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
import play.api.libs.json.JsValue
import uk.gov.hmrc.agentclientmandate.config.ApplicationConfig
import uk.gov.hmrc.agentclientmandate.connectors.AuthConnector
import uk.gov.hmrc.agentclientmandate.models.Mandate
import uk.gov.hmrc.agentclientmandate.repositories.{MandateFetchStatus, MandateRepository}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import uk.gov.hmrc.http.HeaderCarrier

trait MandateFetchService {

  def authConnector: AuthConnector
  def mandateRepository: MandateRepository

  def fetchClientMandate(mandateId: String): Future[MandateFetchStatus] = {
    mandateRepository.fetchMandate(mandateId)
  }

  def fetchClientMandate(clientId: String, service: String): Future[MandateFetchStatus] = {
    mandateRepository.fetchMandateByClient(clientId, service)
  }

  def getAllMandates(arn: String, serviceName: String, credId: Option[String], displayName: Option[String])(implicit hc: HeaderCarrier): Future[Seq[Mandate]] = {

    if (credId.isDefined) {
      authConnector.getAuthority().flatMap { authority =>
        val otherCredId = getCredId(authority)
        mandateRepository.getAllMandatesByServiceName(arn, serviceName, credId, Some(otherCredId), displayName)
      }
    }
    else {
      mandateRepository.getAllMandatesByServiceName(arn, serviceName, credId, None, displayName)
    }
  }

  def getMandatesMissingAgentsEmails(arn: String, service: String): Future[Seq[String]] = {
    mandateRepository.findMandatesMissingAgentEmail(arn, service)
  }

  def fetchClientCancelledMandates(arn: String, serviceName: String): Future[Seq[String]] = {
    val dateFrom = DateTime.now().minusDays(ApplicationConfig.clientCancelledMandateNotification)
    mandateRepository.getClientCancelledMandates(dateFrom, arn, serviceName)
  }

  def getCredId(authorityJson: JsValue): String = (authorityJson \ "credentials" \ "gatewayId").as[String]
}

object MandateFetchService extends MandateFetchService {
  // $COVERAGE-OFF$
  val authConnector = AuthConnector
  val mandateRepository: MandateRepository = MandateRepository()
  // $COVERAGE-ON$
}

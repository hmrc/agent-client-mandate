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

import uk.gov.hmrc.agentclientmandate.models.Mandate
import uk.gov.hmrc.agentclientmandate.repositories.{MandateFetchStatus, MandateRepository}

import scala.concurrent.Future

import scala.concurrent.ExecutionContext.Implicits.global

trait MandateFetchService {

  def mandateRepository: MandateRepository

  def fetchClientMandate(mandateId: String): Future[MandateFetchStatus] = {
    mandateRepository.fetchMandate(mandateId)
  }

  def getAllMandates(arn: String, serviceName: String): Future[Seq[Mandate]] = {
    mandateRepository.getAllMandatesByServiceName(arn, serviceName)
  }
}

object MandateFetchService extends MandateFetchService {
  // $COVERAGE-OFF$
  val mandateRepository: MandateRepository = MandateRepository()
  // $COVERAGE-ON$
}

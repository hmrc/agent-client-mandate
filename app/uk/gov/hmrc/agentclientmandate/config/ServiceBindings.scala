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

package uk.gov.hmrc.agentclientmandate.config

import play.api.inject.{Binding, Module, bind => playBind}
import play.api.{Configuration, Environment}
import uk.gov.hmrc.agentclientmandate.connectors._
import uk.gov.hmrc.agentclientmandate.controllers.auth.{AgentDelegationForAtedController, DefaultAgentDelegationForAtedController}
import uk.gov.hmrc.agentclientmandate.controllers.testOnly.{DefaultPerformanceTestSupportController, PerformanceTestSupportController}
import uk.gov.hmrc.agentclientmandate.metrics.{DefaultServiceMetrics, ServiceMetrics}
import uk.gov.hmrc.agentclientmandate.repositories.{MandateRepo, MandateRepositoryImpl}
import uk.gov.hmrc.agentclientmandate.services._
import uk.gov.hmrc.auth.core.PlayAuthConnector
import uk.gov.hmrc.play.bootstrap.auth.DefaultAuthConnector
import uk.gov.hmrc.play.bootstrap.http.DefaultHttpClient
import uk.gov.hmrc.http.HttpClient

class ServiceBindings extends Module {
  override def bindings(environment: Environment, configuration: Configuration): Seq[Binding[_]] =
    Seq(
      playBind(classOf[ScheduledJobStarter]).to(classOf[DefaultScheduledJobStarter]).eagerly(),
      playBind(classOf[MandateUpdateService]).to(classOf[DefaultMandateUpdateService]),
      playBind(classOf[MandateCreateService]).to(classOf[DefaultMandateCreateService]),
      playBind(classOf[MandateFetchService]).to(classOf[DefaultMandateFetchService]),
      playBind(classOf[RelationshipService]).to(classOf[DefaultRelationshipService]).eagerly(),
      playBind(classOf[AgentDetailsService]).to(classOf[DefaultAgentDetailsService]),
      playBind(classOf[NotificationEmailService]).to(classOf[DefaultNotificationEmailService]),
      playBind(classOf[EtmpConnector]).to(classOf[DefaultEtmpConnector]),
      playBind(classOf[EmailConnector]).to(classOf[DefaultEmailConnector]),
      playBind(classOf[TaxEnrolmentConnector]).to(classOf[DefaultTaxEnrolmentConnector]),
      playBind(classOf[ServiceMetrics]).to(classOf[DefaultServiceMetrics]),
      playBind(classOf[AgentDelegationForAtedController]).to(classOf[DefaultAgentDelegationForAtedController]),
      playBind(classOf[PerformanceTestSupportController]).to(classOf[DefaultPerformanceTestSupportController]),
      playBind(classOf[MandateRepo]).to(classOf[MandateRepositoryImpl]),
      playBind(classOf[PlayAuthConnector]).to(classOf[DefaultAuthConnector]),
      playBind(classOf[HttpClient]).to(classOf[DefaultHttpClient])
    )
}

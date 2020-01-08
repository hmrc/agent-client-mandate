/*
 * Copyright 2020 HM Revenue & Customs
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

import play.api.inject.{Binding, Module}
import play.api.{Configuration, Environment}
import uk.gov.hmrc.agentclientmandate.connectors.{DefaultEmailConnector, DefaultEtmpConnector, DefaultTaxEnrolmentConnector, EmailConnector, EtmpConnector, TaxEnrolmentConnector}
import uk.gov.hmrc.agentclientmandate.controllers.auth.{AgentDelegationForAtedController, DefaultAgentDelegationForAtedController}
import uk.gov.hmrc.agentclientmandate.controllers.testOnly.{DefaultPerformanceTestSupportController, PerformanceTestSupportController}
import uk.gov.hmrc.agentclientmandate.metrics.{DefaultServiceMetrics, ServiceMetrics}
import uk.gov.hmrc.agentclientmandate.repositories.{MandateRepo, MandateRepositoryImpl}
import uk.gov.hmrc.agentclientmandate.services._
import uk.gov.hmrc.auth.core.PlayAuthConnector
import uk.gov.hmrc.play.bootstrap.auth.DefaultAuthConnector
import uk.gov.hmrc.play.bootstrap.http.{DefaultHttpClient, HttpClient}

class ServiceBindings extends Module {
  override def bindings(environment: Environment, configuration: Configuration): Seq[Binding[_]] =
    Seq(
      bind(classOf[ScheduledJobStarter]).to(classOf[DefaultScheduledJobStarter]).eagerly(),
      bind(classOf[MandateUpdateService]).to(classOf[DefaultMandateUpdateService]),
      bind(classOf[MandateCreateService]).to(classOf[DefaultMandateCreateService]),
      bind(classOf[MandateFetchService]).to(classOf[DefaultMandateFetchService]),
      bind(classOf[RelationshipService]).to(classOf[DefaultRelationshipService]).eagerly(),
      bind(classOf[AgentDetailsService]).to(classOf[DefaultAgentDetailsService]),
      bind(classOf[NotificationEmailService]).to(classOf[DefaultNotificationEmailService]),
      bind(classOf[EtmpConnector]).to(classOf[DefaultEtmpConnector]),
      bind(classOf[EmailConnector]).to(classOf[DefaultEmailConnector]),
      bind(classOf[TaxEnrolmentConnector]).to(classOf[DefaultTaxEnrolmentConnector]),
      bind(classOf[ServiceMetrics]).to(classOf[DefaultServiceMetrics]),
      bind(classOf[AgentDelegationForAtedController]).to(classOf[DefaultAgentDelegationForAtedController]),
      bind(classOf[PerformanceTestSupportController]).to(classOf[DefaultPerformanceTestSupportController]),
      bind(classOf[MandateRepo]).to(classOf[MandateRepositoryImpl]),
      bind(classOf[PlayAuthConnector]).to(classOf[DefaultAuthConnector]),
      bind(classOf[HttpClient]).to(classOf[DefaultHttpClient])
    )
}

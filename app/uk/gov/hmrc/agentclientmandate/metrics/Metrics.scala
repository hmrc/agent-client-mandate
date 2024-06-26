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

package uk.gov.hmrc.agentclientmandate.metrics

import com.codahale.metrics.MetricRegistry
import com.codahale.metrics.Timer.Context
import javax.inject.Inject
import uk.gov.hmrc.agentclientmandate.metrics.MetricsEnum.MetricsEnum

class DefaultServiceMetrics @Inject()(val registry: MetricRegistry) extends ServiceMetrics
trait ServiceMetrics {

  def startTimer(api: MetricsEnum): Context = timers(api).time()
  def incrementSuccessCounter(api: MetricsEnum): Unit = successCounters(api).inc()
  def incrementFailedCounter(api: MetricsEnum): Unit = failedCounters(api).inc()
  def incrementFailedCounter(stage: String): Unit = registry.counter(s"stage-$stage-signal-failure-retry-counter").inc()

  val registry: MetricRegistry

  val timers = Map(
    MetricsEnum.GGAdminAddKnownFacts -> registry.timer("gga-add-known-facts-agent-response-timer"),
    MetricsEnum.EtmpGetDetails -> registry.timer("etmp-get-details-response-timer"),
    MetricsEnum.MaintainAtedRelationship -> registry.timer("etmp-maintain-ated-relationship-response-timer"),
    MetricsEnum.AtedSubscriptionDetails -> registry.timer("etmp-ated-subscription-details-response-timer"),
    MetricsEnum.RepositoryInsertMandate -> registry.timer("repository-insert-mandate-timer"),
    MetricsEnum.RepositoryUpdateMandate -> registry.timer("repository-update-mandate-timer"),
    MetricsEnum.RepositoryFetchMandate -> registry.timer("repository-fetch-mandate-timer"),
    MetricsEnum.RepositoryFetchMandateByClient -> registry.timer("repository-fetch-mandate-by-client-timer"),
    MetricsEnum.RepositoryFetchMandatesByService -> registry.timer("repository-fetch-mandates-service-timer"),
    MetricsEnum.RepositoryFindAgentEmail -> registry.timer("repository-find-agent-email-timer"),
    MetricsEnum.RepositoryUpdateAgentEmail -> registry.timer("repository-update-agent-email-timer"),
    MetricsEnum.RepositoryUpdateClientEmail -> registry.timer("repository-update-client-email-timer"),
    MetricsEnum.RepositoryUpdateAgentCredId -> registry.timer("repository-update-agent-credId-timer"),
    MetricsEnum.RepositoryFindOldMandates -> registry.timer("repository-find-old-mandates-timer"),
    MetricsEnum.RepositoryClientCancelledMandates -> registry.timer("repository-find-client-cancelled-timer"),
    MetricsEnum.GGProxyAllocate -> registry.timer("gg-proxy-allocate-response-timer"),
    MetricsEnum.GGProxyDeallocate -> registry.timer("gg-proxy-deallocate-response-timer"),
    MetricsEnum.TaxEnrolmentAllocate -> registry.timer("tax-enrolment-allocate-response-timer"),
    MetricsEnum.TaxEnrolmentDeallocate -> registry.timer("tax-enrolment-deallocate-response-timer")
  )

  val successCounters = Map(
    MetricsEnum.GGAdminAddKnownFacts -> registry.counter("gga-add-known-facts-agent-success-counter"),
    MetricsEnum.EtmpGetDetails -> registry.counter("etmp-get-details-success-counter"),
    MetricsEnum.MaintainAtedRelationship -> registry.counter("etmp-maintain-ated-relationship-success-counter"),
    MetricsEnum.AtedSubscriptionDetails -> registry.counter("etmp-ated-subscription-details-success-counter"),
    MetricsEnum.GGProxyAllocate -> registry.counter("gg-proxy-allocate-success-counter"),
    MetricsEnum.GGProxyDeallocate -> registry.counter("gg-proxy-deallocate-success-counter"),
    MetricsEnum.TaxEnrolmentAllocate -> registry.counter("tax-enrolment-allocate-success-counter"),
    MetricsEnum.TaxEnrolmentDeallocate -> registry.counter("tax-enrolment-deallocate-success-counter")
  )

  val failedCounters = Map(
    MetricsEnum.GGAdminAddKnownFacts -> registry.counter("gga-add-known-facts-agent-failed-counter"),
    MetricsEnum.EtmpGetDetails -> registry.counter("etmp-get-details-failed-counter"),
    MetricsEnum.MaintainAtedRelationship -> registry.counter("etmp-maintain-ated-relationship-failed-counter"),
    MetricsEnum.AtedSubscriptionDetails -> registry.counter("etmp-ated-subscription-details-failed-counter"),
    MetricsEnum.GGProxyAllocate -> registry.counter("gg-proxy-allocate-failed-counter"),
    MetricsEnum.GGProxyDeallocate -> registry.counter("gg-proxy-deallocate-failed-counter"),
    MetricsEnum.StageStartSignalFailed -> registry.counter("stage-start-signal-failure-retry-counter"),
    MetricsEnum.TaxEnrolmentAllocate -> registry.counter("tax-enrolment-allocate-failed-counter"),
    MetricsEnum.TaxEnrolmentDeallocate -> registry.counter("tax-enrolment-deallocate-failed-counter")
  )
}

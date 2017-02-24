/*
 * Copyright 2017 HM Revenue & Customs
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

import com.codahale.metrics.Timer
import com.codahale.metrics.Timer.Context
import com.kenshoo.play.metrics.MetricsRegistry
import uk.gov.hmrc.agentclientmandate.metrics.MetricsEnum.MetricsEnum
import uk.gov.hmrc.play.graphite.MicroserviceMetrics

trait Metrics {

  def startTimer(api: MetricsEnum): Timer.Context

  def incrementSuccessCounter(api: MetricsEnum): Unit

  def incrementFailedCounter(api: MetricsEnum): Unit

  def incrementFailedCounter(api: String): Unit

}

object Metrics extends Metrics with MicroserviceMetrics{
  val registry = metrics.defaultRegistry
  val timers = Map(
    MetricsEnum.EtmpGetDetails -> registry.timer("etmp-get-details-response-timer"),
    MetricsEnum.MaintainAtedRelationship -> registry.timer("etmp-maintain-ated-relationship-response-timer"),
    MetricsEnum.AtedSubscriptionDetails -> registry.timer("etmp-ated-subscription-details-response-timer"),
    MetricsEnum.RepositoryInsertMandate -> registry.timer("repository-insert-mandate-timer"),
    MetricsEnum.RepositoryUpdateMandate -> registry.timer("repository-update-mandate-timer"),
    MetricsEnum.RepositoryFetchMandate -> registry.timer("repository-fetch-mandate-timer"),
    MetricsEnum.RepositoryFetchMandateByClient -> registry.timer("repository-fetch-mandate-by-client-timer"),
    MetricsEnum.RepositoryFetchMandatesByService -> registry.timer("repository-fetch-mandates-service-timer"),
    MetricsEnum.RepositoryAgentAlreadyInserted -> registry.timer("repository-agent-already-inserted-timer"),
    MetricsEnum.RepositoryFindGGRelationshipsToProcess -> registry.timer("repository-find-gg-relationships-process-timer"),
    MetricsEnum.RepositoryInsertExistingRelationships -> registry.timer("repository-insert-existing-relationships-timer"),
    MetricsEnum.RepositoryExistingRelationshipProcessed -> registry.timer("repository-existing-relationships-processed-timer"),
    MetricsEnum.GGProxyAllocate -> registry.timer("gg-proxy-allocate-response-timer"),
    MetricsEnum.GGProxyDeallocate -> registry.timer("gg-proxy-deallocate-response-timer")
  )

  val successCounters = Map(
    MetricsEnum.EtmpGetDetails -> registry.counter("etmp-get-details-success-counter"),
    MetricsEnum.MaintainAtedRelationship -> registry.counter("etmp-maintain-ated-relationship-success-counter"),
    MetricsEnum.AtedSubscriptionDetails -> registry.counter("etmp-ated-subscription-details-success-counter"),
    MetricsEnum.GGProxyAllocate -> registry.counter("gg-proxy-allocate-success-counter"),
    MetricsEnum.GGProxyDeallocate -> registry.counter("gg-proxy-deallocate-success-counter")
  )

  val failedCounters = Map(
    MetricsEnum.EtmpGetDetails -> registry.counter("etmp-get-details-failed-counter"),
    MetricsEnum.MaintainAtedRelationship -> registry.counter("etmp-maintain-ated-relationship-failed-counter"),
    MetricsEnum.AtedSubscriptionDetails -> registry.counter("etmp-ated-subscription-details-failed-counter"),
    MetricsEnum.GGProxyAllocate -> registry.counter("gg-proxy-allocate-failed-counter"),
    MetricsEnum.GGProxyDeallocate -> registry.counter("gg-proxy-deallocate-failed-counter"),
    MetricsEnum.StageStartSignalFailed -> registry.counter("stage-start-signal-failure-retry-counter")
  )

  override def startTimer(api: MetricsEnum): Context = timers(api).time()

  override def incrementSuccessCounter(api: MetricsEnum): Unit = successCounters(api).inc()

  override def incrementFailedCounter(api: MetricsEnum): Unit = failedCounters(api).inc()

  override def incrementFailedCounter(stage: String): Unit = registry.counter(s"stage-$stage-signal-failure-retry-counter").inc()


}

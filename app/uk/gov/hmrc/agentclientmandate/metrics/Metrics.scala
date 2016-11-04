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

package uk.gov.hmrc.agentclientmandate.metrics

import com.codahale.metrics.Timer
import com.codahale.metrics.Timer.Context
import com.kenshoo.play.metrics.MetricsRegistry
import uk.gov.hmrc.agentclientmandate.metrics.MetricsEnum.MetricsEnum

trait Metrics {

  def startTimer(api: MetricsEnum): Timer.Context

  def incrementSuccessCounter(api: MetricsEnum): Unit

  def incrementFailedCounter(api: MetricsEnum): Unit

}

object Metrics extends Metrics {

  val timers = Map(
    MetricsEnum.EtmpGetDetails -> MetricsRegistry.defaultRegistry.timer("etmp-get-details-response-timer"),
    MetricsEnum.MaintainAtedRelationship -> MetricsRegistry.defaultRegistry.timer("etmp-maintain-ated-relationship-response-timer"),
    MetricsEnum.AtedSubscriptionDetails -> MetricsRegistry.defaultRegistry.timer("etmp-ated-subscription-details-response-timer"),
    MetricsEnum.RepositoryInsertMandate -> MetricsRegistry.defaultRegistry.timer("repository-insert-mandate-timer"),
    MetricsEnum.RepositoryUpdateMandate -> MetricsRegistry.defaultRegistry.timer("repository-update-mandate-timer"),
    MetricsEnum.RepositoryFetchMandate -> MetricsRegistry.defaultRegistry.timer("repository-fetch-mandate-timer"),
    MetricsEnum.RepositoryFetchMandatesByService -> MetricsRegistry.defaultRegistry.timer("repository-fetch-mandates-service-timer"),
    MetricsEnum.RepositoryAgentAlreadyInserted -> MetricsRegistry.defaultRegistry.timer("repository-agent-already-inserted-timer"),
    MetricsEnum.RepositoryFindGGRelationshipsToProcess -> MetricsRegistry.defaultRegistry.timer("repository-find-gg-relationships-process-timer"),
    MetricsEnum.RepositoryInsertExistingRelationships -> MetricsRegistry.defaultRegistry.timer("repository-insert-existing-relationships-timer"),
    MetricsEnum.RepositoryExistingRelationshipProcessed -> MetricsRegistry.defaultRegistry.timer("repository-existing-relationships-processed-timer"),
    MetricsEnum.GGProxyAllocate -> MetricsRegistry.defaultRegistry.timer("gg-proxy-allocate-timer"),
    MetricsEnum.GGProxyDeallocate -> MetricsRegistry.defaultRegistry.timer("gg-proxy-deallocate-timer")
  )

  val successCounters = Map(
    MetricsEnum.EtmpGetDetails -> MetricsRegistry.defaultRegistry.counter("etmp-get-details-success-counter"),
    MetricsEnum.MaintainAtedRelationship -> MetricsRegistry.defaultRegistry.counter("etmp-maintain-ated-relationship-success-counter"),
    MetricsEnum.AtedSubscriptionDetails -> MetricsRegistry.defaultRegistry.counter("etmp-ated-subscription-details-success-counter"),
    MetricsEnum.GGProxyAllocate -> MetricsRegistry.defaultRegistry.counter("gg-proxy-allocate-success-counter"),
    MetricsEnum.GGProxyDeallocate -> MetricsRegistry.defaultRegistry.counter("gg-proxy-deallocate-success-counter")
  )

  val failedCounters = Map(
    MetricsEnum.EtmpGetDetails -> MetricsRegistry.defaultRegistry.counter("etmp-get-details-failed-counter"),
    MetricsEnum.MaintainAtedRelationship -> MetricsRegistry.defaultRegistry.counter("etmp-maintain-ated-relationship-failed-counter"),
    MetricsEnum.AtedSubscriptionDetails -> MetricsRegistry.defaultRegistry.counter("etmp-ated-subscription-details-failed-counter"),
    MetricsEnum.GGProxyAllocate -> MetricsRegistry.defaultRegistry.counter("gg-proxy-allocate-failed-counter"),
    MetricsEnum.GGProxyDeallocate -> MetricsRegistry.defaultRegistry.counter("gg-proxy-deallocate-failed-counter")
  )

  override def startTimer(api: MetricsEnum): Context = timers(api).time()

  override def incrementSuccessCounter(api: MetricsEnum): Unit = successCounters(api).inc()

  override def incrementFailedCounter(api: MetricsEnum): Unit = failedCounters(api).inc()

}

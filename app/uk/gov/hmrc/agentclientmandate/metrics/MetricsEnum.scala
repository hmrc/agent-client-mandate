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

object MetricsEnum extends Enumeration {

  type MetricsEnum = Value
  val EtmpGetDetails = Value
  val MaintainAtedRelationship = Value
  val AtedSubscriptionDetails = Value
  val RepositoryInsertMandate = Value
  val RepositoryUpdateMandate = Value
  val RepositoryFetchMandate = Value
  val RepositoryFetchMandateByClient = Value
  val RepositoryFetchMandatesByService = Value
  val RepositoryInsertExistingRelationships = Value
  val RepositoryAgentAlreadyInserted = Value
  val RepositoryExistingRelationshipProcessed = Value
  val RepositoryFindGGRelationshipsToProcess = Value
  val GGProxyAllocate = Value
  val GGProxyDeallocate = Value
  val StageStartSignalFailed = Value
  val StageGGProxyActivationSignalFailed = Value
  val StageFinaliseActivationSignalFailed = Value
  val StageGGProxyDeActivationSignalFailed = Value
  val StageFinaliseDeActivationSignalFailed = Value
}

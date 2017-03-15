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

package uk.gov.hmrc.agentclientmandate.utils

import uk.gov.hmrc.agentclientmandate.models.{EtmpAtedAgentClientRelationship, EtmpRelationship, Mandate, Status}

object MandateUtils {

  def createRelationship(clientId: String, agentId: String) = {
    EtmpAtedAgentClientRelationship(SessionUtils.getUniqueAckNo, clientId, agentId, EtmpRelationship(action = "Authorise", isExclusiveAgent = Some(true)))
  }

  def breakRelationship(clientId: String, agentId: String) = {
    EtmpAtedAgentClientRelationship(SessionUtils.getUniqueAckNo, clientId, agentId, EtmpRelationship(action = "De-Authorise", isExclusiveAgent = None))
  }

  def whetherSelfAuthorised(m: Mandate): Boolean = !m.statusHistory.exists(_.status == Status.Approved) //does not have a status approved
}

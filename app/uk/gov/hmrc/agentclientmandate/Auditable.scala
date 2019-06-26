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

package uk.gov.hmrc.agentclientmandate

import uk.gov.hmrc.agentclientmandate.models.Mandate
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}
import uk.gov.hmrc.play.audit.AuditExtensions._
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.audit.model.{Audit, DataEvent}

trait Auditable {

  val auditConnector: AuditConnector

  private def audit: Audit = new Audit("agent-client-mandate", auditConnector)

  def doAudit(auditType: String, ac: String, m: Mandate)(implicit hc:HeaderCarrier): Unit = {

    val auditDetails = Map("serviceName" -> m.subscription.service.name,
      "mandateId" -> m.id,
      "agentPartyId" -> m.agentParty.id,
      "agentPartyName" -> m.agentParty.name,
      "agentCode" -> ac)

    val clientAuditDetails = {
      m.clientParty match {
        case Some(x) => Map("clientPartyId" -> x.id, "clientPartyName" -> x.name)
        case _ => Map.empty
      }
    }

    sendDataEvent(auditType, auditDetails ++ clientAuditDetails)
  }

  def doResponseAudit(auditType: String, resp: HttpResponse)(implicit hc:HeaderCarrier): Unit = {
    val auditDetails = Map("serviceName" -> "ated",
      "response.status" -> s"${resp.status}",
      "response.body" -> s"${resp.body}")

    sendDataEvent(auditType, auditDetails)
  }

  def doFailedAudit(auditType: String, request: String, response: String)(implicit hc:HeaderCarrier): Unit = {
    val auditDetails = Map("request" -> request,
                           "response" -> response)

    sendDataEvent(auditType, auditDetails)
  }

  private def sendDataEvent(auditType: String, detail: Map[String, String])
                   (implicit hc: HeaderCarrier): Unit =
    audit.sendDataEvent(DataEvent("agent-client-mandate", auditType,
      tags = hc.toAuditTags("", "N/A"),
      detail = hc.toAuditDetails(detail.toSeq: _*)))
}
